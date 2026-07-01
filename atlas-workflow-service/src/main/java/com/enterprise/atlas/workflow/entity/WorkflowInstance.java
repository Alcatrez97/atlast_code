package com.enterprise.atlas.workflow.entity;

import com.enterprise.atlas.workflow.entity.converter.GenericJsonConverter;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Tracks the execution state and lifecycle of a running workflow instance.
 */
@Entity
@Table(name = "workflow_instances", indexes = {
    @Index(name = "idx_inst_wf_key", columnList = "workflow_key"),
    @Index(name = "idx_inst_status", columnList = "status"),
    @Index(name = "idx_inst_created_at", columnList = "created_at")
})
public class WorkflowInstance {

    @Id
    @Column(name = "workflow_instance_pk", length = 36)
    private String id;

    @Column(name = "workflow_key", nullable = false, length = 100)
    private String workflowKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", nullable = false, foreignKey = @ForeignKey(name = "fk_instance_version"))
    private WorkflowVersion workflowVersion;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private WorkflowInstanceStatus status; // CREATED, RUNNING, WAITING, COMPLETED, FAILED, TERMINATED

    @Column(name = "current_node_id", length = 100)
    private String currentNodeId;

    @Column(name = "business_key", length = 100)
    private String businessKey;

    @Lob
    @Column(name = "serialized_context", columnDefinition = "CLOB")
    @Convert(converter = GenericJsonConverter.MapConverter.class)
    private Map<String, Object> serializedContext;

    @Lob
    @Column(name = "runtime_graph", columnDefinition = "CLOB")
    @Convert(converter = GenericJsonConverter.MapConverter.class)
    private Map<String, Object> runtimeGraph;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public WorkflowInstance() {}

    // ---- Getters & Setters ----

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getWorkflowKey() { return workflowKey; }
    public void setWorkflowKey(String workflowKey) { this.workflowKey = workflowKey; }

    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }

    public String getVersionId() {
        return workflowVersion != null ? workflowVersion.getId() : null;
    }
    public void setVersionId(String versionId) {
        if (versionId == null) {
            this.workflowVersion = null;
        } else {
            this.workflowVersion = new WorkflowVersion();
            this.workflowVersion.setId(versionId);
        }
    }

    public Integer getVersionNumber() { return versionNumber; }
    public void setVersionNumber(Integer versionNumber) { this.versionNumber = versionNumber; }

    public String getStatus() { return status != null ? status.name() : null; }
    public void setStatus(String status) { this.status = status != null ? WorkflowInstanceStatus.valueOf(status.toUpperCase()) : null; }

    public String getCurrentNodeId() { return currentNodeId; }
    public void setCurrentNodeId(String currentNodeId) { this.currentNodeId = currentNodeId; }

    public Map<String, Object> getSerializedContext() { return serializedContext; }
    public void setSerializedContext(Map<String, Object> serializedContext) { this.serializedContext = serializedContext; }

    public Map<String, Object> getRuntimeGraph() { return runtimeGraph; }
    public void setRuntimeGraph(Map<String, Object> runtimeGraph) { this.runtimeGraph = runtimeGraph; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
