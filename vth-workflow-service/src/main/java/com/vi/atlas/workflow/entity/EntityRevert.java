package com.vi.atlas.workflow.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Master entity revert audit trail record for human review buckets (A2, AWCS, OBCC)
 * and non-bucket system reverts (NPG, Billing, Provisioning).
 */
@Entity
@Table(name = "postpaid_entity_revert", indexes = {
    @Index(name = "idx_ent_revert_bucket_picker", columnList = "circle_id, revert_category, bucket_id, revert_status, is_active"),
    @Index(name = "idx_ent_revert_system_picker", columnList = "circle_id, revert_category, ent_type_id, revert_status, is_active"),
    @Index(name = "idx_ent_revert_caf", columnList = "caf_id"),
    @Index(name = "idx_ent_revert_inst", columnList = "workflow_instance_id")
})
public class EntityRevert {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_ent_revert")
    @SequenceGenerator(name = "seq_ent_revert", sequenceName = "seq_postpaid_entity_revert", allocationSize = 1)
    @Column(name = "entity_revert_id")
    private Long id;

    @Column(name = "caf_id", nullable = false)
    private Long cafId;

    @Column(name = "workflow_instance_id", length = 36)
    private String workflowInstanceId;

    @Column(name = "revert_category", nullable = false, length = 30)
    private String revertCategory = "BUCKET"; // BUCKET, SYSTEM, NPG, BILLING

    @Column(name = "bucket_id", length = 100)
    private String bucketId; // A2, AWCS, OBCC

    @Column(name = "ent_type_id", nullable = false, length = 100)
    private String entTypeId; // WORKFLOW_ENGINE, AGENT, SUPERVISOR, NPG_SYSTEM

    @Column(name = "revert_status", nullable = false, length = 30)
    private String revertStatus = "PENDING"; // PENDING, COMPLETED, REVERTED, PARKED, FAILED

    @Column(name = "ent_person_id", length = 100)
    private String entPersonId;

    @Column(name = "ent_org_id", length = 100)
    private String entOrgId;

    @Column(name = "revert_person_id", length = 100)
    private String revertPersonId;

    @Column(name = "revert_org_id", length = 100)
    private String revertOrgId;

    @Column(name = "revert_seq_number", nullable = false)
    private Integer revertSeqNumber = 1;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "circle_id", nullable = false)
    private Integer circleId;

    @OneToMany(mappedBy = "entityRevert", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EntityRevertMeta> metadataList = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public EntityRevert() {}

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void addMetadata(String key, String value) {
        EntityRevertMeta meta = new EntityRevertMeta();
        meta.setEntityRevert(this);
        meta.setMetaKey(key);
        meta.setMetaValue(value);
        this.metadataList.add(meta);
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCafId() { return cafId; }
    public void setCafId(Long cafId) { this.cafId = cafId; }

    public String getWorkflowInstanceId() { return workflowInstanceId; }
    public void setWorkflowInstanceId(String workflowInstanceId) { this.workflowInstanceId = workflowInstanceId; }

    public String getRevertCategory() { return revertCategory; }
    public void setRevertCategory(String revertCategory) { this.revertCategory = revertCategory; }

    public String getBucketId() { return bucketId; }
    public void setBucketId(String bucketId) { this.bucketId = bucketId; }

    public String getEntTypeId() { return entTypeId; }
    public void setEntTypeId(String entTypeId) { this.entTypeId = entTypeId; }

    public String getRevertStatus() { return revertStatus; }
    public void setRevertStatus(String revertStatus) { this.revertStatus = revertStatus; }

    public String getEntPersonId() { return entPersonId; }
    public void setEntPersonId(String entPersonId) { this.entPersonId = entPersonId; }

    public String getEntOrgId() { return entOrgId; }
    public void setEntOrgId(String entOrgId) { this.entOrgId = entOrgId; }

    public String getRevertPersonId() { return revertPersonId; }
    public void setRevertPersonId(String revertPersonId) { this.revertPersonId = revertPersonId; }

    public String getRevertOrgId() { return revertOrgId; }
    public void setRevertOrgId(String revertOrgId) { this.revertOrgId = revertOrgId; }

    public Integer getRevertSeqNumber() { return revertSeqNumber; }
    public void setRevertSeqNumber(Integer revertSeqNumber) { this.revertSeqNumber = revertSeqNumber; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Integer getCircleId() { return circleId; }
    public void setCircleId(Integer circleId) { this.circleId = circleId; }

    public List<EntityRevertMeta> getMetadataList() { return metadataList; }
    public void setMetadataList(List<EntityRevertMeta> metadataList) { this.metadataList = metadataList; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
