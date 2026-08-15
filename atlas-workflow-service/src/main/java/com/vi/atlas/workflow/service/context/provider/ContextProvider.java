package com.vi.atlas.workflow.service.context.provider;

import com.vi.atlas.workflow.entity.ContextField;
import com.vi.atlas.workflow.entity.IntegrationRegistry;

import java.util.Map;

public interface ContextProvider {
    Object resolve(ContextField field, IntegrationRegistry integration, Map<String, Object> currentContext);
}
