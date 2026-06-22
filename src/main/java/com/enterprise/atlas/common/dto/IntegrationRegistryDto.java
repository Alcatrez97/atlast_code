package com.enterprise.atlas.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;

@Schema(description = "Data Transfer Object defining an external REST or database integration registry entry")
public class IntegrationRegistryDto implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "Unique database identifier (UUID)", readOnly = true, example = "i1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
    private String id;

    @Schema(description = "Business lookup key for integration endpoint", requiredMode = Schema.RequiredMode.REQUIRED, example = "FETCH_CUSTOMER_STATUS")
    private String integrationKey;

    @Schema(description = "Human-readable name of integration service", example = "Customer Profiler API")
    private String name;

    @Schema(description = "Technology type of integration provider", allowableValues = {"REST", "DB", "CONFIG"}, requiredMode = Schema.RequiredMode.REQUIRED, example = "REST")
    private String providerType; // REST, DB, CONFIG

    @Schema(description = "Absolute HTTP URL or DB connection string", requiredMode = Schema.RequiredMode.REQUIRED, example = "http://localhost:9095/customers/{customerId}")
    private String endpointUrl;

    @Schema(description = "HTTP request verb", allowableValues = {"GET", "POST"}, example = "GET")
    private String method;       // GET, POST

    @Schema(description = "HTTP Request headers mapped as a serialized JSON string", example = "{\"Authorization\": \"Bearer test-token\", \"Content-Type\": \"application/json\"}")
    private String headersJson;  // HTTP headers mapped as JSON string

    @Schema(description = "HTTP Request payload payload template (supports dynamic field parameter replacement templates)", example = "{\"user\": \"{customerId}\"}")
    private String requestTemplate; // Template body/query (Velocity/Freemarker style)

    @Schema(description = "Connection and request timeout duration in milliseconds", example = "5000")
    private Integer timeoutMs = 5000;

    public IntegrationRegistryDto() {}

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
}
