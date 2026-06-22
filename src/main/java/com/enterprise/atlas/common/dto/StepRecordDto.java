package com.enterprise.atlas.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "Data Transfer Object representing a single step segment in a workflow execution trace log")
public class StepRecordDto implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "Index sequence number of the step in the trace timeline", example = "3")
    private int stepIndex;

    @Schema(description = "Identifier tag of the graph node visited", example = "node_rule_check_balance")
    private String nodeId;

    @Schema(description = "Type classification of the visited node", allowableValues = {"START", "RULE", "DECISION", "BUCKET", "TIMER", "PARALLEL", "JOIN", "END"}, example = "RULE")
    private String nodeType;   // START, RULE, DECISION, BUCKET, TIMER, PARALLEL, JOIN, END

    @Schema(description = "Human-readable label of the node", example = "Check Balance Rule")
    private String label;

    @Schema(description = "Evaluation/Traversal status code of the node visit", allowableValues = {"ENTERED", "EVALUATED", "ROUTED", "COMPLETED", "FAILED", "SKIPPED"}, example = "EVALUATED")
    private String status;     // ENTERED, EVALUATED, ROUTED, COMPLETED, FAILED, SKIPPED

    @Schema(description = "Expression executed on the node if applicable", example = "context['balance'] > 1000")
    private String expression; // SpEL expression evaluated (nullable)

    @Schema(description = "Boolean or string outcome value of SpEL evaluation", example = "true")
    private Object expressionResult; // result value of SpEL evaluation (nullable)

    @Schema(description = "Identifier of the outgoing graph edge selected from this node", example = "edge_yes")
    private String edgeTaken;  // ID of the edge selected from this node (nullable)

    @Schema(description = "Action explanation description for trace review logs", example = "Rule evaluated to true. Advancing along 'yes' edge.")
    private String notes;      // human-readable explanation of what happened

    @Schema(description = "Timestamp when the node was entered", readOnly = true)
    private LocalDateTime enteredAt;

    @Schema(description = "Timestamp when the node was exited", readOnly = true)
    private LocalDateTime exitedAt;

    @Schema(description = "Total segment execution time duration in milliseconds", example = "5")
    private long durationMs;

    public StepRecordDto() {}

    public int getStepIndex() { return stepIndex; }
    public void setStepIndex(int stepIndex) { this.stepIndex = stepIndex; }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }

    public Object getExpressionResult() { return expressionResult; }
    public void setExpressionResult(Object expressionResult) { this.expressionResult = expressionResult; }

    public String getEdgeTaken() { return edgeTaken; }
    public void setEdgeTaken(String edgeTaken) { this.edgeTaken = edgeTaken; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getEnteredAt() { return enteredAt; }
    public void setEnteredAt(LocalDateTime enteredAt) { this.enteredAt = enteredAt; }

    public LocalDateTime getExitedAt() { return exitedAt; }
    public void setExitedAt(LocalDateTime exitedAt) { this.exitedAt = exitedAt; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
}
