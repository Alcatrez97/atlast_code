package com.vi.atlas.workflow.context;

import com.vi.atlas.common.context.WorkflowContext;
import com.vi.atlas.common.dto.ExecutionRequestDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowContextTest {

    @Test
    @DisplayName("Normalizes circleId across snake_case, uppercase, and string formats")
    void testCircleIdNormalization() {
        Map<String, Object> map1 = new HashMap<>();
        map1.put("circle_id", "12");
        WorkflowContext ctx1 = WorkflowContext.from(map1);
        assertEquals(12, ctx1.getCircleId());
        assertEquals(12, map1.get("circleId")); // injected canonical key

        Map<String, Object> map2 = new HashMap<>();
        map2.put("CIRCLE_ID", 15);
        WorkflowContext ctx2 = WorkflowContext.from(map2);
        assertEquals(15, ctx2.getCircleId());
        assertEquals(15, map2.get("circleId"));
    }

    @Test
    @DisplayName("Normalizes cafId strictly numeric without hashcode fallback")
    void testCafIdNormalization() {
        Map<String, Object> map = new HashMap<>();
        map.put("caf_id", 999888777L);
        WorkflowContext ctx = WorkflowContext.from(map);
        assertEquals(999888777L, ctx.getCafId());
        assertEquals(999888777L, map.get("cafId"));
        assertEquals("999888777", ctx.getCafIdAsString());

        // String non-numeric formId should not be parsed into a numeric cafId
        Map<String, Object> stringFormMap = new HashMap<>();
        stringFormMap.put("customerFormId", "FORM_CUSTOM_123");
        WorkflowContext ctxNonNumeric = WorkflowContext.from(stringFormMap);
        assertNull(ctxNonNumeric.getCafId());
        assertEquals("FORM_CUSTOM_123", ctxNonNumeric.getFormId());
    }

    @Test
    @DisplayName("Creates WorkflowContext from ExecutionRequestDto with fallback defaults")
    void testFromExecutionRequestDto() {
        ExecutionRequestDto req = new ExecutionRequestDto();
        req.setCircleId("18");
        req.setBusinessKey("BK-555");
        Map<String, Object> inner = new HashMap<>();
        inner.put("customParam", "value1");
        req.setContext(inner);

        WorkflowContext ctx = WorkflowContext.of(req);
        assertEquals(18, ctx.getCircleId());
        assertEquals("BK-555", ctx.getBusinessKey());
        assertEquals("value1", ctx.get("customParam"));
        assertEquals(18, ctx.asMap().get("circleId"));
    }
}
