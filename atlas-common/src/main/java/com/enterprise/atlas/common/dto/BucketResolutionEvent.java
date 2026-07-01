package com.enterprise.atlas.common.dto;

import java.io.Serializable;

public class BucketResolutionEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String instanceId;
    private String bucketId;
    private String outcome; // Accept, Reject, Park
    private String resolvedBy;
    private String resolutionNotes;

    public BucketResolutionEvent() {}

    public BucketResolutionEvent(String instanceId, String bucketId, String outcome, String resolvedBy, String resolutionNotes) {
        this.instanceId = instanceId;
        this.bucketId = bucketId;
        this.outcome = outcome;
        this.resolvedBy = resolvedBy;
        this.resolutionNotes = resolutionNotes;
    }

    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }

    public String getBucketId() { return bucketId; }
    public void setBucketId(String bucketId) { this.bucketId = bucketId; }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }

    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }

    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }
}
