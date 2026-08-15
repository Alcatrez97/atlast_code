package com.vi.atlas.workflow.entity;

import com.vi.atlas.workflow.entity.converter.GenericJsonConverter;
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

    @Column(name = "circle_id")
    private Integer circleId;

    @Column(name = "instance_id", length = 36)
    private String instanceId;

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

    @Column(name = "step_count")
    private Integer stepCount;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "trace_json", length = 20000)
    @Convert(converter = GenericJsonConverter.StepRecordListConverter.class)
    private List<com.vi.atlas.common.dto.StepRecordDto> trace;

    @Column(name = "total_duration_ms")
    private long totalDurationMs;

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
        return instanceId;
    }
    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getStatus() { return status != null ? status.name() : null; }
    public void setStatus(String status) { this.status = status != null ? WorkflowInstanceStatus.valueOf(status.toUpperCase()) : null; }

    public String getOutcomeNodeId() { return outcomeNodeId; }
    public void setOutcomeNodeId(String outcomeNodeId) { this.outcomeNodeId = outcomeNodeId; }

    public String getOutcomeNodeLabel() { return outcomeNodeLabel; }
    public void setOutcomeNodeLabel(String outcomeNodeLabel) { this.outcomeNodeLabel = outcomeNodeLabel; }

    public String getOutcomeBucketId() { return outcomeBucketId; }
    public void setOutcomeBucketId(String outcomeBucketId) { this.outcomeBucketId = outcomeBucketId; }

    public Integer getStepCount() { return stepCount; }
    public void setStepCount(Integer stepCount) { this.stepCount = stepCount; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public long getTotalDurationMs() { return totalDurationMs; }
    public void setTotalDurationMs(long totalDurationMs) { this.totalDurationMs = totalDurationMs; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Integer getCircleId() { return circleId; }
    public void setCircleId(Integer circleId) { this.circleId = circleId; }
}
