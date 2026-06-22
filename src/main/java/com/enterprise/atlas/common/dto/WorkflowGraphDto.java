package com.enterprise.atlas.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Schema(description = "Data Transfer Object representing a structured workflow configuration definition graph containing nodes, edges, and visual viewport metadata")
public class WorkflowGraphDto implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "Ordered list of nodes representing steps, decisions, rules and buckets in the workflow")
    private List<WorkflowNodeDto> nodes = new ArrayList<>();

    @Schema(description = "Ordered list of directional edges linking execution paths between nodes")
    private List<WorkflowEdgeDto> edges = new ArrayList<>();

    @Schema(description = "Additional metadata configuration dictionary (e.g. pan position, zoom values for designer)", example = "{\"zoom\": 1.2, \"panX\": 50, \"panY\": 100}")
    private Map<String, Object> metadata = new HashMap<>();

    public WorkflowGraphDto() {}

    public List<WorkflowNodeDto> getNodes() {
        return nodes;
    }

    public void setNodes(List<WorkflowNodeDto> nodes) {
        if (nodes != null) {
            this.nodes = nodes;
        }
    }

    public List<WorkflowEdgeDto> getEdges() {
        return edges;
    }

    public void setEdges(List<WorkflowEdgeDto> edges) {
        if (edges != null) {
            this.edges = edges;
        }
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        if (metadata != null) {
            this.metadata = metadata;
        }
    }
}
