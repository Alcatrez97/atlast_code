package com.vi.atlas.workflow.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Child key-value metadata record associated with an EntityRevert entry.
 */
@Entity
@Table(name = "postpaid_entity_revert_meta")
public class EntityRevertMeta {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_ent_revert_meta")
    @SequenceGenerator(name = "seq_ent_revert_meta", sequenceName = "seq_postpaid_entity_revert_meta", allocationSize = 1)
    @Column(name = "meta_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_revert_id", nullable = false)
    private EntityRevert entityRevert;

    @Column(name = "meta_key", nullable = false, length = 100)
    private String metaKey;

    @Column(name = "meta_value", length = 50)
    private String metaValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public EntityRevertMeta() {}

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public EntityRevert getEntityRevert() { return entityRevert; }
    public void setEntityRevert(EntityRevert entityRevert) { this.entityRevert = entityRevert; }

    public String getMetaKey() { return metaKey; }
    public void setMetaKey(String metaKey) { this.metaKey = metaKey; }

    public String getMetaValue() { return metaValue; }
    public void setMetaValue(String metaValue) {
        if (metaValue != null && metaValue.length() > 50) {
            this.metaValue = metaValue.substring(0, 50);
        } else {
            this.metaValue = metaValue;
        }
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
