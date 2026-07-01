package com.enterprise.atlas.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "Data Transfer Object representing a predefined event definition in the registry")
public class EventDefinitionDto implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "Unique database identifier (UUID)", readOnly = true, example = "63511c0b-18e3-4317-b518-bdcbba2ca172")
    private String id;

    @Schema(description = "Predefined event lookup key", requiredMode = Schema.RequiredMode.REQUIRED, example = "PAYMENT_RECEIVED")
    private String eventKey;

    @Schema(description = "Human-readable event name", example = "Payment Received Event")
    private String name;

    @Schema(description = "Detailed purpose of the event", example = "Triggers when a customer finishes a payment")
    private String description;

    @Schema(description = "Kafka Topic or Message Queue queue name", example = "payment-events")
    private String kafkaTopic;

    @Schema(description = "JSON Path to extract correlation businessKey/UUID from inbound event payload", example = "payload.cafId")
    private String correlationKeyPath;

    @Schema(description = "JSON structure representing event payload schema or description", example = "{\"amount\": 500, \"status\": \"SUCCESS\"}")
    private String payloadSchema;

    @Schema(description = "Flag specifying if the event is active", example = "true")
    private boolean active;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public EventDefinitionDto() {}

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
