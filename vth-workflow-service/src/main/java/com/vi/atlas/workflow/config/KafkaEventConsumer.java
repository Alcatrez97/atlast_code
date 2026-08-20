package com.vi.atlas.workflow.config;

import com.vi.atlas.workflow.service.EventRoutingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collections;
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
    public void consume(@Payload Map<String, Object> message,
                        @Header(value = "X-User-Id", required = false) String userId) {
        log.info("Kafka event received: {}", message);
        try {
            if (userId != null && !userId.isBlank()) {
                log.info("Trusting Identity Header X-User-Id: {}", userId);
                SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList())
                );
            }

            String eventType = (String) message.get("eventType");
            String businessKey = (String) message.get("businessKey");
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) message.get("payload");

            if (eventType == null || businessKey == null) {
                log.warn("Invalid event format received from Kafka. Missing eventType or businessKey.");
                return;
            }

            eventRoutingService.routeEvent(eventType, businessKey, payload != null ? payload : Map.of());
        } catch (Exception ex) {
            log.error("Failed to parse Kafka event payload: {}", ex.getMessage(), ex);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
