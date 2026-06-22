package com.enterprise.atlas.workflow.service;

import com.enterprise.atlas.workflow.entity.ContextField;
import com.enterprise.atlas.workflow.entity.IntegrationRegistry;

import java.util.Map;

public interface ContextProvider {
    Object resolve(ContextField field, IntegrationRegistry integration, Map<String, Object> currentContext);
}
