package com.enterprise.atlas.workflow.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Records a single workflow execution outcome that landed on a BUCKET node.
 * Tracks the operational lifecycle: PENDING → IN_REVIEW → RESOLVED.
 * SLA breach is computed based on slaHours + createdAt vs. now.
 */
@Entity
@Table(name = "bucket_executions", indexes = {
    @Index(name = "idx_bex_bucket_id", columnList = "bucket_id"),
    @Index(name = "idx_bex_status", columnList = "status"),
    @Index(name = "idx_bex_workflow_key", columnList = "workflow_key"),
    @Index(name = "idx_bex_exec_log_id", columnList = "execution_log_id"),
    @Index(name = "idx_bex_created_at", columnList = "created_at")
})
public class BucketExecution {

    @Id
    @Column(name = "bucket_execution_pk", length = 36)
    private String id;

    /** FK reference to the ExecutionLog that routed here */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_log_id", nullable = false, foreignKey = @ForeignKey(name = "fk_bex_exec_log"))
    private ExecutionLog executionLog;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instance_id", foreignKey = @ForeignKey(name = "fk_bex_instance"))
    private WorkflowInstance workflowInstance;

    @Column(name = "workflow_key", nullable = false, length = 100)
    private String workflowKey;

    /** Business key of the bucket (e.g. BCK_FRAUD_ESCALATION) */
    @Column(name = "bucket_id", nullable = false, length = 100)
    private String bucketId;

    @Column(name = "bucket_name", nullable = false, length = 200)
    private String bucketName;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private BucketTaskStatus status = BucketTaskStatus.PENDING; // PENDING, IN_REVIEW, RESOLVED

    @Column(name = "priority", length = 20)
    private String priority; // CRITICAL, HIGH, MEDIUM, LOW

    @Column(name = "sla_hours")
    private Integer slaHours;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by", length = 200)
    private String resolvedBy;

    @Column(name = "resolution_notes", length = 2000)
    private String resolutionNotes;

    public BucketExecution() {}

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ---- Computed helpers ----

    /**
     * Returns true if SLA time has elapsed and the item is still not resolved.
     */
    public boolean isSlaBreached() {
        if (slaHours == null || status == BucketTaskStatus.RESOLVED) return false;
        return createdAt != null && LocalDateTime.now().isAfter(createdAt.plusHours(slaHours));
    }

    // ---- Getters & Setters ----

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getExecutionLogId() {
        return executionLog != null ? executionLog.getId() : null;
    }
    public void setExecutionLogId(String executionLogId) {
        if (executionLogId == null) {
            this.executionLog = null;
        } else {
            this.executionLog = new ExecutionLog();
            this.executionLog.setId(executionLogId);
        }
    }

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

    public String getWorkflowKey() { return workflowKey; }
    public void setWorkflowKey(String workflowKey) { this.workflowKey = workflowKey; }

    public String getBucketId() { return bucketId; }
    public void setBucketId(String bucketId) { this.bucketId = bucketId; }

    public String getBucketName() { return bucketName; }
    public void setBucketName(String bucketName) { this.bucketName = bucketName; }

    public String getStatus() { return status != null ? status.name() : null; }
    public void setStatus(String status) { this.status = status != null ? BucketTaskStatus.valueOf(status.toUpperCase()) : null; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public Integer getSlaHours() { return slaHours; }
    public void setSlaHours(Integer slaHours) { this.slaHours = slaHours; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }

    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }
}
