package com.enterprise.atlas.workflow.entity;

import com.enterprise.atlas.common.dto.WorkflowGraphDto;
import com.enterprise.atlas.workflow.entity.converter.WorkflowGraphConverter;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "workflow_versions", uniqueConstraints = {
    @UniqueConstraint(name = "uq_wf_version", columnNames = {"workflow_definition_id", "version"})
}, indexes = {
    @Index(name = "idx_wf_ver_status", columnList = "status")
})
public class WorkflowVersion {

    @Id
    @Column(name = "workflow_version_pk", length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_definition_id", nullable = false)
    private WorkflowDefinition workflowDefinition;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // DRAFT, REVIEW, APPROVED, PUBLISHED

    @Lob
    @Column(name = "definition_json", nullable = false, columnDefinition = "CLOB")
    @Convert(converter = WorkflowGraphConverter.class)
    private WorkflowGraphDto definition;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    public WorkflowVersion() {}

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public WorkflowDefinition getWorkflowDefinition() {
        return workflowDefinition;
    }

    public void setWorkflowDefinition(WorkflowDefinition workflowDefinition) {
        this.workflowDefinition = workflowDefinition;
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
