package com.vi.atlas.workflow.service.traversal;

import com.vi.atlas.common.dto.StepRecordDto;
import com.vi.atlas.common.dto.WorkflowGraphDto;
import com.vi.atlas.common.dto.WorkflowNodeDto;
import com.vi.atlas.workflow.entity.WorkflowInstance;
import com.vi.atlas.workflow.entity.WorkflowVersion;
import com.vi.atlas.workflow.service.traversal.node.NodeExecutor;
import com.vi.atlas.workflow.service.traversal.node.NodeExecutorRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.EvaluationContext;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

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
    @Autowired private RuntimeGraphManager runtimeGraphManager;
    @Autowired private TaskRecorder taskRecorder;
    @Autowired private ResumeRouter resumeRouter;
    @Autowired private NodeExecutorRegistry nodeExecutorRegistry;

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
            EvaluationContext spelCtx,
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
                // If the node is non-idempotent (external side-effects or async/suspension boundary),
                // flush all prior in-memory tasks (including skipped dead paths) as a durable checkpoint.
                if (!taskRecorder.isIdempotent(node)) {
                    taskRecorder.flushPendingTasks(instanceId);
                }

                // Delegate node execution to the corresponding NodeExecutor from the registry
                NodeExecutor executor = nodeExecutorRegistry.get(nodeType);
                NodeExecutionResult result = executor.execute(node, state, step, trace);

                if (!trace.contains(step)) {
                    step.setExitedAt(LocalDateTime.now());
                    trace.add(step);
                }

                if (result.isSuspended()) {
                    taskRecorder.flushPendingTasks(instanceId);
                    suspended = true;
                    suspendedNodeId = node.getId();
                    suspendedNodeLabel = node.getLabel();
                    if ("BUCKET".equalsIgnoreCase(nodeType)) {
                        String bucketId = node.getData() != null ? (String) node.getData().get("bucketId") : null;
                        if (bucketId == null) bucketId = node.getId();
                        outcomeBucketId = bucketId;
                    }
                }

                progress = true;
                break;
            }
        }

        if (stepIdx[0] >= MAX_STEPS) {
            log.warn("Activation-based loop halted: exceeded max steps ({})", MAX_STEPS);
        }

        boolean endNodeCompleted = graph.getNodes().stream()
                .filter(n -> "END".equalsIgnoreCase(n.getType()))
                .anyMatch(n -> "COMPLETED".equalsIgnoreCase(taskRecorder.getTaskStatus(instanceId, n.getId())));

        if (endNodeCompleted) {
            suspended = false;
            suspendedNodeId = null;
            suspendedNodeLabel = null;
        } else if (!suspended) {
            suspended = true;
            for (WorkflowNodeDto node : graph.getNodes()) {
                if ("WAITING".equalsIgnoreCase(taskRecorder.getTaskStatus(instanceId, node.getId()))) {
                    suspendedNodeId = node.getId();
                    suspendedNodeLabel = node.getLabel();
                    break;
                }
            }
        }

        // Ensure all remaining in-memory tasks (including skipped dead-paths) are flushed in a single batch
        taskRecorder.flushPendingTasks(instanceId);

        return new GraphTraversalEngine.TraversalResult(trace, suspended, suspendedNodeId, suspendedNodeLabel,
                outcomeBucketId, runtimeGraph);
    }
}
