package com.enterprise.atlas.workflow.entity;

import jakarta.persistence.*;

/**
 * A single typed field definition within a ContextSchema.
 */
@Entity
@Table(name = "context_fields", indexes = {
    @Index(name = "idx_ctx_field_schema", columnList = "schema_id")
})
public class ContextField {

    @Id
    @Column(name = "context_field_pk", length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schema_id", nullable = false)
    private ContextSchema schema;

    @Column(name = "field_key", nullable = false, length = 100)
    private String fieldKey;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(name = "field_type", nullable = false, length = 20)
    private String fieldType; // STRING, NUMBER, BOOLEAN, DATE

    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "default_value", length = 500)
    private String defaultValue;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "field_order", nullable = false)
    private int fieldOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "integration_id", foreignKey = @ForeignKey(name = "fk_ctx_field_integration"))
    private IntegrationRegistry integrationRegistry;

    @Column(name = "response_mapping", length = 250)
    private String responseMapping;

    @Column(name = "cacheable", nullable = false)
    private boolean cacheable = true;

    @Column(name = "ttl_seconds")
    private Integer ttlSeconds = 300;

    @Column(name = "cost", nullable = false, length = 20)
    private String cost = "LOW";

    @Column(name = "expression", length = 1000)
    private String expression;

    public ContextField() {}

    // ---- Getters & Setters ----

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public ContextSchema getSchema() { return schema; }
    public void setSchema(ContextSchema schema) { this.schema = schema; }

    public String getFieldKey() { return fieldKey; }
    public void setFieldKey(String fieldKey) { this.fieldKey = fieldKey; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getFieldType() { return fieldType; }
    public void setFieldType(String fieldType) { this.fieldType = fieldType; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getFieldOrder() { return fieldOrder; }
    public void setFieldOrder(int fieldOrder) { this.fieldOrder = fieldOrder; }

    public String getIntegrationId() {
        return integrationRegistry != null ? integrationRegistry.getId() : null;
    }
    public void setIntegrationId(String integrationId) {
        if (integrationId == null) {
            this.integrationRegistry = null;
        } else {
            this.integrationRegistry = new IntegrationRegistry();
            this.integrationRegistry.setId(integrationId);
        }
    }

    public String getResponseMapping() { return responseMapping; }
    public void setResponseMapping(String responseMapping) { this.responseMapping = responseMapping; }

    public boolean isCacheable() { return cacheable; }
    public void setCacheable(boolean cacheable) { this.cacheable = cacheable; }

    public Integer getTtlSeconds() { return ttlSeconds; }
    public void setTtlSeconds(Integer ttlSeconds) { this.ttlSeconds = ttlSeconds; }

    public String getCost() { return cost; }
    public void setCost(String cost) { this.cost = cost; }

    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }
}
