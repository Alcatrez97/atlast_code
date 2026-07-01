package com.enterprise.atlas.workflow.entity;

import com.enterprise.atlas.workflow.entity.converter.GenericJsonConverter;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Entity representing an active wait-state subscription for an external event.
 */
@Entity
@Table(name = "event_subscriptions", indexes = {
    @Index(name = "idx_event_sub_bkey", columnList = "business_key"),
    @Index(name = "idx_event_sub_type", columnList = "event_type"),
    @Index(name = "idx_event_sub_status", columnList = "status"),
    @Index(name = "idx_event_sub_instance", columnList = "instance_id")
})
public class EventSubscription {

    @Id
    @Column(name = "event_subscription_pk", length = 36)
    private String id;

    @Column(name = "business_key", nullable = false, length = 100)
    private String businessKey;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "target_node_id", nullable = false, length = 100)
    private String targetNodeId;

    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE; // ACTIVE, TRIGGERED, CANCELLED

    @Lob
    @Column(name = "filter_attributes", columnDefinition = "CLOB")
    @Convert(converter = GenericJsonConverter.MapConverter.class)
    private Map<String, Object> filterAttributes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instance_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sub_workflow_instance"))
    private WorkflowInstance workflowInstance;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public EventSubscription() {}

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ---- Getters & Setters ----

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getTargetNodeId() { return targetNodeId; }
    public void setTargetNodeId(String targetNodeId) { this.targetNodeId = targetNodeId; }

    public String getStatus() { return status != null ? status.name() : null; }
    public void setStatus(String status) { this.status = status != null ? SubscriptionStatus.valueOf(status.toUpperCase()) : null; }

    public Map<String, Object> getFilterAttributes() { return filterAttributes; }
    public void setFilterAttributes(Map<String, Object> filterAttributes) { this.filterAttributes = filterAttributes; }

    public WorkflowInstance getWorkflowInstance() { return workflowInstance; }
    public void setWorkflowInstance(WorkflowInstance workflowInstance) { this.workflowInstance = workflowInstance; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
