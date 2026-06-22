package com.enterprise.atlas.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "Data Transfer Object representing a specific version snapshot of a workflow definition")
public class WorkflowVersionDto implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "Unique database identifier (UUID)", readOnly = true, example = "v1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
    private String id;

    @Schema(description = "Associated parent workflow definition metadata identifier (UUID)", example = "w1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
    private String workflowDefinitionId;

    @Schema(description = "Incremental version code number", example = "1")
    private Integer version;

    @Schema(description = "Lifecycle publishing status of the version draft", allowableValues = {"DRAFT", "REVIEW", "APPROVED", "PUBLISHED"}, example = "DRAFT")
    private String status; // DRAFT, REVIEW, APPROVED, PUBLISHED

    @Schema(description = "The structured canvas definition graph containing nodes, edges, and visual metadata")
    private WorkflowGraphDto definition;

    @Schema(description = "Timestamp when the version was created", readOnly = true)
    private LocalDateTime createdAt;

    @Schema(description = "Username/Identifier of the creator", example = "developer_user")
    private String createdBy;

    @Schema(description = "Timestamp when the version was updated", readOnly = true)
    private LocalDateTime updatedAt;

    @Schema(description = "Username/Identifier of the last updater", example = "developer_user")
    private String updatedBy;

    public WorkflowVersionDto() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWorkflowDefinitionId() {
        return workflowDefinitionId;
    }

    public void setWorkflowDefinitionId(String workflowDefinitionId) {
        this.workflowDefinitionId = workflowDefinitionId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public WorkflowGraphDto getDefinition() {
        return definition;
    }

    public void setDefinition(WorkflowGraphDto definition) {
        this.definition = definition;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
