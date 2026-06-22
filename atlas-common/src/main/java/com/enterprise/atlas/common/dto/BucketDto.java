package com.enterprise.atlas.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "Data Transfer Object representing a business outcome bucket (queue)")
public class BucketDto implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "Unique database identifier (UUID)", readOnly = true, example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
    private String id;

    @Schema(description = "Business lookup key for outcome bucket", requiredMode = Schema.RequiredMode.REQUIRED, example = "approval_queue")
    private String bucketId;      // business key used in node data, e.g. BCK_FRAUD

    @Schema(description = "Human-readable name of the bucket", requiredMode = Schema.RequiredMode.REQUIRED, example = "Manual Approval Queue")
    private String name;          // human-readable name, e.g. "Fraud Detection Queue"

    @Schema(description = "Detailed purpose of the bucket", example = "Bucket queue for manual approvals")
    private String description;

    @Schema(description = "Category categorization for routing rules", example = "Approvals")
    private String category;      // e.g. FRAUD, COMPLIANCE, SERVICING, ESCALATION

    @Schema(description = "Priority level of execution tasks in this bucket", allowableValues = {"CRITICAL", "HIGH", "MEDIUM", "LOW"}, example = "HIGH")
    private String priority;      // CRITICAL, HIGH, MEDIUM, LOW

    @Schema(description = "SLA resolution deadline target duration (hours)", example = "24")
    private Integer slaHours;     // expected resolution time in hours

    @Schema(description = "Operations team/group responsible for resolving items", example = "Risk Operations")
    private String ownerGroup;    // team responsible, e.g. "Risk Operations"

    @Schema(description = "Comma-separated automated callback action codes", example = "SEND_EMAIL,TRIGGER_WEBHOOK")
    private String autoActions;   // comma-separated action tags: SEND_EMAIL,TRIGGER_WEBHOOK

    @Schema(description = "Flag specifying if the bucket is active", example = "true")
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public BucketDto() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getBucketId() { return bucketId; }
    public void setBucketId(String bucketId) { this.bucketId = bucketId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public Integer getSlaHours() { return slaHours; }
    public void setSlaHours(Integer slaHours) { this.slaHours = slaHours; }

    public String getOwnerGroup() { return ownerGroup; }
    public void setOwnerGroup(String ownerGroup) { this.ownerGroup = ownerGroup; }

    public String getAutoActions() { return autoActions; }
    public void setAutoActions(String autoActions) { this.autoActions = autoActions; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
