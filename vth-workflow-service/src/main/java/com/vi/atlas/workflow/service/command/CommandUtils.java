package com.vi.atlas.workflow.service.command;

import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.*;

/**
 * Reusable utility methods for command execution, input/output/payload mapping,
 * parameter extraction, and context manipulation.
 */
public final class CommandUtils {

    private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();
    private static final Set<String> DEFAULT_EXCLUDED_KEYS = Set.of(
            "_instanceId", "_contextId", "_workflowKey", "_nodeId", "_nodeLabel", "_context",
            "commandType", "inputMapping", "outputMapping", "payloadMapping"
    );

    private CommandUtils() {}

    /**
     * Maps variables from {@code sourceMap} into {@code targetMap} using the key-value mappings
     * provided in {@code mappingObj} (e.g. Map of "sourceKey" -> "targetKey").
     *
     * @param mappingObj the mapping definition map (or null)
     * @param sourceMap the source map to read values from
     * @param targetMap the target map to populate
     */
    public static void applyMapping(Object mappingObj, Map<String, Object> sourceMap, Map<String, Object> targetMap) {
        if (!(mappingObj instanceof Map<?, ?> mapping) || sourceMap == null || targetMap == null) {
            return;
        }
        for (Map.Entry<?, ?> entry : mapping.entrySet()) {
            String sourceVar = String.valueOf(entry.getKey());
            String targetVar = String.valueOf(entry.getValue());
            Object resolvedValue = sourceMap.get(sourceVar);
            if (resolvedValue != null) {
                targetMap.put(targetVar, resolvedValue);
            }
        }
    }

    /**
     * Extracts "inputMapping" from {@code input} and maps the resolved values into {@code targetMap}.
     *
     * @param input the command input map
     * @param targetMap the target map to populate (e.g. child workflow input)
     */
    public static void applyInputMapping(Map<String, Object> input, Map<String, Object> targetMap) {
        if (input == null || targetMap == null) return;
        Object inputMappingObj = input.get("inputMapping");
        applyMapping(inputMappingObj, input, targetMap);
    }

    /**
     * Resolves complex payload expressions (SpEL or context properties) into {@code targetMap}.
     *
     * @param mappingObj the payload mapping definition (e.g. Map of "targetField" -> "expression")
     * @param input the command input map containing "_context" and fallback variables
     * @param targetMap the target payload map to populate
     */
    @SuppressWarnings("unchecked")
    public static void applyPayloadOrSpelMapping(Object mappingObj, Map<String, Object> input, Map<String, Object> targetMap) {
        if (!(mappingObj instanceof Map<?, ?> mapping) || input == null || targetMap == null) {
            return;
        }

        Map<String, Object> globalContext = (Map<String, Object>) input.get("_context");
        StandardEvaluationContext spelCtx = null;
        if (globalContext != null) {
            spelCtx = new StandardEvaluationContext(Map.of("context", globalContext));
            spelCtx.addPropertyAccessor(new org.springframework.context.expression.MapAccessor());
            spelCtx.setVariable("context", globalContext);
        }

        for (Map.Entry<?, ?> entry : mapping.entrySet()) {
            String targetKey = String.valueOf(entry.getKey());
            String sourceExpr = String.valueOf(entry.getValue());
            Object value = null;

            if (globalContext != null) {
                if (sourceExpr.contains(".") || sourceExpr.contains("[") || sourceExpr.contains("'") || sourceExpr.contains("context")) {
                    try {
                        value = SPEL_PARSER.parseExpression(sourceExpr).getValue(spelCtx);
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
                targetMap.put(targetKey, value);
            }
        }
    }

    /**
     * Creates a clean payload copy of {@code input} with framework and metadata keys removed.
     *
     * @param input the incoming command input map
     * @param additionalExcludedKeys optional additional keys to exclude
     * @return clean payload map
     */
    public static Map<String, Object> extractPayload(Map<String, Object> input, String... additionalExcludedKeys) {
        if (input == null) return new HashMap<>();
        Map<String, Object> payload = new HashMap<>(input);
        for (String key : DEFAULT_EXCLUDED_KEYS) {
            payload.remove(key);
        }
        if (additionalExcludedKeys != null) {
            for (String key : additionalExcludedKeys) {
                payload.remove(key);
            }
        }
        return payload;
    }

    /**
     * Extracts the first non-null, non-blank string value matching any candidate key from {@code input}.
     *
     * @param input the input map
     * @param candidateKeys ordered candidate key names
     * @return matching string value or null
     */
    public static String getStringParam(Map<String, Object> input, String... candidateKeys) {
        if (input == null || candidateKeys == null) return null;
        for (String key : candidateKeys) {
            Object val = input.get(key);
            if (val != null) {
                String str = String.valueOf(val).trim();
                if (!str.isEmpty()) {
                    return str;
                }
            }
        }
        return null;
    }
}
