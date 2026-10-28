package com.vi.atlas.workflow.service.command.impl;
 
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vi.atlas.workflow.dto.CommandParameterDto;
import com.vi.atlas.workflow.service.command.WorkflowCommand;
import com.vi.atlas.workflow.service.traversal.SpelEvaluator;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
 
import java.nio.charset.StandardCharsets;
import java.util.*;
 
@Component
public class MqPublishCommand implements WorkflowCommand {
 
     private static final Logger log = LoggerFactory.getLogger(MqPublishCommand.class);
     private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();
     private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
 
     @Autowired(required = false)
     private KafkaTemplate<String, Object> kafkaTemplate;
 
     @Override
     public String getCommandType() {
         return "MQ";
     }
 
     @Override
     public String getDisplayName() {
         return "Publish to Kafka / MQ";
     }
 
     @Override
     public String getDescription() {
         return "Publishes an asynchronous message payload to an Apache Kafka or MQ topic.";
     }
 
     @Override
     public String getCategory() {
         return "Messaging";
     }
 
     @Override
     public List<CommandParameterDto> getParameters() {
         return List.of(
                 new CommandParameterDto(
                         "topic",
                         "Kafka / Queue Topic",
                         "text",
                         true,
                         "",
                         "Target messaging topic or queue name"
                 ),
                 new CommandParameterDto(
                         "key",
                         "Partition Key (Optional)",
                         "text",
                         false,
                         "",
                         "Message partition key (e.g. cafId or ${context.cafId})"
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
                         "Mapping of context variables to outgoing payload properties"
                 )
         );
     }
 
     @Override
     public boolean isExternalIo() {
         return true;
     }
 
     @Override
     @SuppressWarnings("unchecked")
     public Map<String, Object> execute(Map<String, Object> input) throws Exception {
         String topic = getStringParam(input, "topic", "kafkaTopic");
         if (topic == null || topic.isBlank()) {
             throw new IllegalArgumentException("MQ command requires a non-blank 'topic' or 'kafkaTopic' parameter.");
         }
 
         String rawKey = getStringParam(input, "key", "partitionKey", "businessKey");
         String key = resolvePartitionKey(rawKey, input);
 
         String consumerGroup = getStringParam(input, "consumerGroup", "targetGroup", "targetConsumerGroup");
 
         // Build message payload (using payloadMapping if provided, or clean payload)
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
             payload = extractCleanPayload(input, "topic", "kafkaTopic", "key", "partitionKey", "businessKey",
                     "consumerGroup", "targetGroup", "targetConsumerGroup", "headers", "metadata", "payloadMapping", "inputMapping");
         }
 
         // Build Kafka Record Headers
         Map<String, Object> headersMap = extractHeaders(input);
         if (consumerGroup != null && !consumerGroup.isBlank()) {
             headersMap.put("consumerGroup", consumerGroup);
             headersMap.put("X-Consumer-Group", consumerGroup);
         }
 
         log.info("Executing MqPublishCommand: topic={}, key={}, consumerGroup={}, payload={}, headers={}",
                 topic, key, consumerGroup, payload, headersMap);
 
         if (kafkaTemplate != null) {
             if (headersMap.isEmpty()) {
                 kafkaTemplate.send(topic, key, payload);
             } else {
                 List<Header> recordHeaders = new ArrayList<>();
                 headersMap.forEach((hk, hv) -> {
                     if (hk != null && hv != null) {
                         recordHeaders.add(new RecordHeader(hk, String.valueOf(hv).getBytes(StandardCharsets.UTF_8)));
                     }
                 });
                 ProducerRecord<String, Object> record = new ProducerRecord<>(topic, null, key, payload, recordHeaders);
                 kafkaTemplate.send(record);
             }
             log.info("Successfully published message to Kafka topic '{}' with key '{}'", topic, key);
         } else {
             log.warn("KafkaTemplate not available. Skipped publishing message to topic '{}'", topic);
         }
 
         Map<String, Object> result = new HashMap<>();
         result.put("status", "PUBLISHED");
         result.put("topic", topic);
         if (key != null) result.put("key", key);
         if (consumerGroup != null) result.put("consumerGroup", consumerGroup);
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
                 log.warn("Could not parse headers JSON in MqPublishCommand: {}", e.getMessage());
             }
         }
         return headersMap;
     }
}
