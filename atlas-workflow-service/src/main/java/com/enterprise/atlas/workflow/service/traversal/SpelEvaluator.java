package com.enterprise.atlas.workflow.service.traversal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Centralises all Spring Expression Language (SpEL) operations used during
 * graph traversal.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Thread-safe, application-scoped expression cache (avoids repeated parse overhead)</li>
 *   <li>{@link #evaluate(String, StandardEvaluationContext)} — safe evaluation that returns
 *       {@code null} on any error instead of propagating an exception</li>
 *   <li>{@link #explain(String, Map, Object)} — human-readable explanation of an evaluation
 *       result (used in step trace notes)</li>
 * </ul>
 */
@Component
public class SpelEvaluator {

    private static final Logger log = LoggerFactory.getLogger(SpelEvaluator.class);

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final Map<String, Expression> EXPRESSION_CACHE = new ConcurrentHashMap<>();

    // Patterns used by explain() to extract variable names referenced in an expression
    private static final Pattern BRACKET_VAR = Pattern.compile("context\\[['\"](.*?)['\"]\\]");
    private static final Pattern DOT_VAR     = Pattern.compile("context\\.([a-zA-Z0-9_]+)");

    /**
     * Evaluates a SpEL expression against the supplied context.
     *
     * @param expression the SpEL expression string (may be {@code null} or blank)
     * @param spelCtx    the evaluation context containing the {@code context} variable
     * @return the evaluation result, or {@code null} if the expression is blank / fails
     */
    public Object evaluate(String expression, StandardEvaluationContext spelCtx) {
        if (expression == null || expression.isBlank()) return null;
        try {
            Expression parsed = EXPRESSION_CACHE.computeIfAbsent(expression, PARSER::parseExpression);
            return parsed.getValue(spelCtx);
        } catch (EvaluationException | org.springframework.expression.ParseException ex) {
            log.warn("SpEL evaluation failed for expression '{}': {}", expression, ex.getMessage());
            return null;
        }
    }

    /**
     * Builds a structured JSON explanation of why an expression passed or failed,
     * including the actual runtime values of all referenced context variables.
     *
     * @param expression the SpEL expression that was evaluated
     * @param context    the runtime context map
     * @param result     the result returned by {@link #evaluate}
     * @return JSON string, or a plain text fallback if JSON serialisation fails
     */
    public String explain(String expression, Map<String, Object> context, Object result) {
        if (expression == null || expression.isBlank()) {
            return "No expression evaluated.";
        }

        // Collect every context variable referenced by the expression
        Set<String> variablesUsed = new HashSet<>();
        Matcher m1 = BRACKET_VAR.matcher(expression);
        while (m1.find()) variablesUsed.add(m1.group(1));
        Matcher m2 = DOT_VAR.matcher(expression);
        while (m2.find()) variablesUsed.add(m2.group(1));

        Map<String, Object> valuesUsed = new HashMap<>();
        for (String var : variablesUsed) {
            valuesUsed.put(var, context.get(var));
        }

        Map<String, Object> explanation = new LinkedHashMap<>();
        explanation.put("rule",          expression);
        explanation.put("condition",     expression);
        explanation.put("variablesUsed", variablesUsed);
        explanation.put("actualValues",  valuesUsed);
        explanation.put("result",        result);
        explanation.put("outcome",       Boolean.TRUE.equals(result) ? "PASS" : "FAIL");
        explanation.put("reason",        Boolean.TRUE.equals(result)
                ? "Condition evaluated to true with actual values: "  + valuesUsed
                : "Condition evaluated to false with actual values: " + valuesUsed);

        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(explanation);
        } catch (Exception e) {
            return "Rule: " + expression + " | Result: " + result + " | Values: " + valuesUsed;
        }
    }
}
