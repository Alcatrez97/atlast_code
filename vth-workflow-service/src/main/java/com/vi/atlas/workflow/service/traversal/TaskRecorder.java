package com.vi.atlas.workflow.service.traversal;

import com.vi.atlas.common.dto.WorkflowNodeDto;
import com.vi.atlas.common.dto.StepRecordDto;
import com.vi.atlas.workflow.entity.TaskInstance;
import com.vi.atlas.workflow.entity.WorkflowInstance;
import com.vi.atlas.workflow.repository.TaskInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles creation and completion of {@link TaskInstance} records during traversal.
 *
 * <p>Also maintains the per-traversal in-memory status cache
 * ({@link TraversalContext#localTaskStatuses}) so that repeated calls to
 * {@link #getTaskStatus} within a single traversal pass do not hit the database.
 */
@Component
public class TaskRecorder {

    private static final Logger log = LoggerFactory.getLogger(TaskRecorder.class);

    @Autowired
    private TaskInstanceRepository taskInstanceRepository;

    // -----------------------------------------------------------------------
    // Task lifecycle
    // -----------------------------------------------------------------------

    /**
     * Creates a RUNNING {@link TaskInstance} for the given node and persists it.
     *
     * @param instance the workflow instance (may be {@code null} — no-op if so)
     * @param node     the node being executed
     * @param input    snapshot of the context map at the moment the task starts
     * @return the persisted {@code TaskInstance}, or {@code null} if instance is null
     */
    public TaskInstance recordTaskStart(WorkflowInstance instance,
                                        WorkflowNodeDto node,
                                        Map<String, Object> input) {
        if (instance == null) return null;

        TaskInstance ti = new TaskInstance();
        ti.setId(instance.getId() + "_" + node.getId() + "_" + System.currentTimeMillis());
        ti.setWorkflowInstance(instance);
        ti.setTaskType(node.getType());
        ti.setLabel(node.getLabel() != null ? node.getLabel() : node.getType());
        ti.setStatus("RUNNING");
        ti.setInputData(new HashMap<>(input));
        ti.setStartedAt(LocalDateTime.now());

        TraversalContext travCtx = TraversalContextHolder.get();
        if (travCtx != null && travCtx.localTaskStatuses != null) {
            travCtx.localTaskStatuses.put(node.getId(), "RUNNING");
        }

        return taskInstanceRepository.save(ti);
    }

    /**
     * Updates an existing {@link TaskInstance} to the given terminal status and
     * persists the output data.
     *
     * @param ti     the task instance to update (no-op if {@code null})
     * @param output map of outputs produced by the node
     * @param status terminal status string, e.g. {@code "COMPLETED"}, {@code "FAILED"}
     */
    public void recordTaskCompletion(TaskInstance ti,
                                     Map<String, Object> output,
                                     String status) {
        if (ti == null) return;
        ti.setStatus(status);
        ti.setOutputData(new HashMap<>(output));
        ti.setCompletedAt(LocalDateTime.now());

        // Sync the in-memory cache
        TraversalContext travCtx = TraversalContextHolder.get();
        if (travCtx != null && travCtx.localTaskStatuses != null) {
            String id = ti.getId();
            String prefix = travCtx.instanceId + "_";
            if (id.startsWith(prefix)) {
                String remaining = id.substring(prefix.length());
                int lastUnderscore = remaining.lastIndexOf('_');
                if (lastUnderscore > 0) {
                    String nodeId = remaining.substring(0, lastUnderscore);
                    travCtx.localTaskStatuses.put(nodeId, status);
                    if (travCtx.localTaskOutputs != null) {
                        travCtx.localTaskOutputs.put(nodeId, new HashMap<>(output));
                    }
                }
            }
        }

        taskInstanceRepository.save(ti);
    }

    // -----------------------------------------------------------------------
    // Status query
    // -----------------------------------------------------------------------

    /**
     * Returns the latest recorded status for a given node in a given instance,
     * consulting the in-memory cache first to avoid repeated DB round-trips.
     *
     * @return status string, or {@code null} if no task instance exists for the node
     */
    public String getTaskStatus(String instanceId, String nodeId) {
        TraversalContext travCtx = TraversalContextHolder.get();
        if (travCtx != null && travCtx.localTaskStatuses != null) {
            String cached = travCtx.localTaskStatuses.get(nodeId);
            if (cached != null) return cached;
        }

        if (instanceId == null) return null;
        List<TaskInstance> tasks = taskInstanceRepository.findByWorkflowInstanceIdOrderByStartedAtAsc(instanceId);
        TaskInstance latest = null;
        String expectedPrefix = instanceId + "_" + nodeId + "_";
        for (TaskInstance ti : tasks) {
            if (ti.getId().startsWith(expectedPrefix)) {
                latest = ti;
            }
        }
        return latest != null ? latest.getStatus() : null;
    }

    /**
     * Returns the latest recorded output data map for a given node in a given instance.
     */
    public Map<String, Object> getTaskOutput(String instanceId, String nodeId) {
        TraversalContext travCtx = TraversalContextHolder.get();
        if (travCtx != null && travCtx.localTaskOutputs != null) {
            Map<String, Object> cached = travCtx.localTaskOutputs.get(nodeId);
            if (cached != null) return cached;
        }

        if (instanceId == null) return Map.of();
        List<TaskInstance> tasks = taskInstanceRepository.findByWorkflowInstanceIdOrderByStartedAtAsc(instanceId);
        TaskInstance latest = null;
        String expectedPrefix = instanceId + "_" + nodeId + "_";
        for (TaskInstance ti : tasks) {
            if (ti.getId().startsWith(expectedPrefix)) {
                latest = ti;
            }
        }
        return (latest != null && latest.getOutputData() != null) ? latest.getOutputData() : Map.of();
    }

    // -----------------------------------------------------------------------
    // Pre-traversal cache warm-up
    // -----------------------------------------------------------------------

    /**
     * Loads all existing task-instance statuses for the given instance into the
     * in-memory cache inside the current {@link TraversalContext}.
     *
     * <p>Called once at the start of each traversal to avoid N+1 DB queries.
     */
    public Map<String, String> loadTaskStatuses(String instanceId) {
        Map<String, String> localTaskStatuses = new HashMap<>();
        TraversalContext travCtx = TraversalContextHolder.get();
        if (instanceId == null) return localTaskStatuses;

        List<TaskInstance> existingTasks =
                taskInstanceRepository.findByWorkflowInstanceIdOrderByStartedAtAsc(instanceId);
        for (TaskInstance ti : existingTasks) {
            String id = ti.getId();
            String prefix = instanceId + "_";
            if (id.startsWith(prefix)) {
                String remaining = id.substring(prefix.length());
                int lastUnderscore = remaining.lastIndexOf('_');
                if (lastUnderscore > 0) {
                    String nodeId = remaining.substring(0, lastUnderscore);
                    localTaskStatuses.put(nodeId, ti.getStatus());
                    if (travCtx != null && travCtx.localTaskOutputs != null && ti.getOutputData() != null) {
                        travCtx.localTaskOutputs.put(nodeId, ti.getOutputData());
                    }
                }
            }
        }
        return localTaskStatuses;
    }

    // -----------------------------------------------------------------------
    // Step builder
    // -----------------------------------------------------------------------

    /**
     * Convenience factory for {@link StepRecordDto} — avoids repeated boilerplate
     * inside node executors.
     */
    public StepRecordDto buildStep(int idx, WorkflowNodeDto node, String status,
                                    String expression, Object result,
                                    String edgeTaken, String notes) {
        StepRecordDto s = new StepRecordDto();
        s.setStepIndex(idx);
        s.setNodeId(node.getId());
        s.setNodeType(node.getType());
        s.setLabel(node.getLabel());
        s.setStatus(status);
        s.setExpression(expression);
        s.setExpressionResult(result);
        s.setEdgeTaken(edgeTaken);
        s.setNotes(notes);
        s.setEnteredAt(LocalDateTime.now());
        s.setExitedAt(LocalDateTime.now());
        return s;
    }
}
