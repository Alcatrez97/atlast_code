package com.enterprise.atlas.workflow.entity;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_instance_id", nullable = false, foreignKey = @ForeignKey(name = "fk_revert_instance"))
    private WorkflowInstance workflowInstance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_id", nullable = false, foreignKey = @ForeignKey(name = "fk_revert_form"))
    private CustomerForm customerForm;

    @Column(name = "bucket_id", length = 100, nullable = false)
    private String bucketId;

    @Column(name = "bucket_name", length = 255)
    private String bucketName;

    @Column(name = "status", length = 30, nullable = false)
    @Enumerated(EnumType.STRING)
    private RevertStepStatus status; // PENDING, COMPLETED, REVERTED

    @Column(name = "previous_step_id", length = 36)
    private String previousStepId;

    @Lob
    @Column(name = "dependency_bucket_ids", columnDefinition = "CLOB")
    private String dependencyBucketIds; // JSON list of dependency bucket keys

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    @Lob
    @Column(name = "resolution_notes", columnDefinition = "CLOB")
    private String resolutionNotes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

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
        return workflowInstance != null ? workflowInstance.getId() : null;
    }

    public void setWorkflowInstanceId(String workflowInstanceId) {
        if (workflowInstanceId == null) {
            this.workflowInstance = null;
        } else {
            this.workflowInstance = new WorkflowInstance();
            this.workflowInstance.setId(workflowInstanceId);
        }
    }

    public String getFormId() {
        return customerForm != null ? customerForm.getId() : null;
    }

    public void setFormId(String formId) {
        if (formId == null) {
            this.customerForm = null;
        } else {
            this.customerForm = new CustomerForm();
            this.customerForm.setId(formId);
        }
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
}
