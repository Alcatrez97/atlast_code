package com.vi.atlas.common.dto;

import java.io.Serializable;
import java.util.Map;

public class CafSubmittedEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String cafId;
    private String contextId;
    private String businessKey;
    private String workflowKey;
    private Map<String, Object> context;

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

    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context;
    }
}
