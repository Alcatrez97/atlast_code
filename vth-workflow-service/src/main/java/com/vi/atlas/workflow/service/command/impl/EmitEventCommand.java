package com.vi.atlas.workflow.service.command.impl;

import com.vi.atlas.workflow.service.EventRoutingService;
import com.vi.atlas.workflow.service.command.WorkflowCommand;
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
        String eventKey = getStringParam(input, "eventKey", "eventType");
        if (eventKey == null) {
            throw new IllegalArgumentException("EMIT_EVENT command requires an 'eventKey' or 'eventType' parameter.");
        }

        String bKey = getStringParam(input, "businessKey", "cafId", "_instanceId");

        // Build event payload
        Map<String, Object> payload;
        Object payloadMappingObj = input.get("payloadMapping");
        if (payloadMappingObj instanceof Map) {
            payload = new HashMap<>();
            applyPayloadOrSpelMapping(payloadMappingObj, input, payload);
        } else {
            payload = extractCleanPayload(input, "eventKey", "eventType", "businessKey", "cafId");
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
