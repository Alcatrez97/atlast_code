package com.enterprise.atlas.common.dto;

import java.io.Serializable;
import java.util.Map;

public class CafSubmittedEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String cafId;
    private String workflowKey;
    private Map<String, Object> context;

    public CafSubmittedEvent() {}

    public CafSubmittedEvent(String cafId, String workflowKey, Map<String, Object> context) {
        this.cafId = cafId;
        this.workflowKey = workflowKey;
        this.context = context;
    }

    public String getCafId() {
        return cafId;
    }

    public void setCafId(String cafId) {
        this.cafId = cafId;
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
