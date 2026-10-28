package com.vi.atlas.common.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CafSubmittedEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String cafId;
    private String contextId;
    private String businessKey;
    private String workflowKey;
    private Object circleId;
    private Map<String, Object> context;
    private Map<String, Object> additionalProperties = new HashMap<>();

    public CafSubmittedEvent() {}

    public CafSubmittedEvent(String cafId, String workflowKey, Map<String, Object> context) {
        this.cafId = cafId;
        this.contextId = cafId;
        this.workflowKey = workflowKey;
        this.context = context;
    }

    public String getCafId() {
        return cafId != null ? cafId : contextId;
    }

    public void setCafId(String cafId) {
        this.cafId = cafId;
        if (this.contextId == null) {
            this.contextId = cafId;
        }
    }

    public String getContextId() {
        return contextId != null ? contextId : cafId;
    }

    public void setContextId(String contextId) {
        this.contextId = contextId;
        if (this.cafId == null) {
            this.cafId = contextId;
        }
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    public String getWorkflowKey() {
        return workflowKey;
    }

    public void setWorkflowKey(String workflowKey) {
        this.workflowKey = workflowKey;
    }

    public Object getCircleId() {
        return circleId;
    }

    public void setCircleId(Object circleId) {
        this.circleId = circleId;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String key, Object value) {
        if (this.additionalProperties == null) {
            this.additionalProperties = new HashMap<>();
        }
        this.additionalProperties.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }
}
