package com.enterprise.atlas.workflow.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Reusable event definition in the registry.
 */
@Entity
@Table(name = "event_definitions", indexes = {
    @Index(name = "idx_event_def_key", columnList = "event_key", unique = true),
    @Index(name = "idx_event_def_active", columnList = "active")
})
public class EventDefinition {

    @Id
    @Column(name = "event_definition_pk", length = 36)
    private String id;

    @Column(name = "event_key", nullable = false, length = 100)
    private String eventKey; // e.g. PAYMENT_RECEIVED, OTP_VERIFIED

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "kafka_topic", length = 250)
    private String kafkaTopic; // broker topic configuration

    @Column(name = "correlation_key_path", length = 250)
    private String correlationKeyPath; // path in payload to extract businessKey, e.g. "payload.cafId"

    @Lob
    @Column(name = "payload_schema", length = 4000)
    private String payloadSchema; // standard JSON structure template/schema

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public EventDefinition() {}

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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEventKey() {
        return eventKey;
    }

    public void setEventKey(String eventKey) {
        this.eventKey = eventKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getKafkaTopic() {
        return kafkaTopic;
    }

    public void setKafkaTopic(String kafkaTopic) {
        this.kafkaTopic = kafkaTopic;
    }

    public String getCorrelationKeyPath() {
        return correlationKeyPath;
    }

    public void setCorrelationKeyPath(String correlationKeyPath) {
        this.correlationKeyPath = correlationKeyPath;
    }

    public String getPayloadSchema() {
        return payloadSchema;
    }

    public void setPayloadSchema(String payloadSchema) {
        this.payloadSchema = payloadSchema;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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
