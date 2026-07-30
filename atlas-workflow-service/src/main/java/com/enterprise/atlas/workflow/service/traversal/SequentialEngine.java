package com.enterprise.atlas.workflow.service.traversal;

import com.enterprise.atlas.common.dto.StepRecordDto;
import com.enterprise.atlas.common.dto.WorkflowEdgeDto;
import com.enterprise.atlas.common.dto.WorkflowGraphDto;
import com.enterprise.atlas.common.dto.WorkflowNodeDto;
import com.enterprise.atlas.workflow.entity.WorkflowInstance;
import com.enterprise.atlas.workflow.entity.WorkflowVersion;
import com.enterprise.atlas.workflow.service.CommandRegistry;
import com.enterprise.atlas.workflow.service.EventRoutingService;
import com.enterprise.atlas.workflow.service.ExecutionService;
import com.enterprise.atlas.workflow.service.GraphTraversalEngine;
import com.enterprise.atlas.workflow.service.traversal.NodeExecutionResult;
import com.enterprise.atlas.workflow.service.traversal.node.NodeExecutor;
import com.enterprise.atlas.workflow.service.traversal.node.NodeExecutorRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Helper component that contains the sequential (pointer‑based) traversal algorithm.
 * Extracted from {@link GraphTraversalEngine} to improve readability.
 */
@Component
public class SequentialEngine {

    private static final Logger log = LoggerFactory.getLogger(SequentialEngine.class);
    private static final int MAX_STEPS = 200;

    // ---- Collaborator beans (same as GraphTraversalEngine) ----
    @Autowired private SpelEvaluator spelEvaluator;
    @Autowired private EdgeSelector edgeSelector;
    @Autowired private RuntimeGraphManager runtimeGraphManager;
    @Autowired private TaskRecorder taskRecorder;
    @Autowired private EventSubscriptionManager eventSubscriptionManager;
    @Autowired private BucketSuspensionManager bucketSuspensionManager;
    @Autowired private ResumeRouter resumeRouter;
    @Autowired private NodeExecutorRegistry nodeExecutorRegistry;
    @Autowired private CommandRegistry commandRegistry;
    @Autowired @Lazy private ExecutionService executionService;
    @Autowired @Lazy private EventRoutingService eventRoutingService;

    public GraphTraversalEngine.TraversalResult runSequentialTraversal(
            WorkflowGraphDto graph,
            TraversalExecutionState state,
            String startNodeId,
            List<StepRecordDto> trace,
            Map<String, WorkflowNodeDto> nodeMap,
            Map<String, List<WorkflowEdgeDto>> edgesBySource,
            List<Map<String, Object>> activeNodes,
            List<Map<String, Object>> activeEdges,
            Map<String, Object> runtimeGraph,
            List<WorkflowNodeDto> activeFrontiers,
            List<WorkflowNodeDto> suspendedNodes,
            StandardEvaluationContext spelCtx,
            WorkflowInstance instance,
            String instanceId,
            String contextId,
            WorkflowVersion version,
            int[] stepIdx,
            Map<String, Object> context) {

        WorkflowNodeDto currentNode = null;

        if (startNodeId != null && !startNodeId.isBlank()) {
            currentNode = resumeRouter.resolveSequentialResumeNode(startNodeId, state, trace);
        } else {
            // Fresh start from START node
            currentNode = graph.getNodes().stream()
                    .filter(n -> "START".equalsIgnoreCase(n.getType()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Workflow graph has no START node"));
            runtimeGraphManager.markNodeActive(currentNode, activeNodes, instance, runtimeGraph);
        }

        Set<String> visited = new HashSet<>();
        if (currentNode != null) activeFrontiers.add(currentNode);

        while (!activeFrontiers.isEmpty() && stepIdx[0] < MAX_STEPS) {
            currentNode = activeFrontiers.remove(0);

            // Cycle detection (skip for JOIN nodes which must be re‑visited)
            if (!"JOIN".equalsIgnoreCase(currentNode.getType())) {
                if (visited.contains(currentNode.getId())) {
                    trace.add(taskRecorder.buildStep(stepIdx[0]++, currentNode, "SKIPPED",
                            null, null, null, "Cycle detected – node already visited; halting."));
                    continue;
                }
                visited.add(currentNode.getId());
            }

            runtimeGraphManager.markNodeActive(currentNode, activeNodes, instance, runtimeGraph);

            String nodeType = Optional.ofNullable(currentNode.getType()).orElse("UNKNOWN").toUpperCase();
            log.info("Engine traversing node ID: {}, Label: {}, Type: {}",
                    currentNode.getId(), currentNode.getLabel(), nodeType);

            StepRecordDto step = new StepRecordDto();
            step.setStepIndex(stepIdx[0]++);
            step.setNodeId(currentNode.getId());
            step.setNodeType(nodeType);
            step.setLabel(Optional.ofNullable(currentNode.getLabel()).orElse(nodeType));
            step.setEnteredAt(LocalDateTime.now());

            // Delegate to the appropriate NodeExecutor
            NodeExecutor executor = nodeExecutorRegistry.get(nodeType);
            NodeExecutionResult result = executor.execute(currentNode, state, step, trace);

            // If the executor already added the step to trace (suspend cases), skip re‑adding
            if (!trace.contains(step)) {
                if (result.getEdgeTakenId() != null) {
                    step.setEdgeTaken(result.getEdgeTakenId());
                    // Track the taken edge in the runtime graph
                    final String finalEdgeId = result.getEdgeTakenId();
                    boolean hasEdge = activeEdges.stream().anyMatch(e -> finalEdgeId.equals(e.get("id")));
                    if (!hasEdge) {
                        graph.getEdges().stream()
                                .filter(e -> finalEdgeId.equals(e.getId()))
                                .findFirst()
                                .ifPresent(edgeObj -> activeEdges.add(RuntimeGraphManager.convertEdgeToMap(edgeObj)));
                    }
                    log.info("NodeID: {} matched next Node ID: {} via edge ID: {}",
                            currentNode.getId(),
                            result.getNextNode() != null ? result.getNextNode().getId() : "null",
                            finalEdgeId);
                }
                step.setExitedAt(LocalDateTime.now());
                step.setDurationMs(step.getEnteredAt() != null && step.getExitedAt() != null
                        ? java.time.Duration.between(step.getEnteredAt(), step.getExitedAt()).toMillis()
                        : 0L);
                trace.add(step);
            }

            if (instance != null) instance.setRuntimeGraph(runtimeGraph);

            if (!result.isContinueLoop() && result.getNextNode() != null) {
                activeFrontiers.add(result.getNextNode());
            }
        }

        if (stepIdx[0] >= MAX_STEPS) {
            log.warn("Traversal halted: exceeded max steps ({}) for workflow '{}'", MAX_STEPS, version.getWorkflowDefinition().getKey());
        }

        // Build result from collected suspended nodes
        if (!suspendedNodes.isEmpty()) {
            WorkflowNodeDto prim = suspendedNodes.get(0);
            String outcomeBucketId = null;
            for (WorkflowNodeDto sn : suspendedNodes) {
                if ("BUCKET".equalsIgnoreCase(sn.getType())) {
                    outcomeBucketId = sn.getData() != null ? (String) sn.getData().get("bucketId") : null;
                    if (outcomeBucketId == null) outcomeBucketId = sn.getId();
                    prim = sn;
                    break;
                }
            }
            return new GraphTraversalEngine.TraversalResult(trace, true, prim.getId(), prim.getLabel(),
                    outcomeBucketId, runtimeGraph);
        }

        return new GraphTraversalEngine.TraversalResult(trace, false, null, null, null, runtimeGraph);
    }
}
