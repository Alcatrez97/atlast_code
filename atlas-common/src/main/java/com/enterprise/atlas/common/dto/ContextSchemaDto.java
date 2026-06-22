package com.enterprise.atlas.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Schema(description = "Data Transfer Object representing a workflow context schema defining required and auto-resolving parameters")
public class ContextSchemaDto implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "Unique database identifier (UUID)", readOnly = true, example = "e5f6a7b8-c9d0-1f2a-3b4c-5d6e7f8a9b0c")
    private String id;

    @Schema(description = "Workflow key pattern this context schema is mapped to", requiredMode = Schema.RequiredMode.REQUIRED, example = "ORDER_VALIDATION")
    private String workflowKey;   // matches WorkflowDefinition.key

    @Schema(description = "Human-readable schema name", example = "Order Validation Context Schema")
    private String name;

    @Schema(description = "Detailed purpose of this schema", example = "Describes transaction, customer status, and account balance resolution rules")
    private String description;

    @Schema(description = "Timestamp when the schema was created", readOnly = true)
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp when the schema was updated", readOnly = true)
    private LocalDateTime updatedAt;

    @Schema(description = "Ordered list of fields that make up this context schema description")
    private List<ContextFieldDto> fields = new ArrayList<>();

    public ContextSchemaDto() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getWorkflowKey() { return workflowKey; }
    public void setWorkflowKey(String workflowKey) { this.workflowKey = workflowKey; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<ContextFieldDto> getFields() { return fields; }
    public void setFields(List<ContextFieldDto> fields) {
        if (fields != null) this.fields = fields;
    }
}
