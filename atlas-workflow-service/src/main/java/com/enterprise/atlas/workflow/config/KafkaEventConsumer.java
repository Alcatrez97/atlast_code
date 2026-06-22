package com.enterprise.atlas.workflow.config;

import com.enterprise.atlas.workflow.service.EventRoutingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Conditional Kafka consumer that listens to inbound events.
 * Enabled only when setting `kafka.enabled=true` in application.properties.
 */
@Component
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = false)
public class KafkaEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventConsumer.class);

    @Autowired
    private EventRoutingService eventRoutingService;

    @KafkaListener(topics = "${kafka.topics.events:workflow-events}", groupId = "${kafka.group-id:workflow-engine-group}")
    public void consume(Map<String, Object> message) {
        log.info("Kafka event received: {}", message);
        try {
            String eventType = (String) message.get("eventType");
            String businessKey = (String) message.get("businessKey");
            Map<String, Object> payload = (Map<String, Object>) message.get("payload");

            if (eventType == null || businessKey == null) {
                log.warn("Invalid event format received from Kafka. Missing eventType or businessKey.");
                return;
            }

            eventRoutingService.routeEvent(eventType, businessKey, payload != null ? payload : Map.of());
        } catch (Exception ex) {
            log.error("Failed to parse Kafka event payload: {}", ex.getMessage(), ex);
        }
    }
}
