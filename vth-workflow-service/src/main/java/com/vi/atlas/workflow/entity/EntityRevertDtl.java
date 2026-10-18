package com.vi.atlas.workflow.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Child key-value detail record associated with an EntityRevert entry.
 */
@Entity
@Table(name = "postpaid_entity_revert_dtl")
public class EntityRevertDtl {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_ent_revert_dtl")
    @SequenceGenerator(name = "seq_ent_revert_dtl", sequenceName = "seq_postpaid_entity_revert_dtl", allocationSize = 1)
    @Column(name = "revert_dtl_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_revert_id", nullable = false)
    private EntityRevert entityRevert;

    @Column(name = "key", nullable = false, length = 100)
    private String key;

    @Column(name = "value", length = 50)
    private String value;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public EntityRevertDtl() {}

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public EntityRevert getEntityRevert() { return entityRevert; }
    public void setEntityRevert(EntityRevert entityRevert) { this.entityRevert = entityRevert; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getValue() { return value; }
    public void setValue(String value) {
        if (value != null && value.length() > 50) {
            this.value = value.substring(0, 50);
        } else {
            this.value = value;
        }
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
