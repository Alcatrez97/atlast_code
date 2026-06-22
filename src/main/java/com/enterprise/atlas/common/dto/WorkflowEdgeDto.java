package com.enterprise.atlas.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Schema(description = "Data Transfer Object representing a connection edge transition between nodes inside a workflow definition canvas graph")
public class WorkflowEdgeDto implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "Unique edge identifier tag", requiredMode = Schema.RequiredMode.REQUIRED, example = "edge_check_vip_to_high_value")
    private String id;

    @Schema(description = "Source node identifier", requiredMode = Schema.RequiredMode.REQUIRED, example = "node_rule_check_vip")
    private String source;

    @Schema(description = "Target node identifier", requiredMode = Schema.RequiredMode.REQUIRED, example = "node_bucket_high_value_review")
    private String target;

    @Schema(description = "Human-readable transition action label", example = "Yes")
    private String label;

    @Schema(description = "Conditional transition expression logic or matching rules", example = "{\"condition\": \"true\"}")
    private Map<String, Object> data = new HashMap<>(); // contains condition strings, transition rules, etc.

    public WorkflowEdgeDto() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
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
}
