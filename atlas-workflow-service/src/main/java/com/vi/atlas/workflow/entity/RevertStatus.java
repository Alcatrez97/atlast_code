package com.vi.atlas.workflow.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "revert_status", indexes = {
    @Index(name = "idx_revert_inst_id", columnList = "workflow_instance_id"),
    @Index(name = "idx_revert_form_id", columnList = "form_id")
})
public class RevertStatus {

    @Id
    @Column(name = "revert_status_pk", length = 36)
    private String id;

    @Column(name = "workflow_instance_id", nullable = false, length = 36)
    private String workflowInstanceId;

    @Column(name = "form_id", nullable = false, length = 100)
    private String formId;

    @Column(name = "bucket_id", length = 100, nullable = false)
    private String bucketId;

    @Column(name = "bucket_name", length = 255)
    private String bucketName;

    @Column(name = "status", length = 30, nullable = false)
    @Enumerated(EnumType.STRING)
    private RevertStepStatus status; // PENDING, COMPLETED, REVERTED

    @Column(name = "previous_step_id", length = 36)
    private String previousStepId;

    @Column(name = "dependency_bucket_ids", length = 1000)
    private String dependencyBucketIds; // JSON list of dependency bucket keys

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    @Column(name = "resolution_notes", length = 2000)
    private String resolutionNotes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "circle_id")
    private Integer circleId;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public RevertStatus() {}

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWorkflowInstanceId() {
        return workflowInstanceId;
    }

    public void setWorkflowInstanceId(String workflowInstanceId) {
        this.workflowInstanceId = workflowInstanceId;
    }

    public String getFormId() {
        return formId;
    }

    public void setFormId(String formId) {
        this.formId = formId;
    }

    public String getBucketId() {
        return bucketId;
    }

    public void setBucketId(String bucketId) {
        this.bucketId = bucketId;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getStatus() {
        return status != null ? status.name() : null;
    }

    public void setStatus(String status) {
        this.status = status != null ? RevertStepStatus.valueOf(status.toUpperCase()) : null;
    }

    public String getPreviousStepId() {
        return previousStepId;
    }

    public void setPreviousStepId(String previousStepId) {
        this.previousStepId = previousStepId;
    }

    public String getDependencyBucketIds() {
        return dependencyBucketIds;
    }

    public void setDependencyBucketIds(String dependencyBucketIds) {
        this.dependencyBucketIds = dependencyBucketIds;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(String resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }

    public void setResolutionNotes(String resolutionNotes) {
        this.resolutionNotes = resolutionNotes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public Integer getCircleId() {
        return circleId;
    }

    public void setCircleId(Integer circleId) {
        this.circleId = circleId;
    }
}
