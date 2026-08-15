package com.vi.atlas.workflow.service.traversal.node;

import com.vi.atlas.common.dto.*;
import com.vi.atlas.workflow.entity.WorkflowInstance;
import com.vi.atlas.workflow.repository.WorkflowInstanceRepository;
import com.vi.atlas.workflow.service.ExecutionService;
import com.vi.atlas.workflow.service.traversal.EventSubscriptionManager;
import com.vi.atlas.workflow.service.traversal.NodeExecutionResult;
import com.vi.atlas.workflow.service.traversal.SpelEvaluator;
import com.vi.atlas.workflow.service.traversal.TaskRecorder;
import com.vi.atlas.workflow.service.traversal.TraversalExecutionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Executes a SUB_WORKFLOW node.
 *
 * <p>Applies input mapping, triggers child workflow execution via
 * {@link ExecutionService}, then either:
 * <ul>
 *   <li>Maps child outputs back and continues if child completed synchronously</li>
 *   <li>Suspends and registers a {@code CHILD_WORKFLOW_COMPLETED} event subscription
 *       if the child itself suspended</li>
 * </ul>
 */
@Component
public class SubWorkflowNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(SubWorkflowNodeExecutor.class);

    @Autowired private TaskRecorder                taskRecorder;
    @Autowired private SpelEvaluator               spel;
    @Autowired private EventSubscriptionManager    eventSubscriptionManager;
    @Autowired private WorkflowInstanceRepository  instanceRepository;
    @Autowired @Lazy private ExecutionService      executionService;

    @Override
    public NodeExecutionResult execute(WorkflowNodeDto node,
                                       TraversalExecutionState state,
                                       StepRecordDto step,
                                       List<StepRecordDto> trace) {
        var ti = taskRecorder.recordTaskStart(state.instance, node, state.context);

        String childWorkflowKey = extractString(node.getData(), "childWorkflowKey");
        if (childWorkflowKey == null || childWorkflowKey.isBlank()) {
            step.setStatus("FAILED");
            step.setNotes("SUB_WORKFLOW node has no childWorkflowKey configured.");
            trace.add(step);
            taskRecorder.recordTaskCompletion(ti, Map.of(), "FAILED");
            return NodeExecutionResult.failed();
        }

        // Build child input context
        Map<String, Object> childInput = buildChildInput(node, state);

        try {
            ExecutionRequestDto childRequest = new ExecutionRequestDto();
            childRequest.setBusinessKey(state.instance != null ? state.instance.getBusinessKey() : null);
            childRequest.setContext(childInput);
            childRequest.setContextId(UUID.randomUUID().toString());

            ExecutionLogDto childExecution = executionService.execute(childWorkflowKey, childRequest);

            if ("COMPLETED".equalsIgnoreCase(childExecution.getStatus())) {
                // Child finished synchronously â€” map outputs back
                WorkflowInstance childInst =
                        instanceRepository.findById(childExecution.getInstanceId()).orElse(null);
                Map<String, Object> childOutputs =
                        childInst != null ? childInst.getSerializedContext() : Map.of();

                applyOutputMapping(node, state.context, childOutputs);

                step.setStatus("COMPLETED");
                step.setNotes("Sub-workflow '" + childWorkflowKey + "' completed synchronously.");
                taskRecorder.recordTaskCompletion(
                        ti, childOutputs != null ? childOutputs : Map.of(), "COMPLETED");

                if (state.instance != null) state.instance.setRuntimeGraph(state.runtimeGraph);

                List<WorkflowEdgeDto> outEdges = state.edgesBySource.getOrDefault(node.getId(), List.of());
                if (!outEdges.isEmpty()) {
                    return NodeExecutionResult.next(
                            state.nodeMap.get(outEdges.get(0).getTarget()), outEdges.get(0).getId());
                }
                return NodeExecutionResult.COMPLETED;

            } else {
                // Child suspended â€” wait for CHILD_WORKFLOW_COMPLETED event
                step.setStatus("WAITING");
                step.setNotes("Sub-workflow '" + childWorkflowKey
                        + "' suspended. Waiting for child instance completion (ID: "
                        + childExecution.getInstanceId() + ").");
                trace.add(step);
                taskRecorder.recordTaskCompletion(
                        ti, Map.of("childInstanceId", childExecution.getInstanceId()), "WAITING");
                eventSubscriptionManager.createEventSubscription(
                        state.instance, "CHILD_WORKFLOW_COMPLETED", node.getId(),
                        Map.of("childInstanceId", childExecution.getInstanceId()));

                state.suspendedNodes.add(node);
                return NodeExecutionResult.suspended();
            }

        } catch (Exception e) {
            log.error("Failed to execute child workflow '{}': {}", childWorkflowKey, e.getMessage(), e);
            step.setStatus("FAILED");
            step.setNotes("Failed to execute child workflow: " + e.getMessage());
            trace.add(step);
            taskRecorder.recordTaskCompletion(ti, Map.of("error", e.getMessage()), "FAILED");
            return NodeExecutionResult.failed();
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private Map<String, Object> buildChildInput(WorkflowNodeDto node, TraversalExecutionState state) {
        Map<String, Object> childInput = new HashMap<>();
        if (state.instance != null && state.instance.getBusinessKey() != null) {
            childInput.put("businessKey", state.instance.getBusinessKey());
        }
        if (state.context.get("businessKey") != null) {
            childInput.put("businessKey", state.context.get("businessKey"));
        }
        if (state.context.get("cafId") != null) {
            childInput.put("cafId", state.context.get("cafId"));
        }

        Object inputMappingObj = node.getData() != null ? node.getData().get("inputMapping") : null;
        if (inputMappingObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<?, ?> inputMap = (Map<?, ?>) inputMappingObj;
            for (Map.Entry<?, ?> entry : inputMap.entrySet()) {
                String sourceVar = String.valueOf(entry.getKey());
                String targetVar = String.valueOf(entry.getValue());
                Object value;
                if (sourceVar.contains(".") || sourceVar.contains("[") || sourceVar.contains("'")) {
                    try {
                        value = spel.evaluate(sourceVar, state.spelCtx);
                    } catch (Exception e) {
                        value = state.context.get(sourceVar);
                    }
                } else {
                    value = state.context.get(sourceVar);
                }
                if (value != null) childInput.put(targetVar, value);
            }
        }
        return childInput;
    }

    private void applyOutputMapping(WorkflowNodeDto node,
                                     Map<String, Object> parentContext,
                                     Map<String, Object> childOutputs) {
        if (childOutputs == null) return;
        Object outputMappingObj = node.getData() != null ? node.getData().get("outputMapping") : null;
        if (!(outputMappingObj instanceof Map)) return;
        @SuppressWarnings("unchecked")
        Map<?, ?> outputMap = (Map<?, ?>) outputMappingObj;
        for (Map.Entry<?, ?> entry : outputMap.entrySet()) {
            String childVar  = String.valueOf(entry.getKey());
            String parentVar = String.valueOf(entry.getValue());
            Object val = childOutputs.get(childVar);
            if (val != null) parentContext.put(parentVar, val);
        }
    }

    private String extractString(Map<String, Object> data, String key) {
        if (data == null) return null;
        Object val = data.get(key);
        return val != null ? String.valueOf(val) : null;
    }
}
