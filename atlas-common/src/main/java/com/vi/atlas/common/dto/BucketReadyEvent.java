package com.vi.atlas.common.dto;

import java.io.Serializable;
import java.util.List;

public class BucketReadyEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String instanceId;
    private String contextId;
    private String bucketId;
    private String bucketName;
    private String priority;
    private Integer slaHours;
    private Integer circleId;
    private List<String> dependencyBucketIds;

    public BucketReadyEvent() {}

    public BucketReadyEvent(String instanceId, String contextId, String bucketId, String bucketName, String priority, Integer slaHours, List<String> dependencyBucketIds, Integer circleId) {
        this.instanceId = instanceId;
        this.contextId = contextId;
        this.bucketId = bucketId;
        this.bucketName = bucketName;
        this.priority = priority;
        this.slaHours = slaHours;
        this.dependencyBucketIds = dependencyBucketIds;
        this.circleId = circleId;
    }

    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }

    public String getContextId() { return contextId; }
    public void setContextId(String contextId) { this.contextId = contextId; }

    public String getBucketId() { return bucketId; }
    public void setBucketId(String bucketId) { this.bucketId = bucketId; }

    public String getBucketName() { return bucketName; }
    public void setBucketName(String bucketName) { this.bucketName = bucketName; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public Integer getSlaHours() { return slaHours; }
    public void setSlaHours(Integer slaHours) { this.slaHours = slaHours; }

    public List<String> getDependencyBucketIds() { return dependencyBucketIds; }
    public void setDependencyBucketIds(List<String> dependencyBucketIds) { this.dependencyBucketIds = dependencyBucketIds; }

    public Integer getCircleId() { return circleId; }
    public void setCircleId(Integer circleId) { this.circleId = circleId; }
}
