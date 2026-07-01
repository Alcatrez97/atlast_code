package com.enterprise.atlas.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Schema(description = "Data Transfer Object representing a stateful running instance of a workflow definition")
public class WorkflowInstanceDto implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "Unique database identifier (UUID)", readOnly = true, example = "e1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
    private String id;

    @Schema(description = "Associated workflow definition business lookup key pattern", requiredMode = Schema.RequiredMode.REQUIRED, example = "ORDER_VALIDATION")
    private String workflowKey;

    @Schema(description = "Associated workflow version snapshot identifier (UUID)", example = "v1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
    private String versionId;

    @Schema(description = "Business lookup key (e.g. CAF_ID, MSISDN) associated with the running instance", example = "CAF123")
    private String businessKey;

    @Schema(description = "Incremental version code number", example = "1")
    private Integer versionNumber;

    @Schema(description = "Execution status of the running stateful instance", allowableValues = {"RUNNING", "WAITING", "COMPLETED", "FAILED", "TERMINATED"}, example = "WAITING")
    private String status; // RUNNING, WAITING, COMPLETED, FAILED, TERMINATED

    @Schema(description = "Current node identifier tag if paused or executing (e.g. BUCKET node)", example = "node_bucket_manual_review")
    private String currentNodeId;

    @Schema(description = "Human-readable label of the current node", example = "Manual Approval Queue")
    private String currentNodeLabel;

    @Schema(description = "Serialized context map representing variables captured so far during the run",
            example = "{\"amount\": 6500, \"vipCustomer\": true, \"age\": 26}")
    private Map<String, Object> context = new HashMap<>();

    @Schema(description = "Persisted runtime execution graph (subset of definition nodes/edges activated in this run)")
    private Map<String, Object> runtimeGraph = new HashMap<>();

    @Schema(description = "Timestamp when the stateful instance was created", readOnly = true)
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp when the stateful instance was updated", readOnly = true)
    private LocalDateTime updatedAt;

    public WorkflowInstanceDto() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getWorkflowKey() { return workflowKey; }
    public void setWorkflowKey(String workflowKey) { this.workflowKey = workflowKey; }

    public String getVersionId() { return versionId; }
    public void setVersionId(String versionId) { this.versionId = versionId; }

    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }

    public Integer getVersionNumber() { return versionNumber; }
    public void setVersionNumber(Integer versionNumber) { this.versionNumber = versionNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCurrentNodeId() { return currentNodeId; }
    public void setCurrentNodeId(String currentNodeId) { this.currentNodeId = currentNodeId; }

    public String getCurrentNodeLabel() { return currentNodeLabel; }
    public void setCurrentNodeLabel(String currentNodeLabel) { this.currentNodeLabel = currentNodeLabel; }

    public Map<String, Object> getContext() { return context; }
    public void setContext(Map<String, Object> context) { this.context = context; }

    public Map<String, Object> getRuntimeGraph() { return runtimeGraph; }
    public void setRuntimeGraph(Map<String, Object> runtimeGraph) { this.runtimeGraph = runtimeGraph; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
