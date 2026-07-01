package com.enterprise.atlas.workflow.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Business outcome bucket - a named endpoint in a workflow decision tree.
 * Referenced by BUCKET nodes via the bucketId business key.
 */
@Entity
@Table(name = "buckets", indexes = {
    @Index(name = "idx_bucket_id_key", columnList = "bucket_id", unique = true),
    @Index(name = "idx_bucket_priority", columnList = "priority"),
    @Index(name = "idx_bucket_active", columnList = "active")
})
public class Bucket {

    @Id
    @Column(name = "bucket_pk", length = 36)
    private String id;

    @Column(name = "bucket_id", nullable = false, length = 100)
    private String bucketId; // business key used in workflow node data

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "priority", nullable = false, length = 20)
    private String priority; // CRITICAL, HIGH, MEDIUM, LOW

    @Column(name = "sla_hours")
    private Integer slaHours;

    @Column(name = "owner_group", length = 200)
    private String ownerGroup;

    @Column(name = "auto_actions", length = 500)
    private String autoActions; // comma-separated: SEND_EMAIL,TRIGGER_WEBHOOK

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Bucket() {}

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ---- Getters & Setters ----

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getBucketId() { return bucketId; }
    public void setBucketId(String bucketId) { this.bucketId = bucketId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public Integer getSlaHours() { return slaHours; }
    public void setSlaHours(Integer slaHours) { this.slaHours = slaHours; }

    public String getOwnerGroup() { return ownerGroup; }
    public void setOwnerGroup(String ownerGroup) { this.ownerGroup = ownerGroup; }

    public String getAutoActions() { return autoActions; }
    public void setAutoActions(String autoActions) { this.autoActions = autoActions; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
