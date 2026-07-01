package com.enterprise.atlas.workflow.controller;

import com.enterprise.atlas.workflow.service.EventRoutingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/events")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Tag(name = "Event Orchestration Ingestion API", description = "Operations to simulate inbound messaging events (e.g. Kafka triggers, webhooks) and correlate them to suspended task instances")
public class EventController {

    @Autowired
    private EventRoutingService eventRoutingService;

    public static class EventPayload {
        private String eventId;
        private String eventType;
        private String timestamp;
        private String businessKey;
        private Map<String, Object> payload;

        public EventPayload() {}

        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }

        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }

        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

        public String getBusinessKey() { return businessKey; }
        public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }

        public Map<String, Object> getPayload() { return payload; }
        public void setPayload(Map<String, Object> payload) { this.payload = payload; }
    }

    @PostMapping
    @Operation(summary = "Publish simulation event", description = "Simulates inbound messages and routes them to resume correlated suspended workflows")
    public ResponseEntity<Void> publishEvent(@RequestBody EventPayload body) {
        if (body.getEventType() == null) {
            return ResponseEntity.badRequest().build();
        }
        eventRoutingService.routeEvent(
                body.getEventType(), 
                body.getBusinessKey(), 
                body.getPayload() != null ? body.getPayload() : Map.of()
        );
        return ResponseEntity.ok().build();
    }
}
