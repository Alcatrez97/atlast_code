package com.enterprise.atlas.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "Data Transfer Object representing a business rule definition in the registry")
public class RuleDto implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "Unique database identifier (UUID)", readOnly = true, example = "63511c0b-18e3-4317-b518-bdcbba2ca172")
    private String id;

    @Schema(description = "Business rule lookup key", requiredMode = Schema.RequiredMode.REQUIRED, example = "CHECK_HIGH_VALUE")
    private String ruleKey;

    @Schema(description = "Human-readable rule name", example = "High Value Customer Rule")
    private String name;

    @Schema(description = "Detailed purpose of the rule", example = "Intercepts transactions over $5000")
    private String description;

    @Schema(description = "Spring SpEL expression representing the logic, evaluating against context map", requiredMode = Schema.RequiredMode.REQUIRED, example = "context['amount'] > 5000")
    private String expression;

    @Schema(description = "Flag specifying if the rule is active and runnable", example = "true")
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public RuleDto() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRuleKey() {
        return ruleKey;
    }

    public void setRuleKey(String ruleKey) {
        this.ruleKey = ruleKey;
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

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
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
