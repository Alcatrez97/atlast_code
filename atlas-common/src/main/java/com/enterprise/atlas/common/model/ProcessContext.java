package com.enterprise.atlas.common.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * ProcessContext represents the execution context of a workflow instance.
 * It carries variables that rules, providers, and workflows evaluate or modify.
 */
public class ProcessContext implements Serializable {
    private static final long serialVersionUID = 1L;

    private String workflowInstanceId;
    private Map<String, Object> variables = new HashMap<>();

    public ProcessContext() {
    }

    public ProcessContext(String workflowInstanceId) {
        this.workflowInstanceId = workflowInstanceId;
    }

    public ProcessContext(String workflowInstanceId, Map<String, Object> variables) {
        this.workflowInstanceId = workflowInstanceId;
        if (variables != null) {
            this.variables = new HashMap<>(variables);
        }
    }

    public String getWorkflowInstanceId() {
        return workflowInstanceId;
    }

    public void setWorkflowInstanceId(String workflowInstanceId) {
        this.workflowInstanceId = workflowInstanceId;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        if (variables != null) {
            this.variables = variables;
        } else {
            this.variables = new HashMap<>();
        }
    }

    public Object getVariable(String name) {
        return this.variables.get(name);
    }

    public void setVariable(String name, Object value) {
        this.variables.put(name, value);
    }

    public boolean hasVariable(String name) {
        return this.variables.containsKey(name);
    }
}
