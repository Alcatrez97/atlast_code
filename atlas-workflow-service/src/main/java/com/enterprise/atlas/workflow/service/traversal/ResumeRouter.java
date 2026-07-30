package com.enterprise.atlas.workflow.service.traversal;

import com.enterprise.atlas.common.dto.StepRecordDto;
import com.enterprise.atlas.common.dto.WorkflowEdgeDto;
import com.enterprise.atlas.common.dto.WorkflowNodeDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates every "resume from a suspended node" code path that was previously
 * scattered across the top of {@code GraphTraversalEngine#traverse()}.
 *
 * <p>When a traversal is re-entered with a non-null {@code startNodeId}, the engine
 * must first work out where to pick up execution. The logic differs by node type:
 * <ul>
 *   <li>{@code SUB_WORKFLOW} — apply the child's output mapping back to parent context</li>
 *   <li>{@code WAIT_EVENT}   — resolve which node to jump to based on routed value/routes</li>
 *   <li>{@code COMMAND} (ASYNC mode) — mark node active, record RESUMED step, continue</li>
 *   <li>Generic / BUCKET     — choose edge by outcome and advance the frontier</li>
 * </ul>
 *
 * <p>All logic is copied verbatim from the corresponding blocks inside
 * {@code GraphTraversalEngine#traverse()} — zero algorithmic changes.
 */
@Component
public class ResumeRouter {

    private static final Logger log = LoggerFactory.getLogger(ResumeRouter.class);

    @Autowired private RuntimeGraphManager    runtimeGraphManager;
    @Autowired private EdgeSelector           edgeSelector;

    // -----------------------------------------------------------------------
    // Sequential traversal resume
    // -----------------------------------------------------------------------

    /**
     * Handles resume logic for the <em>sequential pointer-based</em> traversal.
     *
     * <p>Reads the type of the suspended node and sets up {@code state} so that
     * the main loop can proceed from the correct next node.  Returns the first
     * node the loop should process, or {@code null} if it could not be resolved
     * (which causes the loop to start from the START node instead — should not
     * happen in practice).
     *
     * @param startNodeId the ID of the node from which we are resuming
     * @param state       the current traversal state (mutated in place)
     * @param trace       the running trace list (a RESUMED step may be appended)
     * @return the resolved next node, or {@code null}
     */
    public WorkflowNodeDto resolveSequentialResumeNode(String startNodeId,
                                                        TraversalExecutionState state,
                                                        List<StepRecordDto> trace) {
        WorkflowNodeDto suspendedNode = state.nodeMap.get(startNodeId);
        if (suspendedNode == null) {
            throw new IllegalStateException("Suspended node not found in graph: " + startNodeId);
        }

        String nodeType = suspendedNode.getType() != null
                ? suspendedNode.getType().toUpperCase() : "";

        switch (nodeType) {

            case "SUB_WORKFLOW" -> {
                applySubWorkflowOutputMapping(suspendedNode, state);
                // fall through — the edge is chosen below (currentNode == null path)
            }

            case "COMMAND" -> {
                String executionMode = extractString(suspendedNode.getData(), "executionMode");
                if ("ASYNC".equalsIgnoreCase(executionMode)) {
                    runtimeGraphManager.markNodeActive(
                            suspendedNode, state.activeNodes, state.instance, state.runtimeGraph);
                    trace.add(buildResumedStep(state.nextStepIdx(), startNodeId,
                            suspendedNode, "Resumed workflow at asynchronous command node: "
                                    + suspendedNode.getLabel()));
                    return suspendedNode;  // the COMMAND executor will handle the rest
                }
                // synchronous COMMAND: fall through to generic edge-choose logic
            }

            case "WAIT_EVENT" -> {
                WorkflowNodeDto next = resolveWaitEventResumeTarget(startNodeId, suspendedNode, state, trace);
                if (next != null) return next;
            }

            default -> {
                // BUCKET and generic: fall through to edge-choose below
            }
        }

        // Generic resume path: choose the outgoing edge and advance frontier
        return resolveGenericResumeNode(startNodeId, suspendedNode, state, trace);
    }

    // -----------------------------------------------------------------------
    // Activation-based traversal resume
    // -----------------------------------------------------------------------

    /**
     * Handles resume logic for the <em>activation-based</em> traversal.
     *
     * <p>Applies any necessary output mapping and marks the correct target node
     * as active so the activation loop picks it up on the next iteration.
     *
     * @param startNodeId the ID of the suspended node we are resuming from
     * @param state       the current traversal state
     * @param trace       the running trace list (a RESUMED step will be appended)
     */
    public void handleActivationBasedResume(String startNodeId,
                                             TraversalExecutionState state,
                                             List<StepRecordDto> trace) {
        WorkflowNodeDto suspendedNode = state.nodeMap.get(startNodeId);
        if (suspendedNode == null) return;

        String nodeType = suspendedNode.getType() != null
                ? suspendedNode.getType().toUpperCase() : "";

        if ("SUB_WORKFLOW".equalsIgnoreCase(nodeType)) {
            applySubWorkflowOutputMapping(suspendedNode, state);

        } else if ("WAIT_EVENT".equalsIgnoreCase(nodeType)) {
            String routingValue = extractRoutingValue(state.context);

            String targetNodeId = resolveRouteTarget(suspendedNode, routingValue);
            if (targetNodeId == null) {
                targetNodeId = extractString(suspendedNode.getData(), "defaultRoute");
            }

            if (targetNodeId != null && !targetNodeId.isBlank()
                    && state.nodeMap.containsKey(targetNodeId)) {
                WorkflowNodeDto targetNode = state.nodeMap.get(targetNodeId);
                runtimeGraphManager.markNodeActive(
                        targetNode, state.activeNodes, state.instance, state.runtimeGraph);

                final String finalTarget = targetNodeId;
                state.version.getDefinition().getEdges().stream()
                        .filter(e -> startNodeId.equals(e.getSource())
                                && finalTarget.equals(e.getTarget()))
                        .findFirst()
                        .ifPresent(edgeObj -> runtimeGraphManager.markEdgeActive(edgeObj, state.activeEdges));
            }
        }

        // Always append a RESUMED step
        trace.add(buildResumedStep(state.nextStepIdx(), startNodeId, suspendedNode,
                "Resumed workflow execution from suspended node: " + suspendedNode.getLabel()));
    }

    // -----------------------------------------------------------------------
    // Synchronous resumption (ThreadLocal fast path)
    // -----------------------------------------------------------------------

    /**
     * Attempts to resume a suspended node <em>synchronously within the same
     * traversal pass</em> — used by the event routing layer when it can deliver
     * an event while the traversal stack is still active.
     *
     * <p>Copied verbatim from {@code GraphTraversalEngine#trySynchronousResumption}.
     */
    public static boolean trySynchronousResumption(String instanceId,
                                                    String eventType,
                                                    String targetNodeId,
                                                    Map<String, Object> payload) {
        TraversalContext travCtx = TraversalContextHolder.get();
        if (travCtx == null || !travCtx.instanceId.equals(instanceId)) return false;

        WorkflowNodeDto suspendedNode = travCtx.nodeMap.get(targetNodeId);
        if (suspendedNode == null) return false;

        if (payload != null) travCtx.context.putAll(payload);

        if (travCtx.suspendedNodes != null) travCtx.suspendedNodes.remove(suspendedNode);

        // Determine routing value from payload
        String routingValue = null;
        if (payload != null) {
            for (String key : List.of("status", "value", "outcome")) {
                if (payload.containsKey(key) && payload.get(key) != null) {
                    routingValue = String.valueOf(payload.get(key));
                    break;
                }
            }
        }

        // Find target node to enqueue
        String nextNodeId = resolveRouteTargetStatic(suspendedNode, routingValue);
        if (nextNodeId == null && suspendedNode.getData() != null) {
            Object defRoute = suspendedNode.getData().get("defaultRoute");
            if (defRoute != null) nextNodeId = String.valueOf(defRoute);
        }
        if (nextNodeId == null) {
            List<WorkflowEdgeDto> outEdges =
                    travCtx.edgesBySource.getOrDefault(targetNodeId, List.of());
            if (!outEdges.isEmpty()) nextNodeId = outEdges.get(0).getTarget();
        }

        if (nextNodeId != null && !nextNodeId.isBlank() && travCtx.nodeMap.containsKey(nextNodeId)) {
            travCtx.activeFrontiers.add(travCtx.nodeMap.get(nextNodeId));

            final String srcId  = targetNodeId;
            final String destId = nextNodeId;
            for (List<WorkflowEdgeDto> edges : travCtx.edgesBySource.values()) {
                for (WorkflowEdgeDto edge : edges) {
                    if (srcId.equals(edge.getSource()) && destId.equals(edge.getTarget())) {
                        boolean hasEdge = travCtx.activeEdges.stream()
                                .anyMatch(e -> edge.getId().equals(e.get("id")));
                        if (!hasEdge) {
                            travCtx.activeEdges.add(RuntimeGraphManager.convertEdgeToMap(edge));
                        }
                    }
                }
            }
        }
        return true;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void applySubWorkflowOutputMapping(WorkflowNodeDto suspendedNode,
                                                TraversalExecutionState state) {
        @SuppressWarnings("unchecked")
        Map<String, Object> childOutputs = (Map<String, Object>) state.context.get("childOutputs");
        if (childOutputs == null) return;

        Object outputMappingObj = suspendedNode.getData() != null
                ? suspendedNode.getData().get("outputMapping") : null;
        if (outputMappingObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<?, ?> outputMap = (Map<?, ?>) outputMappingObj;
            for (Map.Entry<?, ?> entry : outputMap.entrySet()) {
                String childVar  = String.valueOf(entry.getKey());
                String parentVar = String.valueOf(entry.getValue());
                Object val = childOutputs.get(childVar);
                if (val != null) state.context.put(parentVar, val);
            }
        }
        state.context.remove("childOutputs");
        state.context.remove("childInstanceId");
    }

    private WorkflowNodeDto resolveWaitEventResumeTarget(String startNodeId,
                                                          WorkflowNodeDto suspendedNode,
                                                          TraversalExecutionState state,
                                                          List<StepRecordDto> trace) {
        String routingValue = extractRoutingValue(state.context);
        String targetNodeId = resolveRouteTarget(suspendedNode, routingValue);
        if (targetNodeId == null && suspendedNode.getData() != null) {
            targetNodeId = extractString(suspendedNode.getData(), "defaultRoute");
        }

        if (targetNodeId == null || targetNodeId.isBlank()
                || !state.nodeMap.containsKey(targetNodeId)) return null;

        WorkflowNodeDto targetNode = state.nodeMap.get(targetNodeId);
        runtimeGraphManager.markNodeActive(
                suspendedNode, state.activeNodes, state.instance, state.runtimeGraph);

        final String finalTarget = targetNodeId;
        state.version.getDefinition().getEdges().stream()
                .filter(e -> startNodeId.equals(e.getSource()) && finalTarget.equals(e.getTarget()))
                .findFirst()
                .ifPresent(edgeObj -> runtimeGraphManager.markEdgeActive(edgeObj, state.activeEdges));

        if (state.instance != null) state.instance.setRuntimeGraph(state.runtimeGraph);

        trace.add(buildResumedStep(state.nextStepIdx(), startNodeId, suspendedNode,
                "Resumed workflow from wait event node: " + suspendedNode.getLabel()
                        + " (Routed value: " + routingValue + " -> " + targetNodeId + ")"));

        return targetNode;
    }

    private WorkflowNodeDto resolveGenericResumeNode(String startNodeId,
                                                      WorkflowNodeDto suspendedNode,
                                                      TraversalExecutionState state,
                                                      List<StepRecordDto> trace) {
        List<WorkflowEdgeDto> outEdges = state.edgesBySource.getOrDefault(startNodeId, List.of());
        if (outEdges.isEmpty()) return null;

        WorkflowEdgeDto chosen;
        if (outEdges.size() == 1) {
            chosen = outEdges.get(0);
        } else if (suspendedNode != null && "BUCKET".equalsIgnoreCase(suspendedNode.getType())) {
            String outcome = (String) state.context.get("lastOutcome");
            chosen = edgeSelector.chooseBucketEdgeByOutcome(outEdges, outcome, state.spelCtx);
        } else {
            chosen = edgeSelector.chooseEdge(outEdges, state.spelCtx, true);
        }

        if (chosen == null) return null;

        WorkflowNodeDto nextNode = state.nodeMap.get(chosen.getTarget());
        runtimeGraphManager.markNodeActive(
                suspendedNode, state.activeNodes, state.instance, state.runtimeGraph);
        runtimeGraphManager.markEdgeActive(chosen, state.activeEdges);

        if (state.instance != null) state.instance.setRuntimeGraph(state.runtimeGraph);

        trace.add(buildResumedStep(state.nextStepIdx(), startNodeId, suspendedNode,
                "Resumed workflow from node: " + suspendedNode.getLabel()
                        + " via edge: " + chosen.getId()));

        return nextNode;
    }

    private static String extractRoutingValue(Map<String, Object> context) {
        for (String key : List.of("status", "value", "outcome")) {
            if (context.containsKey(key) && context.get(key) != null) {
                return String.valueOf(context.get(key));
            }
        }
        return null;
    }

    private static String resolveRouteTarget(WorkflowNodeDto node, String routingValue) {
        return resolveRouteTargetStatic(node, routingValue);
    }

    private static String resolveRouteTargetStatic(WorkflowNodeDto node, String routingValue) {
        if (routingValue == null || node.getData() == null) return null;
        Object routesObj = node.getData().get("routes");
        if (!(routesObj instanceof List)) return null;
        for (Object routeItem : (List<?>) routesObj) {
            if (routeItem instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<?, ?> routeMap = (Map<?, ?>) routeItem;
                String val    = String.valueOf(routeMap.get("value"));
                String target = String.valueOf(routeMap.get("target"));
                if (val != null && val.equalsIgnoreCase(routingValue)) return target;
            }
        }
        return null;
    }

    private StepRecordDto buildResumedStep(int idx, String nodeId,
                                            WorkflowNodeDto node, String notes) {
        StepRecordDto s = new StepRecordDto();
        s.setStepIndex(idx);
        s.setNodeId(nodeId);
        s.setNodeType(node.getType());
        s.setLabel(node.getLabel());
        s.setStatus("RESUMED");
        s.setNotes(notes);
        s.setEnteredAt(LocalDateTime.now());
        s.setExitedAt(LocalDateTime.now());
        return s;
    }

    private static String extractString(Map<String, Object> data, String key) {
        if (data == null) return null;
        Object val = data.get(key);
        return val != null ? String.valueOf(val) : null;
    }
}
