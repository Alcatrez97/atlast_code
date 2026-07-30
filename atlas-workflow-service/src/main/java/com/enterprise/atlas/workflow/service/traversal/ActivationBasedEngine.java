package com.enterprise.atlas.workflow.service.traversal;

import com.enterprise.atlas.workflow.entity.WorkflowInstance;
import com.enterprise.atlas.workflow.entity.WorkflowVersion;
import com.enterprise.atlas.common.dto.StepRecordDto;
import com.enterprise.atlas.common.dto.WorkflowEdgeDto;
import com.enterprise.atlas.common.dto.WorkflowNodeDto;
import com.enterprise.atlas.common.dto.WorkflowGraphDto;
import com.enterprise.atlas.workflow.service.GraphTraversalEngine;
import com.enterprise.atlas.workflow.service.CommandRegistry;
import com.enterprise.atlas.workflow.service.ExecutionService;
import com.enterprise.atlas.workflow.service.EventRoutingService;
import com.enterprise.atlas.workflow.service.traversal.NodeExecutionResult;
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
 * Helper component that contains the activation‑based traversal algorithm.
 * Extracted from {@link GraphTraversalEngine} to improve readability.
 */
@Component
public class ActivationBasedEngine {

    private static final Logger log = LoggerFactory.getLogger(ActivationBasedEngine.class);
    private static final int MAX_STEPS = 200; // keep same circuit‑breaker

    // ---- Collaborator beans ----
    @Autowired private TraversalHelper traversalHelper;
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

    public GraphTraversalEngine.TraversalResult runActivationBasedTraversal(
            WorkflowGraphDto graph,
            TraversalExecutionState state,
            String startNodeId,
            List<StepRecordDto> trace,
            Map<String, WorkflowNodeDto> nodeMap,
            List<Map<String, Object>> activeNodes,
            List<Map<String, Object>> activeEdges,
            Map<String, Object> runtimeGraph,
            List<WorkflowNodeDto> suspendedNodes,
            StandardEvaluationContext spelCtx,
            WorkflowInstance instance,
            String instanceId,
            String contextId,
            WorkflowVersion version,
            int[] stepIdx) {

        log.info("Running activation-based traversal loop for instance: {}", instanceId);

        WorkflowNodeDto startNode = graph.getNodes().stream()
                .filter(n -> "START".equalsIgnoreCase(n.getType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Workflow graph has no START node"));

        boolean isFreshStart = activeNodes.isEmpty();

        if (isFreshStart) {
            runtimeGraphManager.markNodeActive(startNode, activeNodes, instance, runtimeGraph);
            var ti = taskRecorder.recordTaskStart(instance, startNode, state.context);
            taskRecorder.recordTaskCompletion(ti, Map.of(), "COMPLETED");
            trace.add(taskRecorder.buildStep(stepIdx[0]++, startNode, "ENTERED", null, null, null,
                    "Workflow execution started (Activation-Based)."));
        } else if (startNodeId != null && !startNodeId.isBlank()) {
            resumeRouter.handleActivationBasedResume(startNodeId, state, trace);
        }

        boolean progress = true;
        boolean suspended = false;
        String suspendedNodeId = null;
        String suspendedNodeLabel = null;
        String outcomeBucketId = null;

        while (progress && stepIdx[0] < MAX_STEPS) {
            progress = false;

            for (WorkflowNodeDto node : graph.getNodes()) {
                final String nodeId = node.getId();

                if (traversalHelper.isActiveNode(nodeId)) continue;

                TraversalHelper.ActivationDecision decision = traversalHelper.evaluateActivation(node, nodeId, instanceId, spelCtx);

                if (!decision.canEvaluate) continue;

                if (decision.isBypassed) {
                    var ti = taskRecorder.recordTaskStart(instance, node, state.context);
                    taskRecorder.recordTaskCompletion(ti, Map.of(), "SKIPPED");
                    runtimeGraphManager.markNodeActive(node, activeNodes, instance, runtimeGraph);
                    traversalHelper.markIncomingEdgesActive(nodeId, instanceId, activeEdges, state);
                    if (instance != null) instance.setRuntimeGraph(runtimeGraph);
                    trace.add(taskRecorder.buildStep(stepIdx[0]++, node, "SKIPPED", null, false, null,
                            "Bypassed via Dead-Path Elimination (structural join)."));
                    progress = true;
                    break;
                }

                boolean eligible = traversalHelper.evaluateBusinessEligibility(node, spelCtx);
                if (!eligible) {
                    var ti = taskRecorder.recordTaskStart(instance, node, state.context);
                    taskRecorder.recordTaskCompletion(ti, Map.of(), "SKIPPED");
                    runtimeGraphManager.markNodeActive(node, activeNodes, instance, runtimeGraph);
                    traversalHelper.markIncomingEdgesActive(nodeId, instanceId, activeEdges, state);
                    if (instance != null) instance.setRuntimeGraph(runtimeGraph);
                    String rule = traversalHelper.extractString(node.getData(), "businessEligibilityRule");
                    if (rule == null || rule.isBlank()) rule = traversalHelper.extractString(node.getData(), "activationCondition");
                    trace.add(taskRecorder.buildStep(stepIdx[0]++, node, "SKIPPED", rule, false, null,
                            "Bypassed: Business eligibility condition failed."));
                    progress = true;
                    break;
                }

                runtimeGraphManager.markNodeActive(node, activeNodes, instance, runtimeGraph);
                traversalHelper.markIncomingEdgesActiveForActivated(nodeId, activeEdges, state);
                if (instance != null) instance.setRuntimeGraph(runtimeGraph);

                String nodeType = Optional.ofNullable(node.getType()).orElse("UNKNOWN").toUpperCase();
                StepRecordDto step = new StepRecordDto();
                step.setStepIndex(stepIdx[0]++);
                step.setNodeId(node.getId());
                step.setNodeType(nodeType);
                step.setLabel(Optional.ofNullable(node.getLabel()).orElse(nodeType));
                step.setEnteredAt(LocalDateTime.now());

                switch (nodeType) {
                    case "BUCKET" -> {
                        var ti = taskRecorder.recordTaskStart(instance, node, state.context);
                        taskRecorder.recordTaskCompletion(ti, Map.of(), "WAITING");
                        step.setStatus("WAITING");
                        step.setNotes("Suspended execution. Waiting on business outcome bucket: " + step.getLabel());
                        step.setExitedAt(LocalDateTime.now());
                        trace.add(step);

                        String bucketId = node.getData() != null ? (String) node.getData().get("bucketId") : null;
                        if (bucketId == null) bucketId = node.getId();
                        outcomeBucketId = bucketId;

                        bucketSuspensionManager.createBucketRevertStatusAndFormPending(instanceId, contextId, bucketId, node, version);
                        eventSubscriptionManager.createEventSubscription(instance, bucketId, node.getId(), Map.of());

                        suspended = true;
                        suspendedNodeId = node.getId();
                        suspendedNodeLabel = node.getLabel();
                        progress = true;
                    }
                    case "SUB_WORKFLOW" -> {
                        NodeExecutionResult result = nodeExecutorRegistry.get("SUB_WORKFLOW")
                                .execute(node, state, step, trace);
                        if (result.isSuspended()) {
                            suspended = true;
                            suspendedNodeId = node.getId();
                            suspendedNodeLabel = node.getLabel();
                        }
                        progress = true;
                    }
                    case "COMMAND" -> {
                        NodeExecutionResult result = nodeExecutorRegistry.get("COMMAND")
                                .execute(node, state, step, trace);
                        if (result.isSuspended()) {
                            suspended = true;
                            suspendedNodeId = node.getId();
                            suspendedNodeLabel = node.getLabel();
                        }
                        progress = true;
                    }
                    case "WAIT_EVENT" -> {
                        var ti = taskRecorder.recordTaskStart(instance, node, state.context);
                        taskRecorder.recordTaskCompletion(ti, Map.of(), "WAITING");
                        String eventType = traversalHelper.extractString(node.getData(), "eventType");
                        if (eventType == null || eventType.isBlank()) eventType = "GENERIC_EVENT";
                        step.setStatus("WAITING");
                        step.setNotes("Suspended execution. Waiting on event: " + eventType);
                        step.setExitedAt(LocalDateTime.now());
                        trace.add(step);
                        eventSubscriptionManager.createEventSubscription(instance, eventType, node.getId(), Map.of());
                        suspended = true;
                        suspendedNodeId = node.getId();
                        suspendedNodeLabel = node.getLabel();
                        progress = true;
                    }
                    case "RULE" -> {
                        NodeExecutionResult result = nodeExecutorRegistry.get("RULE")
                                .execute(node, state, step, trace);
                        step.setExitedAt(LocalDateTime.now());
                        if (!trace.contains(step)) trace.add(step);
                        progress = true;
                    }
                    case "DECISION" -> {
                        nodeExecutorRegistry.get("DECISION").execute(node, state, step, trace);
                        step.setExitedAt(LocalDateTime.now());
                        if (!trace.contains(step)) trace.add(step);
                        progress = true;
                    }
                    case "TIMER" -> {
                        nodeExecutorRegistry.get("TIMER").execute(node, state, step, trace);
                        step.setExitedAt(LocalDateTime.now());
                        if (!trace.contains(step)) trace.add(step);
                        progress = true;
                    }
                    case "PARALLEL" -> {
                        nodeExecutorRegistry.get("PARALLEL").execute(node, state, step, trace);
                        step.setExitedAt(LocalDateTime.now());
                        if (!trace.contains(step)) trace.add(step);
                        progress = true;
                    }
                    case "JOIN" -> {
                        nodeExecutorRegistry.get("JOIN").execute(node, state, step, trace);
                        step.setExitedAt(LocalDateTime.now());
                        if (!trace.contains(step)) trace.add(step);
                        progress = true;
                    }
                    case "END" -> {
                        var ti = taskRecorder.recordTaskStart(instance, node, state.context);
                        taskRecorder.recordTaskCompletion(ti, Map.of(), "COMPLETED");
                        step.setStatus("COMPLETED");
                        step.setNotes("Workflow execution reached END node.");
                        step.setExitedAt(LocalDateTime.now());
                        trace.add(step);
                        progress = true;
                    }
                    default -> {
                        var ti = taskRecorder.recordTaskStart(instance, node, state.context);
                        taskRecorder.recordTaskCompletion(ti, Map.of(), "COMPLETED");
                        step.setStatus("COMPLETED");
                        step.setExitedAt(LocalDateTime.now());
                        trace.add(step);
                        progress = true;
                    }
                }

                if (progress) break;
            }
        }

        if (stepIdx[0] >= MAX_STEPS) {
            log.warn("Activation-based loop halted: exceeded max steps ({})", MAX_STEPS);
        }

        if (!suspended) {
            boolean endNodeCompleted = graph.getNodes().stream()
                    .filter(n -> "END".equalsIgnoreCase(n.getType()))
                    .anyMatch(n -> "COMPLETED".equalsIgnoreCase(taskRecorder.getTaskStatus(instanceId, n.getId())));
            if (!endNodeCompleted) {
                suspended = true;
                for (WorkflowNodeDto node : graph.getNodes()) {
                    if ("WAITING".equalsIgnoreCase(taskRecorder.getTaskStatus(instanceId, node.getId()))) {
                        suspendedNodeId = node.getId();
                        suspendedNodeLabel = node.getLabel();
                        break;
                    }
                }
            }
        }

        return new GraphTraversalEngine.TraversalResult(trace, suspended, suspendedNodeId, suspendedNodeLabel,
                outcomeBucketId, runtimeGraph);
    }
}
