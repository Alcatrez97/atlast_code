package com.enterprise.atlas.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;

@Schema(description = "Data Transfer Object summarizing workload KPIs and metrics for an outcome bucket")
public class BucketWorkloadDto implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "Business lookup key for outcome bucket", requiredMode = Schema.RequiredMode.REQUIRED, example = "BCK_FRAUD")
    private String bucketId;

    @Schema(description = "Human-readable name of the bucket", example = "Fraud Detection Queue")
    private String bucketName;

    @Schema(description = "Priority level of execution tasks in this bucket", allowableValues = {"CRITICAL", "HIGH", "MEDIUM", "LOW"}, example = "HIGH")
    private String priority;

    @Schema(description = "SLA resolution deadline target duration (hours)", example = "24")
    private Integer slaHours;

    @Schema(description = "Operations team/group responsible for resolving items", example = "Risk Operations")
    private String ownerGroup;

    @Schema(description = "Total number of tasks ever routed to this bucket", example = "45")
    private long totalRouted;

    @Schema(description = "Count of tasks currently pending review", example = "12")
    private long pending;

    @Schema(description = "Count of tasks currently in-review", example = "5")
    private long inReview;

    @Schema(description = "Count of tasks resolved", example = "28")
    private long resolved;

    @Schema(description = "Count of resolved or pending tasks that breached their SLA", example = "2")
    private long slaBreached;

    @Schema(description = "Average resolution time (hours) for resolved tasks", example = "4.2")
    private Double avgResolutionHours; // null if no resolved items yet

    public BucketWorkloadDto() {}

    public String getBucketId() { return bucketId; }
    public void setBucketId(String bucketId) { this.bucketId = bucketId; }

    public String getBucketName() { return bucketName; }
    public void setBucketName(String bucketName) { this.bucketName = bucketName; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public Integer getSlaHours() { return slaHours; }
    public void setSlaHours(Integer slaHours) { this.slaHours = slaHours; }

    public String getOwnerGroup() { return ownerGroup; }
    public void setOwnerGroup(String ownerGroup) { this.ownerGroup = ownerGroup; }

    public long getTotalRouted() { return totalRouted; }
    public void setTotalRouted(long totalRouted) { this.totalRouted = totalRouted; }

    public long getPending() { return pending; }
    public void setPending(long pending) { this.pending = pending; }

    public long getInReview() { return inReview; }
    public void setInReview(long inReview) { this.inReview = inReview; }

    public long getResolved() { return resolved; }
    public void setResolved(long resolved) { this.resolved = resolved; }

    public long getSlaBreached() { return slaBreached; }
    public void setSlaBreached(long slaBreached) { this.slaBreached = slaBreached; }

    public Double getAvgResolutionHours() { return avgResolutionHours; }
    public void setAvgResolutionHours(Double avgResolutionHours) { this.avgResolutionHours = avgResolutionHours; }
}
