package com.enterprise.atlas.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "Data Transfer Object representing a revert status audit log entry for a bucket task")
public class RevertStatusDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String workflowInstanceId;
    private String formId;
    private String bucketId;
    private String bucketName;
    private String status; // PENDING, COMPLETED, REVERTED
    private String previousStepId;
    private String dependencyBucketIds; // JSON list of dependency bucket keys
    private String resolvedBy;
    private String resolutionNotes;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public RevertStatusDto() {}

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
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
