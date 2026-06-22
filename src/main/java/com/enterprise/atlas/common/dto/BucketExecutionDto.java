package com.enterprise.atlas.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "Data Transfer Object representing an execution task routed to a business bucket")
public class BucketExecutionDto implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "Unique database identifier (UUID)", readOnly = true, example = "b1c2d3e4-f5a6-7b8c-9d0e-1f2a3b4c5d6e")
    private String id;

    @Schema(description = "Associated execution log identifier (UUID)", example = "c2d3e4f5-a6b7-8c9d-0e1f-2a3b4c5d6e7f")
    private String executionLogId;

    @Schema(description = "Parent workflow instance identifier (UUID)", example = "d3e4f5a6-b7c8-9d0e-1f2a-3b4c5d6e7f8a")
    private String instanceId;

    @Schema(description = "Workflow key pattern", requiredMode = Schema.RequiredMode.REQUIRED, example = "ORDER_VALIDATION")
    private String workflowKey;

    @Schema(description = "Business lookup key for outcome bucket", requiredMode = Schema.RequiredMode.REQUIRED, example = "BCK_FRAUD")
    private String bucketId;      // business key (e.g. BCK_FRAUD_ESCALATION)

    @Schema(description = "Human-readable name of the bucket", example = "Fraud Detection Queue")
    private String bucketName;

    @Schema(description = "Status of the bucket task lifecycle", allowableValues = {"PENDING", "IN_REVIEW", "RESOLVED"}, example = "PENDING")
    private String status;        // PENDING, IN_REVIEW, RESOLVED

    @Schema(description = "Priority level of the bucket task", allowableValues = {"CRITICAL", "HIGH", "MEDIUM", "LOW"}, example = "HIGH")
    private String priority;      // CRITICAL, HIGH, MEDIUM, LOW

    @Schema(description = "SLA resolution deadline target duration (hours)", example = "24")
    private Integer slaHours;

    @Schema(description = "Flag specifying if the SLA has been breached", example = "false")
    private boolean slaBreached;

    @Schema(description = "Timestamp when the task was created", readOnly = true)
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp when the task was resolved", readOnly = true)
    private LocalDateTime resolvedAt;

    @Schema(description = "Username/Identifier who resolved the task", example = "admin_user")
    private String resolvedBy;

    @Schema(description = "Notes explaining the outcome resolution", example = "Approved after manual document verification")
    private String resolutionNotes;

    public BucketExecutionDto() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getExecutionLogId() { return executionLogId; }
    public void setExecutionLogId(String executionLogId) { this.executionLogId = executionLogId; }

    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }

    public String getWorkflowKey() { return workflowKey; }
    public void setWorkflowKey(String workflowKey) { this.workflowKey = workflowKey; }

    public String getBucketId() { return bucketId; }
    public void setBucketId(String bucketId) { this.bucketId = bucketId; }

    public String getBucketName() { return bucketName; }
    public void setBucketName(String bucketName) { this.bucketName = bucketName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public Integer getSlaHours() { return slaHours; }
    public void setSlaHours(Integer slaHours) { this.slaHours = slaHours; }

    public boolean isSlaBreached() { return slaBreached; }
    public void setSlaBreached(boolean slaBreached) { this.slaBreached = slaBreached; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }

    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }
}
