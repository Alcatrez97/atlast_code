package com.enterprise.atlas.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Schema(description = "Data Transfer Object representing a high-level workflow definition metadata template")
public class WorkflowDefinitionDto implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "Unique database identifier (UUID)", readOnly = true, example = "w1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
    private String id;

    @Schema(description = "Human-readable name of the workflow", requiredMode = Schema.RequiredMode.REQUIRED, example = "Order Processing Flow")
    private String name;

    @Schema(description = "Unique business identifier key used in route URLs and triggers", requiredMode = Schema.RequiredMode.REQUIRED, example = "ORDER_VALIDATION")
    private String key; // Unique business key, e.g. "OrderValidationWorkflow"

    @Schema(description = "Detailed purpose of the workflow", example = "Evaluates transaction rules, checks user status, and routes to approvals or fulfillment")
    private String description;

    @Schema(description = "Timestamp when the definition was created", readOnly = true)
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp when the definition was updated", readOnly = true)
    private LocalDateTime updatedAt;

    @Schema(description = "Timeline history of draft and published versions for this workflow definition")
    private List<WorkflowVersionDto> versions = new ArrayList<>();

    @Schema(description = "Version number code of the currently published definition, if any", example = "1")
    private Integer activeVersion; // version number of the currently published definition, if any

    public WorkflowDefinitionDto() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<WorkflowVersionDto> getVersions() {
        return versions;
    }

    public void setVersions(List<WorkflowVersionDto> versions) {
        if (versions != null) {
            this.versions = versions;
        }
    }

    public Integer getActiveVersion() {
        return activeVersion;
    }

    public void setActiveVersion(Integer activeVersion) {
        this.activeVersion = activeVersion;
    }
}
