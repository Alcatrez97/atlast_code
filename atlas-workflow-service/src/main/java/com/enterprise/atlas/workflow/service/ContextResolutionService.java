package com.enterprise.atlas.workflow.service;

import com.enterprise.atlas.workflow.entity.ContextField;
import com.enterprise.atlas.workflow.entity.ContextSchema;
import com.enterprise.atlas.workflow.entity.IntegrationRegistry;
import com.enterprise.atlas.workflow.repository.ContextSchemaRepository;
import com.enterprise.atlas.workflow.repository.IntegrationRegistryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ContextResolutionService {

    private static final Logger log = LoggerFactory.getLogger(ContextResolutionService.class);
    private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();

    @Autowired
    private ContextSchemaRepository schemaRepository;

    @Autowired
    private IntegrationRegistryRepository integrationRepository;

    @Autowired
    private RestContextProvider restProvider;

    @Autowired
    private DbContextProvider dbProvider;

    // Cache structure: executionId -> (variableKey -> CacheEntry)
    private final Map<String, Map<String, CacheEntry>> executionCaches = new ConcurrentHashMap<>();
    
    // ThreadLocal to detect circular resolutions
    private final ThreadLocal<Set<String>> resolvingKeys = ThreadLocal.withInitial(HashSet::new);

    private static class CacheEntry {
        Object value;
        LocalDateTime expiresAt;

        CacheEntry(Object value, Integer ttlSeconds) {
            this.value = value;
            this.expiresAt = ttlSeconds != null && ttlSeconds > 0 
                    ? LocalDateTime.now().plusSeconds(ttlSeconds) 
                    : null;
        }

        boolean isExpired() {
            return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
        }
    }

    public void clearCacheForExecution(String executionId) {
        executionCaches.remove(executionId);
    }

    public Object resolveVariable(String workflowKey, String executionId, String fieldKey, LazyContextMap contextMap) {
        // Prevent circular resolutions
        Set<String> activeKeys = resolvingKeys.get();
        if (activeKeys.contains(fieldKey)) {
            throw new IllegalStateException("Circular dependency detected during resolution of variable: " + fieldKey + " -> Path: " + activeKeys);
        }
        activeKeys.add(fieldKey);
        contextMap.logEvent(fieldKey, "RESOLUTION_STARTED", "Resolving variable...");

        try {
            // Find schema definition
            Optional<ContextSchema> schemaOpt = schemaRepository.findByWorkflowKey(workflowKey);
            if (schemaOpt.isEmpty()) {
                log.debug("Context schema not found for workflow key: {}", workflowKey);
                contextMap.logEvent(fieldKey, "RESOLUTION_SKIPPED", "No context schema registered for workflow; returning null.");
                return null;
            }
            ContextSchema schema = schemaOpt.get();

            ContextField field = schema.getFields().stream()
                    .filter(f -> f.getFieldKey().equals(fieldKey))
                    .findFirst()
                    .orElse(null);

            if (field == null) {
                // Not defined in schema - return null (or check default/inputs)
                contextMap.logEvent(fieldKey, "RESOLUTION_SKIPPED", "Not defined in context schema; returning null.");
                return null;
            }

            // Check Cache
            if (field.isCacheable()) {
                Map<String, CacheEntry> cache = executionCaches.computeIfAbsent(executionId, k -> new ConcurrentHashMap<>());
                CacheEntry entry = cache.get(fieldKey);
                if (entry != null && !entry.isExpired()) {
                    log.info("Cache hit for variable '{}' inside execution '{}'", fieldKey, executionId);
                    contextMap.logEvent(fieldKey, "CACHE_HIT", "Value returned from execution cache: " + entry.value);
                    return entry.value;
                }
            }

            Object result = null;

            // Scenario A: Derived Expression (SpEL formula)
            if (field.getExpression() != null && !field.getExpression().isBlank()) {
                contextMap.logEvent(fieldKey, "DERIVED_EXPRESSION_EVAL", "Evaluating SpEL derived expression: " + field.getExpression());
                Map<String, Object> root = Map.of("context", contextMap);
                StandardEvaluationContext spelCtx = new StandardEvaluationContext(root);
                spelCtx.addPropertyAccessor(new org.springframework.context.expression.MapAccessor());
                spelCtx.setVariable("context", contextMap);
                Expression expr = SPEL_PARSER.parseExpression(field.getExpression());
                result = expr.getValue(spelCtx);
            } 
            // Scenario B: Integration Provider
            else if (field.getIntegrationId() != null && !field.getIntegrationId().isBlank()) {
                IntegrationRegistry integration = integrationRepository.findById(field.getIntegrationId())
                        .orElseThrow(() -> new IllegalArgumentException("Integration not found with ID: " + field.getIntegrationId()));

                contextMap.logEvent(fieldKey, "INTEGRATION_CALL", "Triggering provider " + integration.getProviderType() + " for integration " + integration.getIntegrationKey());
                long start = System.currentTimeMillis();
                
                if ("REST".equalsIgnoreCase(integration.getProviderType())) {
                    result = restProvider.resolve(field, integration, contextMap);
                } else if ("DB".equalsIgnoreCase(integration.getProviderType())) {
                    result = dbProvider.resolve(field, integration, contextMap);
                } else {
                    throw new UnsupportedOperationException("Unsupported provider type: " + integration.getProviderType());
                }
                
                contextMap.logEvent(fieldKey, "INTEGRATION_RESPONSE", "Integration completed in " + (System.currentTimeMillis() - start) + "ms. Returned value: " + result);
            } 
            // Scenario C: Default value fallback
            else if (field.getDefaultValue() != null && !field.getDefaultValue().isBlank()) {
                contextMap.logEvent(fieldKey, "DEFAULT_VALUE_FALLBACK", "Returning configured default value: " + field.getDefaultValue());
                result = castValue(field.getDefaultValue(), field.getFieldType());
            }

            // Save to cache if result is resolved and cacheable is true
            if (result != null && field.isCacheable()) {
                Map<String, CacheEntry> cache = executionCaches.computeIfAbsent(executionId, k -> new ConcurrentHashMap<>());
                cache.put(fieldKey, new CacheEntry(result, field.getTtlSeconds()));
            }

            contextMap.logEvent(fieldKey, "RESOLUTION_SUCCESS", "Successfully resolved value: " + result);
            return result;

        } catch (Exception ex) {
            log.error("Failed to resolve context variable '{}': {}", fieldKey, ex.getMessage(), ex);
            contextMap.logEvent(fieldKey, "RESOLUTION_FAILED", "Error: " + ex.getMessage());
            throw new RuntimeException("Error resolving context variable '" + fieldKey + "': " + ex.getMessage(), ex);
        } finally {
            activeKeys.remove(fieldKey);
        }
    }

    private Object castValue(String val, String type) {
        if (val == null) return null;
        switch (type.toUpperCase()) {
            case "NUMBER":
                return Double.parseDouble(val);
            case "BOOLEAN":
                return Boolean.parseBoolean(val);
            default:
                return val;
        }
    }
}
