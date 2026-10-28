package com.vi.atlas.workflow.service.command.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vi.atlas.workflow.dto.CommandParameterDto;
import com.vi.atlas.workflow.entity.EventDefinition;
import com.vi.atlas.workflow.repository.EventDefinitionRepository;
import com.vi.atlas.workflow.service.EventRoutingService;
import com.vi.atlas.workflow.service.command.WorkflowCommand;
import com.vi.atlas.workflow.service.traversal.SpelEvaluator;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class EmitEventCommand implements WorkflowCommand {

    private static final Logger log = LoggerFactory.getLogger(EmitEventCommand.class);
    private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    @Lazy
    private EventRoutingService eventRoutingService;

    @Autowired(required = false)
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired(required = false)
    private EventDefinitionRepository eventDefinitionRepository;

    @Override
    public String getCommandType() {
        return "EMIT_EVENT";
    }

    @Override
    public String getDisplayName() {
        return "Emit Domain Event";
    }

    @Override
    public String getDescription() {
        return "Broadcasts an internal domain event to active event subscribers and publishes to Kafka if configured.";
    }

    @Override
    public String getCategory() {
        return "Event";
    }

    @Override
    public List<CommandParameterDto> getParameters() {
        CommandParameterDto eventParam = new CommandParameterDto(
                "eventType",
                "Event Key / Type",
                "select",
                true,
                "",
                "Identifier of the domain event to broadcast"
        );
        eventParam.setDataSource("EVENTS");

        return List.of(
                eventParam,
                new CommandParameterDto(
                        "topic",
                        "Kafka Topic (Optional)",
                        "text",
                        false,
                        "",
                        "Target Kafka topic (defaults to registered event topic if available)"
                ),
                new CommandParameterDto(
                        "key",
                        "Partition Key (Optional)",
                        "text",
                        false,
                        "",
                        "Kafka partition key (e.g. cafId or ${context.cafId})"
                ),
                new CommandParameterDto(
                        "consumerGroup",
                        "Consumer Group / Target Listener (Optional)",
                        "text",
                        false,
                        "",
                        "Target downstream consumer group or listener identifier"
                ),
                new CommandParameterDto(
                        "headers",
                        "Kafka Headers / Metadata (JSON)",
                        "json",
                        false,
                        "{}",
                        "Metadata headers attached to the Kafka record"
                ),
                new CommandParameterDto(
                        "payloadMapping",
                        "Payload Mapping (JSON / Key-Value)",
                        "json",
                        false,
                        "{}",
                        "JSON mapping SpEL expressions or context keys to outbound event payload"
                )
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> input) throws Exception {
        String eventKey = getStringParam(input, "eventKey", "eventType");
        if (eventKey == null) {
            throw new IllegalArgumentException("EMIT_EVENT command requires an 'eventKey' or 'eventType' parameter.");
        }

        String rawKey = getStringParam(input, "key", "partitionKey", "businessKey", "cafId", "_instanceId");
        String bKey = resolvePartitionKey(rawKey, input);

        // Build event payload
        Map<String, Object> payload;
        Object payloadMappingObj = input.get("payloadMapping");
        if (payloadMappingObj == null) {
            payloadMappingObj = input.get("inputMapping");
        }

        if (payloadMappingObj instanceof Map<?, ?> map && !map.isEmpty()) {
            payload = new HashMap<>();
            applyPayloadOrSpelMapping(payloadMappingObj, input, payload);
        } else if (payloadMappingObj instanceof String str && !str.isBlank() && !str.trim().equals("{}")) {
            payload = new HashMap<>();
            applyPayloadOrSpelMapping(str, input, payload);
        } else {
            payload = extractCleanPayload(input, "eventKey", "eventType", "businessKey", "cafId",
                    "topic", "kafkaTopic", "key", "partitionKey", "consumerGroup", "targetGroup", "headers", "metadata");
        }

        log.info("Executing EMIT_EVENT command: eventKey={}, businessKey={}, payload={}", eventKey, bKey, payload);

        // 1. Internal domain routing
        try {
            eventRoutingService.routeEvent(eventKey, bKey, payload);
            log.info("Successfully emitted event '{}' for businessKey '{}'", eventKey, bKey);
        } catch (Exception e) {
            log.error("Failed to emit event key={} via EMIT_EVENT command: {}", eventKey, e.getMessage(), e);
            throw e;
        }

        // 2. Publish to Kafka if topic is configured on the node or the event definition
        String kafkaTopic = getStringParam(input, "topic", "kafkaTopic");
        if ((kafkaTopic == null || kafkaTopic.isBlank()) && eventDefinitionRepository != null) {
            kafkaTopic = eventDefinitionRepository.findByEventKey(eventKey)
                    .map(EventDefinition::getKafkaTopic)
                    .orElse(null);
        }

        if (kafkaTopic != null && !kafkaTopic.isBlank() && kafkaTemplate != null) {
            try {
                String consumerGroup = getStringParam(input, "consumerGroup", "targetGroup", "targetConsumerGroup");
                Map<String, Object> headersMap = extractHeaders(input);
                if (consumerGroup != null && !consumerGroup.isBlank()) {
                    headersMap.put("consumerGroup", consumerGroup);
                    headersMap.put("X-Consumer-Group", consumerGroup);
                }
                headersMap.put("eventType", eventKey);

                if (headersMap.isEmpty()) {
                    kafkaTemplate.send(kafkaTopic, bKey, payload);
                } else {
                    List<Header> recordHeaders = new ArrayList<>();
                    headersMap.forEach((hk, hv) -> {
                        if (hk != null && hv != null) {
                            recordHeaders.add(new RecordHeader(hk, String.valueOf(hv).getBytes(StandardCharsets.UTF_8)));
                        }
                    });
                    ProducerRecord<String, Object> record = new ProducerRecord<>(kafkaTopic, null, bKey, payload, recordHeaders);
                    kafkaTemplate.send(record);
                }
                log.info("Successfully emitted event to Kafka topic '{}' with key '{}'", kafkaTopic, bKey);
            } catch (Exception ke) {
                log.warn("Failed to publish event to Kafka topic '{}': {}", kafkaTopic, ke.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("status", "EMITTED");
        result.put("eventKey", eventKey);
        if (bKey != null) result.put("key", bKey);
        if (kafkaTopic != null) result.put("kafkaTopic", kafkaTopic);
        return result;
    }

    @SuppressWarnings("unchecked")
    private String resolvePartitionKey(String rawKey, Map<String, Object> input) {
        if (rawKey == null || rawKey.isBlank()) {
            return getStringParam(input, "cafId", "_instanceId", "businessKey");
        }
        String expr = rawKey.trim();
        if (expr.startsWith("${") && expr.endsWith("}")) {
            expr = expr.substring(2, expr.length() - 1).trim();
        }

        Map<String, Object> globalContext = (Map<String, Object>) input.get("_context");
        if (globalContext != null && globalContext.containsKey(expr)) {
            Object val = globalContext.get(expr);
            if (val != null) return String.valueOf(val);
        }
        if (input.containsKey(expr)) {
            Object val = input.get(expr);
            if (val != null) return String.valueOf(val);
        }

        // Evaluate SpEL expression if applicable
        if (globalContext != null && (expr.contains(".") || expr.contains("["))) {
            try {
                EvaluationContext spelCtx = SpelEvaluator.createEvaluationContext(globalContext);
                Object val = SPEL_PARSER.parseExpression(expr).getValue(spelCtx);
                if (val != null) return String.valueOf(val);
            } catch (Exception ignored) {}
        }
        return rawKey;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractHeaders(Map<String, Object> input) {
        Map<String, Object> headersMap = new HashMap<>();
        Object headersObj = input.get("headers");
        if (headersObj == null) {
            headersObj = input.get("metadata");
        }

        if (headersObj instanceof Map<?, ?> m) {
            m.forEach((k, v) -> {
                if (k != null && v != null) headersMap.put(String.valueOf(k), v);
            });
        } else if (headersObj instanceof String s && !s.isBlank()) {
            try {
                Map<?, ?> parsed = OBJECT_MAPPER.readValue(s, Map.class);
                parsed.forEach((k, v) -> {
                    if (k != null && v != null) headersMap.put(String.valueOf(k), v);
                });
            } catch (Exception e) {
                log.warn("Could not parse headers JSON in EmitEventCommand: {}", e.getMessage());
            }
        }
        return headersMap;
    }
}
