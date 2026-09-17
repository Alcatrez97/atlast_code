package com.vi.atlas.workflow.service.traversal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.expression.EvaluationContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SpelEvaluatorTest {

    private SpelEvaluator spelEvaluator;

    @BeforeEach
    public void setUp() {
        spelEvaluator = new SpelEvaluator();
    }

    @Test
    public void testBracketAndDotVariables() {
        Map<String, Object> context = Map.of(
                "amount", 1000,
                "status", "APPROVED",
                "customerName", "Priya"
        );
        EvaluationContext evalCtx = SpelEvaluator.createEvaluationContext(context);

        // #context['key']
        Object res1 = spelEvaluator.evaluate("#context['amount'] > 500", evalCtx);
        assertEquals(true, res1);

        // context.key
        Object res2 = spelEvaluator.evaluate("context.amount > 500", evalCtx);
        assertEquals(true, res2);

        // direct key
        Object res3 = spelEvaluator.evaluate("amount == 1000", evalCtx);
        assertEquals(true, res3);

        // String comparison
        Object res4 = spelEvaluator.evaluate("#context['status'] == 'APPROVED'", evalCtx);
        assertEquals(true, res4);

        // Instance method invocation (allowed in SimpleEvaluationContext withInstanceMethods)
        Object res5 = spelEvaluator.evaluate("#context['customerName'].toUpperCase() == 'PRIYA'", evalCtx);
        assertEquals(true, res5);
    }

    @Test
    public void testSecurityHardeningBlocksArbitraryCodeExecution() {
        Map<String, Object> context = Map.of("data", "hello");
        EvaluationContext evalCtx = SpelEvaluator.createEvaluationContext(context);

        // Malicious SpEL attempting Java type reflection (T operator) must be blocked
        Object malicious1 = spelEvaluator.evaluate("T(java.lang.Runtime).getRuntime()", evalCtx);
        assertNull(malicious1, "T(...) type locator must be blocked by SimpleEvaluationContext");

        Object malicious2 = spelEvaluator.evaluate("new java.io.File('/tmp')", evalCtx);
        assertNull(malicious2, "new constructor invocation must be blocked by SimpleEvaluationContext");
    }
}
