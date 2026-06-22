package com.enterprise.atlas.workflow.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MqPublishCommand implements WorkflowCommand {

    private static final Logger log = LoggerFactory.getLogger(MqPublishCommand.class);

    @Autowired(required = false)
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public String getCommandType() {
        return "MQ";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> input) throws Exception {
        String topic = (String) input.get("topic");
        if (topic == null || topic.isBlank()) {
            topic = (String) input.get("kafkaTopic");
        }
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("MQ command requires a non-blank 'topic' or 'kafkaTopic' parameter.");
        }

        String key = (String) input.get("key");
        if (key == null) {
            key = (String) input.get("businessKey");
        }

        // Build message payload (excluding configuration parameters)
        Map<String, Object> payload = new java.util.HashMap<>(input);
        payload.remove("topic");
        payload.remove("kafkaTopic");
        payload.remove("key");
        payload.remove("commandType");
        payload.remove("inputMapping");
        payload.remove("outputMapping");

        log.info("Executing MqPublishCommand: topic={}, key={}, payload={}", topic, key, payload);

        if (kafkaTemplate != null) {
            kafkaTemplate.send(topic, key, payload);
            log.info("Successfully published message to Kafka topic '{}'", topic);
        } else {
            log.warn("KafkaTemplate not available. Skipped publishing message to topic '{}'", topic);
        }

        return Map.of("status", "PUBLISHED", "topic", topic);
    }
}
