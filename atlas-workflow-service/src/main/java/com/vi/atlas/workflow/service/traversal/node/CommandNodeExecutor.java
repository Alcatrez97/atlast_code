package com.vi.atlas.workflow.service.traversal.node;

import com.vi.atlas.common.dto.StepRecordDto;
import com.vi.atlas.common.dto.WorkflowEdgeDto;
import com.vi.atlas.common.dto.WorkflowNodeDto;
import com.vi.atlas.workflow.entity.TaskInstance;
import com.vi.atlas.workflow.service.EventRoutingService;
import com.vi.atlas.workflow.service.traversal.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes a COMMAND node in both synchronous and asynchronous modes.
 *
 * <p><b>Sync mode</b>: executes the registered {@link com.vi.atlas.workflow.service.WorkflowCommand}
 * immediately in the current thread, applies output mapping, and continues traversal.
 *
 * <p><b>Async mode</b>: records a WAITING step, suspends the traversal, and schedules
 * the command to run in a background thread after the current DB transaction commits.
 * When complete, it routes a resume event via {@link EventRoutingService}.
 */
@Component
public class CommandNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(CommandNodeExecutor.class);

    @Autowired private TaskRecorder     taskRecorder;
    @Autowired private EventSubscriptionManager eventSubscriptionManager;
    @Autowired @Lazy private EventRoutingService eventRoutingService;

    // The actual command execution logic lives in GraphTraversalEngine so it has
    // access to CommandRegistry; we delegate via a functional interface set during wiring.
    private CommandExecutorDelegate commandDelegate;

    /** Called by GraphTraversalEngine during initialisation to wire in the command executor. */
    public void setCommandDelegate(CommandExecutorDelegate delegate) {
        this.commandDelegate = delegate;
    }

    @FunctionalInterface
    public interface CommandExecutorDelegate {
        Map<String, Object> execute(WorkflowNodeDto node,
                                    com.vi.atlas.workflow.entity.WorkflowInstance instance,
                                    Map<String, Object> context,
                                    String instanceId,
                                    String contextId,
                                    com.vi.atlas.workflow.entity.WorkflowVersion version,
                                    StandardEvaluationContext spelCtx);
    }

    @FunctionalInterface
    public interface AsyncTriggerDelegate {
        void trigger(WorkflowNodeDto node,
                     com.vi.atlas.workflow.entity.WorkflowInstance instance,
                     Map<String, Object> backgroundContext,
                     String instanceId,
                     String contextId,
                     com.vi.atlas.workflow.entity.WorkflowVersion version,
                     String eventType);
    }

    private AsyncTriggerDelegate asyncDelegate;

    public void setAsyncDelegate(AsyncTriggerDelegate delegate) {
        this.asyncDelegate = delegate;
    }

    @Override
    public NodeExecutionResult execute(WorkflowNodeDto node,
                                       TraversalExecutionState state,
                                       StepRecordDto step,
                                       List<StepRecordDto> trace) {
        String existingStatus = taskRecorder.getTaskStatus(state.instanceId, node.getId());
        var ti = taskRecorder.recordTaskStart(state.instance, node, state.context);

        String commandType    = extractString(node.getData(), "commandType");
        if (commandType == null) commandType = extractString(node.getData(), "type");

        String executionMode  = extractString(node.getData(), "executionMode");
        boolean isAsync       = "ASYNC".equalsIgnoreCase(executionMode);

        if (isAsync) {
            return handleAsyncCommand(node, state, step, trace, ti, commandType, existingStatus);
        } else {
            return handleSyncCommand(node, state, step, trace, ti, commandType);
        }
    }

    // -----------------------------------------------------------------------
    // Sync path
    // -----------------------------------------------------------------------

    private NodeExecutionResult handleSyncCommand(WorkflowNodeDto node,
                                                   TraversalExecutionState state,
                                                   StepRecordDto step,
                                                   List<StepRecordDto> trace,
                                                   TaskInstance ti,
                                                   String commandType) {
        Map<String, Object> commandOutput = Map.of();

        if (commandType != null) {
            try {
                commandOutput = commandDelegate.execute(
                        node, state.instance, state.context,
                        state.instanceId, state.contextId, state.version, state.spelCtx);
                step.setStatus("COMPLETED");
                step.setNotes("Executed command: " + commandType);
            } catch (Exception e) {
                log.error("COMMAND node '{}' execution failed: {}", node.getId(), e.getMessage(), e);
                step.setStatus("FAILED");
                step.setNotes("Failed to execute command: " + e.getMessage());
                taskRecorder.recordTaskCompletion(ti, Map.of("error", e.getMessage()), "FAILED");
                return NodeExecutionResult.failed();
            }
        } else {
            step.setStatus("COMPLETED");
            step.setNotes("COMMAND node has no commandType configured.");
        }

        // Store command outputs in the shared commandOutputs map on the context
        @SuppressWarnings("unchecked")
        Map<String, Object> parentObj = (Map<String, Object>)
                state.context.computeIfAbsent("commandOutputs", k -> new HashMap<String, Object>());
        parentObj.put(node.getId(), commandOutput);

        taskRecorder.recordTaskCompletion(ti, commandOutput, "COMPLETED");

        List<WorkflowEdgeDto> outEdges = state.edgesBySource.getOrDefault(node.getId(), List.of());
        if (!outEdges.isEmpty()) {
            return NodeExecutionResult.next(
                    state.nodeMap.get(outEdges.get(0).getTarget()), outEdges.get(0).getId());
        }
        return NodeExecutionResult.COMPLETED;
    }

    // -----------------------------------------------------------------------
    // Async path
    // -----------------------------------------------------------------------

    private NodeExecutionResult handleAsyncCommand(WorkflowNodeDto node,
                                                    TraversalExecutionState state,
                                                    StepRecordDto step,
                                                    List<StepRecordDto> trace,
                                                    TaskInstance ti,
                                                    String commandType,
                                                    String existingStatus) {
        boolean alreadyCompleted = "COMPLETED".equalsIgnoreCase(existingStatus);

        if (alreadyCompleted) {
            // Resuming â€” the async work is done, pick up outputs and continue
            log.info("COMMAND node {} resuming in ASYNC mode.", node.getId());
            step.setStatus("COMPLETED");
            step.setNotes("Resumed and completed asynchronous command: " + commandType);

            Map<String, Object> commandOutput = new HashMap<>();
            if (ti != null && ti.getOutputData() != null) {
                commandOutput.putAll(ti.getOutputData());
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> parentObj = (Map<String, Object>)
                    state.context.computeIfAbsent("commandOutputs", k -> new HashMap<String, Object>());
            parentObj.put(node.getId(), commandOutput);

            applyOutputMapping(node, state.context, commandOutput);
            taskRecorder.recordTaskCompletion(ti, commandOutput, "COMPLETED");

            List<WorkflowEdgeDto> outEdges = state.edgesBySource.getOrDefault(node.getId(), List.of());
            if (!outEdges.isEmpty()) {
                return NodeExecutionResult.next(
                        state.nodeMap.get(outEdges.get(0).getTarget()), outEdges.get(0).getId());
            }
            return NodeExecutionResult.COMPLETED;
        }

        // First encounter â€” suspend and schedule async execution
        String eventType = "COMMAND_RESUME_" + node.getId();
        eventSubscriptionManager.createEventSubscription(
                state.instance, eventType, node.getId(), Map.of());

        step.setStatus("WAITING");
        step.setNotes("Suspended execution. Waiting on asynchronous command: " + commandType);
        trace.add(step);
        taskRecorder.recordTaskCompletion(ti, Map.of(), "WAITING");

        final WorkflowNodeDto finalNode         = node;
        final var finalInstance                  = state.instance;
        final String finalInstanceId             = state.instanceId;
        final String finalContextId              = state.contextId;
        final var finalVersion                   = state.version;
        final Map<String, Object> backgroundCtx  = new HashMap<>(state.context);

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    asyncDelegate.trigger(finalNode, finalInstance, backgroundCtx,
                            finalInstanceId, finalContextId, finalVersion, eventType);
                }
            });
        } else {
            asyncDelegate.trigger(finalNode, finalInstance, backgroundCtx,
                    finalInstanceId, finalContextId, finalVersion, eventType);
        }

        state.suspendedNodes.add(node);
        return NodeExecutionResult.suspended();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void applyOutputMapping(WorkflowNodeDto node,
                                     Map<String, Object> context,
                                     Map<String, Object> commandOutput) {
        Object outputMappingObj = node.getData() != null ? node.getData().get("outputMapping") : null;
        if (!(outputMappingObj instanceof Map)) return;
        @SuppressWarnings("unchecked")
        Map<?, ?> outputMap = (Map<?, ?>) outputMappingObj;
        for (Map.Entry<?, ?> entry : outputMap.entrySet()) {
            String outputKey       = String.valueOf(entry.getKey());
            String targetContextKey = String.valueOf(entry.getValue());
            Object val = commandOutput.get(outputKey);
            if (val != null) {
                String contextKey = targetContextKey.startsWith("context.")
                        ? targetContextKey.substring(8) : targetContextKey;
                context.put(contextKey, val);
            }
        }
    }

    private String extractString(Map<String, Object> data, String key) {
        if (data == null) return null;
        Object val = data.get(key);
        return val != null ? String.valueOf(val) : null;
    }
}
