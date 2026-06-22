package com.enterprise.atlas.workflow.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Persisted registry configuration for external endpoints/datasources.
 */
@Entity
@Table(name = "integration_registry", indexes = {
    @Index(name = "idx_int_key", columnList = "integration_key", unique = true)
})
public class IntegrationRegistry {

    @Id
    @Column(name = "integration_pk", length = 36)
    private String id;

    @Column(name = "integration_key", nullable = false, length = 100)
    private String integrationKey;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "provider_type", nullable = false, length = 20)
    private String providerType; // REST, DB, CONFIG

    @Column(name = "endpoint_url", length = 500)
    private String endpointUrl;

    @Column(name = "method", length = 10)
    private String method; // GET, POST

    @Column(name = "headers_json", length = 2000)
    private String headersJson;

    @Column(name = "request_template", length = 2000)
    private String requestTemplate;

    @Column(name = "timeout_ms")
    private Integer timeoutMs = 5000;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public IntegrationRegistry() {}

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

    public String getIntegrationKey() {
        return integrationKey;
    }

    public void setIntegrationKey(String integrationKey) {
        this.integrationKey = integrationKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProviderType() {
        return providerType;
    }

    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getHeadersJson() {
        return headersJson;
    }

    public void setHeadersJson(String headersJson) {
        this.headersJson = headersJson;
    }

    public String getRequestTemplate() {
        return requestTemplate;
    }

    public void setRequestTemplate(String requestTemplate) {
        this.requestTemplate = requestTemplate;
    }

    public Integer getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(Integer timeoutMs) {
        this.timeoutMs = timeoutMs;
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
