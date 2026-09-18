package com.vi.atlas.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vi.atlas.workflow.entity.EventDefinition;
import com.vi.atlas.workflow.repository.EventDefinitionRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Dynamically subscribes to all Kafka topics configured across active Event Definitions
 * in the Event Registry (e.g. 'submit-events', 'caf-events', 'parallel-events').
 *
 * Whenever an event definition is created or updated, {@link #refreshTopics()} dynamically
 * re-subscribes the consumer container to newly added topics without requiring a restart.
 */
@Service
@Profile("!test")
public class DynamicKafkaConsumerService implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(DynamicKafkaConsumerService.class);

    @Autowired
    private ConsumerFactory<String, Object> consumerFactory;

    @Autowired
    private EventDefinitionRepository eventDefinitionRepository;

    @Autowired
    private EventRoutingService eventRoutingService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${spring.kafka.consumer.group-id:atlas-workflow-dynamic-group}")
    private String dynamicGroupId;

    private ConcurrentMessageListenerContainer<String, Object> container;
    private Set<String> activeTopics = new HashSet<>();

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Initializing DynamicKafkaConsumerService on application startup...");
        refreshTopics();
    }

    /**
     * Re-queries active topics from the Event Registry and updates the listener container.
     */
    public synchronized void refreshTopics() {
        try {
            List<EventDefinition> definitions = eventDefinitionRepository.findByActiveTrue();

            Set<String> desiredTopics = definitions.stream()
                    .map(EventDefinition::getKafkaTopic)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(t -> !t.isEmpty())
                    .collect(Collectors.toSet());

            // Always include default workflow-events topic
            desiredTopics.add("workflow-events");

            if (container != null && container.isRunning() && activeTopics.equals(desiredTopics)) {
                log.info("DynamicKafkaConsumerService: No changes in topics. Active topics: {}", activeTopics);
                return;
            }

            log.info("DynamicKafkaConsumerService: Subscribing to topics: {}", desiredTopics);

            stopContainer();

            if (desiredTopics.isEmpty()) {
                log.warn("DynamicKafkaConsumerService: No topics configured to listen.");
                return;
            }

            ContainerProperties containerProperties = new ContainerProperties(desiredTopics.toArray(new String[0]));
            containerProperties.setGroupId(dynamicGroupId);
            containerProperties.setMessageListener((MessageListener<String, Object>) this::processRecord);

            container = new ConcurrentMessageListenerContainer<>(consumerFactory, containerProperties);
            container.setConcurrency(1);
            container.setBeanName("dynamicKafkaEventContainer");
            container.start();

            activeTopics = new HashSet<>(desiredTopics);
            log.info("DynamicKafkaConsumerService: Started listener container successfully for topics: {}", activeTopics);
        } catch (Exception ex) {
            log.error("Failed to refresh topics in DynamicKafkaConsumerService: {}", ex.getMessage(), ex);
        }
    }

    private void processRecord(ConsumerRecord<String, Object> record) {
        String topic = record.topic();
        Object rawVal = record.value();
        String recordKey = record.key();

        log.info("Dynamic Kafka event received: topic={}, key={}, value={}", topic, recordKey, rawVal);

        try {
            Map<String, Object> message = parsePayload(rawVal);
            if (message == null) {
                log.warn("Discarding invalid message received on topic {}: cannot parse to Map", topic);
                return;
            }

            // 1. Resolve eventType
            String eventType = (String) message.get("eventType");
            if (eventType == null || eventType.isBlank()) {
                List<EventDefinition> matchingDefs = eventDefinitionRepository.findByKafkaTopicIgnoreCaseAndActiveTrue(topic);
                if (!matchingDefs.isEmpty()) {
                    eventType = matchingDefs.get(0).getEventKey();
                    log.info("Inferred eventType='{}' from registered topic '{}'", eventType, topic);
                }
            }

            // 2. Resolve payload map
            Map<String, Object> payload;
            if (message.get("payload") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> p = (Map<String, Object>) message.get("payload");
                payload = p;
            } else {
                payload = message;
            }

            // 3. Resolve businessKey
            String businessKey = (String) message.get("businessKey");
            if (businessKey == null || businessKey.isBlank()) {
                if (recordKey != null && !recordKey.isBlank()) {
                    businessKey = recordKey;
                }
            }

            if (eventType == null || eventType.isBlank()) {
                log.warn("Cannot route event from topic {}: missing eventType in payload and no EventDefinition mapped to this topic", topic);
                return;
            }

            log.info("Routing dynamic event to engine: topic={}, eventType={}, businessKey={}, payload={}",
                    topic, eventType, businessKey, payload);

            eventRoutingService.routeEvent(eventType, businessKey, payload);

        } catch (Exception ex) {
            log.error("Failed to process dynamic Kafka message on topic {}: {}", topic, ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Map) {
            return (Map<String, Object>) raw;
        }
        try {
            if (raw instanceof String str) {
                return objectMapper.readValue(str, Map.class);
            }
            if (raw instanceof byte[] bytes) {
                return objectMapper.readValue(bytes, Map.class);
            }
            return objectMapper.convertValue(raw, Map.class);
        } catch (Exception ex) {
            log.error("Failed to parse Kafka record value as JSON Map: {}", ex.getMessage());
            return null;
        }
    }

    private synchronized void stopContainer() {
        if (container != null && container.isRunning()) {
            log.info("Stopping previous DynamicKafkaConsumerService container...");
            container.stop();
            container = null;
        }
    }

    @Override
    public void destroy() {
        stopContainer();
    }

    public Set<String> getActiveTopics() {
        return Collections.unmodifiableSet(activeTopics);
    }
}
