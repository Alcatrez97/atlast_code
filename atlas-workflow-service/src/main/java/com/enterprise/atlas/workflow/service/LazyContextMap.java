package com.enterprise.atlas.workflow.service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom lazy-loading Map wrapper passed to SpEL expression context.
 * Intercepts gets to dynamically resolve missing values through ContextResolutionService.
 */
public class LazyContextMap extends AbstractMap<String, Object> {

    private final String workflowKey;
    private final String contextId;
    private final Map<String, Object> resolvedValues = new ConcurrentHashMap<>();
    private final ContextResolutionService resolutionService;
    private final List<Map<String, Object>> resolutionEvents = Collections.synchronizedList(new ArrayList<>());

    public LazyContextMap(String workflowKey, String contextId, Map<String, Object> initialPayload, ContextResolutionService resolutionService) {
        this.workflowKey = workflowKey;
        this.contextId = contextId;
        this.resolutionService = resolutionService;
        if (initialPayload != null) {
            this.resolvedValues.putAll(initialPayload);
        }
    }

    @Override
    public Object get(Object key) {
        if (key == null) return null;
        String varKey = String.valueOf(key);
        
        // Return pre-resolved or cached value in this local scope
        if (resolvedValues.containsKey(varKey)) {
            Object val = resolvedValues.get(varKey);
            return val;
        }

        // Delegate resolution to active Context Resolution Service
        Object resolved = resolutionService.resolveVariable(workflowKey, contextId, varKey, this);
        if (resolved != null) {
            resolvedValues.put(varKey, resolved);
        }
        return resolved;
    }

    @Override
    public boolean containsKey(Object key) {
        if (key == null) return false;
        // In SpEL context['key'] checks, we want to pretend we contain keys registered in schema
        // Or simply trigger lazy resolution to check if it has a value
        Object resolved = get(key);
        return resolved != null;
    }

    @Override
    public Object put(String key, Object value) {
        if (key == null) return null;
        return resolvedValues.put(key, value);
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return resolvedValues.entrySet();
    }

    public void logEvent(String key, String eventType, String detail) {
        Map<String, Object> event = new HashMap<>();
        event.put("timestamp", LocalDateTime.now().toString());
        event.put("variable", key);
        event.put("eventType", eventType);
        event.put("detail", detail);
        resolutionEvents.add(event);
    }

    public List<Map<String, Object>> getResolutionEvents() {
        return new ArrayList<>(resolutionEvents);
    }

    public String getContextId() {
        return contextId;
    }
}
