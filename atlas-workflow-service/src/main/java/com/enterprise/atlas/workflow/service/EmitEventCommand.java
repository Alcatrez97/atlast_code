package com.enterprise.atlas.workflow.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class EmitEventCommand implements WorkflowCommand {

    private static final Logger log = LoggerFactory.getLogger(EmitEventCommand.class);

    @Autowired
    @Lazy
    private EventRoutingService eventRoutingService;

    @Override
    public String getCommandType() {
        return "EMIT_EVENT";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> input) throws Exception {
        String eventKey = (String) input.get("eventKey");
        if (eventKey == null || eventKey.isBlank()) {
            eventKey = (String) input.get("eventType");
        }

        if (eventKey == null || eventKey.isBlank()) {
            throw new IllegalArgumentException("EMIT_EVENT command requires an 'eventKey' or 'eventType' parameter.");
        }

        String bKey = (String) input.get("businessKey");
        if (bKey == null) {
            bKey = (String) input.get("cafId");
        }
        if (bKey == null) {
            bKey = (String) input.get("_instanceId");
        }

        // Build event payload
        Map<String, Object> payload = new HashMap<>();
        Object payloadMappingObj = input.get("payloadMapping");
        if (payloadMappingObj instanceof Map) {
            Map<?, ?> mapping = (Map<?, ?>) payloadMappingObj;
            for (Map.Entry<?, ?> entry : mapping.entrySet()) {
                String targetKey = String.valueOf(entry.getKey());      // e.g. "cafId"
                String sourceExpr = String.valueOf(entry.getValue());    // e.g. "context.targetCafId" or "targetCafId"

                // Evaluate SpEL or get from global context
                Map<String, Object> globalContext = (Map<String, Object>) input.get("_context");
                Object value = null;
                if (globalContext != null) {
                    if (sourceExpr.contains(".") || sourceExpr.contains("[") || sourceExpr.contains("'") || sourceExpr.contains("context")) {
                        try {
                            org.springframework.expression.ExpressionParser parser = new org.springframework.expression.spel.standard.SpelExpressionParser();
                            Map<String, Object> root = Map.of("context", globalContext);
                            org.springframework.expression.spel.support.StandardEvaluationContext spelCtx = new org.springframework.expression.spel.support.StandardEvaluationContext(root);
                            spelCtx.addPropertyAccessor(new org.springframework.context.expression.MapAccessor());
                            spelCtx.setVariable("context", globalContext);
                            value = parser.parseExpression(sourceExpr).getValue(spelCtx);
                        } catch (Exception e) {
                            value = globalContext.get(sourceExpr);
                        }
                    } else {
                        value = globalContext.get(sourceExpr);
                        if (value == null && sourceExpr.startsWith("context.")) {
                            value = globalContext.get(sourceExpr.substring(8));
                        }
                    }
                }
                if (value == null) {
                    value = input.get(sourceExpr);
                }
                if (value != null) {
                    payload.put(targetKey, value);
                }
            }
        } else {
            // Default: pass through all input values
            payload.putAll(input);
            payload.remove("eventKey");
            payload.remove("eventType");
            payload.remove("businessKey");
            payload.remove("cafId");
            payload.remove("commandType");
            payload.remove("inputMapping");
            payload.remove("outputMapping");
        }

        log.info("Executing EMIT_EVENT command: eventKey={}, businessKey={}, payload={}", eventKey, bKey, payload);

        try {
            eventRoutingService.routeEvent(eventKey, bKey, payload);
            log.info("Successfully emitted event '{}' for businessKey '{}'", eventKey, bKey);
            return Map.of("status", "EMITTED", "eventKey", eventKey);
        } catch (Exception e) {
            log.error("Failed to emit event key={} via EMIT_EVENT command: {}", eventKey, e.getMessage(), e);
            throw e;
        }
    }
}
