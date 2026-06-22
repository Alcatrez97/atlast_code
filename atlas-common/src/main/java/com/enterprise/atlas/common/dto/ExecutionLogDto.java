package com.enterprise.atlas.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Schema(description = "Data Transfer Object containing execution results, trace logs, and outcome metrics for a workflow run")
public class ExecutionLogDto implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "Unique database identifier for this execution run (UUID)", readOnly = true, example = "f5a6b7c8-d9e0-1f2a-3b4c-5d6e7f8a9b0c")
    private String id;

    @Schema(description = "Parent stateful workflow instance identifier (UUID) if this execution is tracked", example = "a6b7c8d9-e0f1-2a3b-4c5d-6e7f8a9b0c1d")
    private String instanceId;

    @Schema(description = "Workflow key pattern", requiredMode = Schema.RequiredMode.REQUIRED, example = "ORDER_VALIDATION")
    private String workflowKey;

    @Schema(description = "Workflow version identifier (UUID) executing", example = "b7c8d9e0-f1a2-3b4c-5d6e-7f8a9b0c1d2e")
    private String versionId;

    @Schema(description = "Version number code", example = "1")
    private Integer versionNumber;

    @Schema(description = "Client-supplied tracking context correlation ID", example = "client-correlation-12345")
    private String contextId;

    @Schema(description = "Overall status result of the execution run", allowableValues = {"COMPLETED", "FAILED", "SUSPENDED"}, example = "COMPLETED")
    private String status;  // COMPLETED, FAILED

    @Schema(description = "The terminal node ID reached at execution completion/suspension", example = "node_end_success")
    private String outcomeNodeId;   // The final node reached (typically END or BUCKET)

    @Schema(description = "Human-readable label of the final node", example = "Success")
    private String outcomeNodeLabel;

    @Schema(description = "The fully-resolved context parameter map used during traversal evaluation", 
            example = "{\"amount\": 6500, \"vipCustomer\": true, \"age\": 26}")
    private Map<String, Object> inputContext = new HashMap<>();

    @Schema(description = "Ordered timeline log sequence of node traversal steps")
    private List<StepRecordDto> executionTrace = new ArrayList<>();

    @Schema(description = "Timestamp when the execution started", readOnly = true)
    private LocalDateTime startedAt;

    @Schema(description = "Timestamp when the execution completed", readOnly = true)
    private LocalDateTime completedAt;

    @Schema(description = "Total runtime execution duration in milliseconds", example = "45")
    private long totalDurationMs;

    @Schema(description = "Detailed runtime exception message or validation failure text if status is FAILED", example = "SpEL evaluation error: division by zero")
    private String errorMessage; // populated only on FAILED executions

    public ExecutionLogDto() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }

    public String getWorkflowKey() { return workflowKey; }
    public void setWorkflowKey(String workflowKey) { this.workflowKey = workflowKey; }

    public String getVersionId() { return versionId; }
    public void setVersionId(String versionId) { this.versionId = versionId; }

    public Integer getVersionNumber() { return versionNumber; }
    public void setVersionNumber(Integer versionNumber) { this.versionNumber = versionNumber; }

    public String getContextId() { return contextId; }
    public void setContextId(String contextId) { this.contextId = contextId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getOutcomeNodeId() { return outcomeNodeId; }
    public void setOutcomeNodeId(String outcomeNodeId) { this.outcomeNodeId = outcomeNodeId; }

    public String getOutcomeNodeLabel() { return outcomeNodeLabel; }
    public void setOutcomeNodeLabel(String outcomeNodeLabel) { this.outcomeNodeLabel = outcomeNodeLabel; }

    public Map<String, Object> getInputContext() { return inputContext; }
    public void setInputContext(Map<String, Object> inputContext) { this.inputContext = inputContext; }

    public List<StepRecordDto> getExecutionTrace() { return executionTrace; }
    public void setExecutionTrace(List<StepRecordDto> executionTrace) { this.executionTrace = executionTrace; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public long getTotalDurationMs() { return totalDurationMs; }
    public void setTotalDurationMs(long totalDurationMs) { this.totalDurationMs = totalDurationMs; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
