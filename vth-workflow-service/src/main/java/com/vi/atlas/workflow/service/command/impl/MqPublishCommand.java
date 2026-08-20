package com.vi.atlas.workflow.service.command.impl;

import com.vi.atlas.workflow.service.command.WorkflowCommand;
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
    public boolean isExternalIo() {
        return true;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> input) throws Exception {
        String topic = getStringParam(input, "topic", "kafkaTopic");
        if (topic == null) {
            throw new IllegalArgumentException("MQ command requires a non-blank 'topic' or 'kafkaTopic' parameter.");
        }

        String key = getStringParam(input, "key", "businessKey");

        // Build message payload (excluding configuration parameters)
        Map<String, Object> payload = extractCleanPayload(input, "topic", "kafkaTopic", "key", "businessKey");

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
