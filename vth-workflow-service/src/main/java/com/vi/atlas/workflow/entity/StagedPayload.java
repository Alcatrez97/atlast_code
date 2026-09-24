package com.vi.atlas.workflow.entity;

import com.vi.atlas.workflow.entity.converter.GenericJsonConverter;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "postpaid_workflow_staged_payloads", indexes = {
    @Index(name = "idx_staged_biz_key", columnList = "business_key"),
    @Index(name = "idx_staged_type", columnList = "payload_type")
})
public class StagedPayload {

    @Id
    @Column(name = "staged_payload_pk", length = 36)
    private String id;

    @Column(name = "business_key", nullable = false, length = 100)
    private String businessKey;

    @Column(name = "payload_type", nullable = false, length = 50)
    private String payloadType; // DOCUMENTS, CAF, FAMILY_GROUP

    @Convert(converter = GenericJsonConverter.MapConverter.class)
    @Column(name = "payload", columnDefinition = "CLOB")
    private Map<String, Object> payload;

    @Column(name = "status", length = 30)
    private String status; // STAGED, CONSUMED

    @Column(name = "circle_id")
    private Integer circleId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public StagedPayload() {
        this.id = UUID.randomUUID().toString();
        this.status = "STAGED";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public StagedPayload(String businessKey, String payloadType, Map<String, Object> payload, Integer circleId) {
        this();
        this.businessKey = businessKey;
        this.payloadType = payloadType;
        this.payload = payload;
        this.circleId = circleId;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    public String getPayloadType() {
        return payloadType;
    }

    public void setPayloadType(String payloadType) {
        this.payloadType = payloadType;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getCircleId() {
        return circleId;
    }

    public void setCircleId(Integer circleId) {
        this.circleId = circleId;
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
}
