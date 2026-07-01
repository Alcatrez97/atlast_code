package com.enterprise.atlas.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Schema(description = "Inbound execution request payload for executing a workflow key")
public class ExecutionRequestDto implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "Optional client-supplied correlation context ID (UUID)", example = "test-stateful-ctx-12345")
    private String contextId;

    @Schema(description = "Business key to correlate this execution (e.g. CAF123, MSISDN99)", example = "CAF123")
    private String businessKey;

    @Schema(description = "Flat or nested JSON context map passed into the workflow execution", 
            example = "{\"initial_param\": \"some_value\", \"amount\": 6500, \"birthYear\": 1998, \"currentYear\": 2024}")
    private Map<String, Object> context = new HashMap<>();

    public ExecutionRequestDto() {}

    public String getContextId() { return contextId; }
    public void setContextId(String contextId) { this.contextId = contextId; }

    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }

    public Map<String, Object> getContext() { return context; }
    public void setContext(Map<String, Object> context) {
        if (context != null) this.context = context;
    }
}
