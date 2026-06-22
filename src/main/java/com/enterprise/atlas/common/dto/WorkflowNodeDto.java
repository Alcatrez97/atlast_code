package com.enterprise.atlas.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Schema(description = "Data Transfer Object representing a node inside a workflow definition canvas graph")
public class WorkflowNodeDto implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "Unique node identifier key", requiredMode = Schema.RequiredMode.REQUIRED, example = "node_rule_check_vip")
    private String id;

    @Schema(description = "Workflow canvas node type", allowableValues = {"START", "DECISION", "RULE", "BUCKET", "PARALLEL", "JOIN", "TIMER", "END"}, requiredMode = Schema.RequiredMode.REQUIRED, example = "RULE")
    private String type; // START, DECISION, RULE, BUCKET, PARALLEL, JOIN, TIMER, END

    @Schema(description = "Human-readable label for the canvas node", example = "Check VIP Customer status")
    private String label;

    @Schema(description = "Workflow logic configuration properties depending on node type (e.g. ruleKey, bucketId)", example = "{\"ruleKey\": \"CHECK_VIP\"}")
    private Map<String, Object> data = new HashMap<>(); // holds node properties like bucketId, ruleId, etc.

    @Schema(description = "Visual positioning coordinates layout for XYFlow designer", example = "{\"x\": 100, \"y\": 150}")
    private Map<String, Object> position = new HashMap<>(); // {x: 100, y: 150} for XYFlow positioning

    public WorkflowNodeDto() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        if (data != null) {
            this.data = data;
        }
    }

    public Map<String, Object> getPosition() {
        return position;
    }

    public void setPosition(Map<String, Object> position) {
        if (position != null) {
            this.position = position;
        }
    }
}
