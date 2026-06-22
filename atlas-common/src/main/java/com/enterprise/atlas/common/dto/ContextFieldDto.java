package com.enterprise.atlas.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;

@Schema(description = "Data Transfer Object representing a typed field in a context resolution schema")
public class ContextFieldDto implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "Unique database identifier (UUID)", readOnly = true, example = "c1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
    private String id;

    @Schema(description = "Associated context schema parent identifier (UUID)", example = "d4e5f6a7-b8c9-0d1e-2f3a-4b5c6d7e8f9a")
    private String schemaId;

    @Schema(description = "Key utilized inside SpEL expressions to query this context field", requiredMode = Schema.RequiredMode.REQUIRED, example = "amount")
    private String fieldKey;        // the key used in SpEL: context['fieldKey']

    @Schema(description = "Human-readable label for user interface form fields", example = "Transaction Amount")
    private String displayName;     // human-readable label

    @Schema(description = "Data type of the field value", allowableValues = {"STRING", "NUMBER", "BOOLEAN", "DATE"}, requiredMode = Schema.RequiredMode.REQUIRED, example = "NUMBER")
    private String fieldType;       // STRING, NUMBER, BOOLEAN, DATE

    @Schema(description = "Flag specifying if this field is required to initiate execution", example = "true")
    private boolean required;

    @Schema(description = "Default fallback value if not supplied in the inbound request context", example = "0")
    private String defaultValue;

    @Schema(description = "Detailed explanation of the field's use and source", example = "Total transaction amount in cents/dollars")
    private String description;

    @Schema(description = "Positional order index for visual form layouts", example = "1")
    private int fieldOrder;
    
    // Sprint 7: Dynamic context resolution & integration fields
    @Schema(description = "Linked Integration API registry identifier (UUID) for runtime resolution", example = "f5a6b7c8-d9e0-1f2a-3b4c-5d6e7f8a9b0c")
    private String integrationId;

    @Schema(description = "JSONPath/Response query mapping to extract the resolved value from raw API JSON response", example = "$.data.balance")
    private String responseMapping;

    @Schema(description = "Flag specifying if dynamically resolved API responses can be cached", example = "true")
    private boolean cacheable = true;

    @Schema(description = "Time-to-Live (TTL) cache duration in seconds", example = "300")
    private Integer ttlSeconds = 300;

    @Schema(description = "Estimated computational/external cost classification of this context lookup", allowableValues = {"LOW", "MEDIUM", "HIGH"}, example = "LOW")
    private String cost = "LOW";    // LOW, MEDIUM, HIGH

    @Schema(description = "Fallback SpEL derivation calculation expression if target integration is absent", example = "context['basePrice'] * 1.1")
    private String expression;      // SpEL expression if derived

    public ContextFieldDto() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSchemaId() { return schemaId; }
    public void setSchemaId(String schemaId) { this.schemaId = schemaId; }

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

    public String getIntegrationId() { return integrationId; }
    public void setIntegrationId(String integrationId) { this.integrationId = integrationId; }

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
