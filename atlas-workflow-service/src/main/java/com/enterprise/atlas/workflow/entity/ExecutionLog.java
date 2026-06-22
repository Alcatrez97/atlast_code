package com.enterprise.atlas.workflow.entity;

import com.enterprise.atlas.workflow.entity.converter.GenericJsonConverter;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Persists a single workflow execution run with its full step-by-step trace.
 */
@Entity
@Table(name = "execution_logs", indexes = {
    @Index(name = "idx_exec_wf_key", columnList = "workflow_key"),
    @Index(name = "idx_exec_status", columnList = "status"),
    @Index(name = "idx_exec_started_at", columnList = "started_at")
})
public class ExecutionLog {

    @Id
    @Column(name = "execution_log_pk", length = 36)
    private String id;

    @Column(name = "workflow_key", nullable = false, length = 100)
    private String workflowKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", foreignKey = @ForeignKey(name = "fk_exec_log_version"))
    private WorkflowVersion workflowVersion;

    @Column(name = "version_number")
    private Integer versionNumber;

    @Column(name = "context_id", length = 100)
    private String contextId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instance_id", foreignKey = @ForeignKey(name = "fk_exec_log_instance"))
    private WorkflowInstance workflowInstance;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private WorkflowInstanceStatus status; // COMPLETED, FAILED

    @Column(name = "outcome_node_id", length = 100)
    private String outcomeNodeId;

    @Column(name = "outcome_node_label", length = 255)
    private String outcomeNodeLabel;

    /** Business key of the bucket that was the final outcome (null if outcome was END or non-bucket node) */
    @Column(name = "outcome_bucket_id", length = 100)
    private String outcomeBucketId;

    @Lob
    @Column(name = "input_context_json", columnDefinition = "CLOB")
    @Convert(converter = GenericJsonConverter.MapConverter.class)
    private Map<String, Object> inputContext;

    @Lob
    @Column(name = "execution_trace_json", columnDefinition = "CLOB")
    @Convert(converter = GenericJsonConverter.ListConverter.class)
    private List<Map<String, Object>> executionTrace;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "total_duration_ms")
    private long totalDurationMs;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    public ExecutionLog() {}

    // ---- Getters & Setters ----

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getWorkflowKey() { return workflowKey; }
    public void setWorkflowKey(String workflowKey) { this.workflowKey = workflowKey; }

    public String getVersionId() {
        return workflowVersion != null ? workflowVersion.getId() : null;
    }
    public void setVersionId(String versionId) {
        if (versionId == null) {
            this.workflowVersion = null;
        } else {
            this.workflowVersion = new WorkflowVersion();
            this.workflowVersion.setId(versionId);
        }
    }

    public Integer getVersionNumber() { return versionNumber; }
    public void setVersionNumber(Integer versionNumber) { this.versionNumber = versionNumber; }

    public String getContextId() { return contextId; }
    public void setContextId(String contextId) { this.contextId = contextId; }

    public String getInstanceId() {
        return workflowInstance != null ? workflowInstance.getId() : null;
    }
    public void setInstanceId(String instanceId) {
        if (instanceId == null) {
            this.workflowInstance = null;
        } else {
            this.workflowInstance = new WorkflowInstance();
            this.workflowInstance.setId(instanceId);
        }
    }

    public String getStatus() { return status != null ? status.name() : null; }
    public void setStatus(String status) { this.status = status != null ? WorkflowInstanceStatus.valueOf(status.toUpperCase()) : null; }

    public String getOutcomeNodeId() { return outcomeNodeId; }
    public void setOutcomeNodeId(String outcomeNodeId) { this.outcomeNodeId = outcomeNodeId; }

    public String getOutcomeNodeLabel() { return outcomeNodeLabel; }
    public void setOutcomeNodeLabel(String outcomeNodeLabel) { this.outcomeNodeLabel = outcomeNodeLabel; }

    public String getOutcomeBucketId() { return outcomeBucketId; }
    public void setOutcomeBucketId(String outcomeBucketId) { this.outcomeBucketId = outcomeBucketId; }

    public Map<String, Object> getInputContext() { return inputContext; }
    public void setInputContext(Map<String, Object> inputContext) { this.inputContext = inputContext; }

    public List<Map<String, Object>> getExecutionTrace() { return executionTrace; }
    public void setExecutionTrace(List<Map<String, Object>> executionTrace) { this.executionTrace = executionTrace; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public long getTotalDurationMs() { return totalDurationMs; }
    public void setTotalDurationMs(long totalDurationMs) { this.totalDurationMs = totalDurationMs; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
