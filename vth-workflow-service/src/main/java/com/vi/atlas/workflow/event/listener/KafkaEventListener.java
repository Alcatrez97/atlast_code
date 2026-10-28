package com.vi.atlas.workflow.event.listener;

import com.vi.atlas.common.dto.BucketResolutionEvent;
import com.vi.atlas.common.dto.CafSubmittedEvent;
import com.vi.atlas.common.dto.ExecutionRequestDto;
import com.vi.atlas.workflow.repository.ExecutionRepository;
import com.vi.atlas.workflow.service.BucketResolutionService;
import com.vi.atlas.workflow.service.ExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class KafkaEventListener {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventListener.class);

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private BucketResolutionService bucketResolutionService;

    @Autowired
    private com.vi.atlas.workflow.repository.WorkflowInstanceRepository workflowInstanceRepository;

    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper()
                    .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @KafkaListener(topics = "${kafka.topics.lifecycle:caf-lifecycle}", groupId = "atlas-workflow-group")
    public void handleWorkflowTrigger(@org.springframework.messaging.handler.annotation.Payload Object rawPayload) {
        Object payload = rawPayload;
        if (payload instanceof org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> cr) {
            payload = cr.value();
        }

        java.util.Map<String, Object> payloadMap = new java.util.HashMap<>();
        CafSubmittedEvent event = null;

        if (payload instanceof CafSubmittedEvent cse) {
            event = cse;
            try {
                payloadMap = OBJECT_MAPPER.convertValue(cse, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
            } catch (Exception ignored) {}
        } else if (payload instanceof java.util.Map<?, ?> m) {
            for (java.util.Map.Entry<?, ?> entry : m.entrySet()) {
                if (entry.getKey() != null) {
                    payloadMap.put(entry.getKey().toString(), entry.getValue());
                }
            }
            try {
                event = OBJECT_MAPPER.convertValue(payloadMap, CafSubmittedEvent.class);
            } catch (Exception ignored) {}
        } else if (payload instanceof String str && !str.isBlank()) {
            try {
                payloadMap = OBJECT_MAPPER.readValue(str, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
            } catch (Exception ignored) {}
            try {
                event = OBJECT_MAPPER.readValue(str, CafSubmittedEvent.class);
            } catch (Exception ex) {
                log.warn("Failed to deserialize CafSubmittedEvent directly from string: {}", ex.getMessage());
            }
        } else if (payload != null) {
            try {
                payloadMap = OBJECT_MAPPER.convertValue(payload, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
            } catch (Exception ignored) {}
            try {
                event = OBJECT_MAPPER.convertValue(payload, CafSubmittedEvent.class);
            } catch (Exception ignored) {}
        }

        if (event == null) {
            event = new CafSubmittedEvent();
        }

        // Build executionContext aggregating event.context, inner context map, additionalProperties, and top-level fields
        java.util.Map<String, Object> executionContext = new java.util.HashMap<>();
        if (event.getContext() != null) {
            executionContext.putAll(event.getContext());
        }
        if (payloadMap.get("context") instanceof java.util.Map<?, ?> innerCtx) {
            for (java.util.Map.Entry<?, ?> e : innerCtx.entrySet()) {
                if (e.getKey() != null) {
                    executionContext.put(e.getKey().toString(), e.getValue());
                }
            }
        }
        if (event.getAdditionalProperties() != null) {
            executionContext.putAll(event.getAdditionalProperties());
        }
        for (java.util.Map.Entry<String, Object> entry : payloadMap.entrySet()) {
            String key = entry.getKey();
            if (!"context".equalsIgnoreCase(key) && !"additionalProperties".equalsIgnoreCase(key)) {
                executionContext.putIfAbsent(key, entry.getValue());
            }
        }

        // Resolve circleId across all variants in executionContext & payloadMap
        Object resolvedCircleId = null;
        for (String cKey : new String[] {
                "circleId", "circle_id", "circleid", "CircleId", "CIRCLE_ID",
                "circle-id", "circleID", "CircleID", "CIRCLEID"
        }) {
            if (executionContext.get(cKey) != null) {
                resolvedCircleId = executionContext.get(cKey);
                break;
            }
            if (payloadMap.get(cKey) != null) {
                resolvedCircleId = payloadMap.get(cKey);
                break;
            }
        }
        if (resolvedCircleId == null && event.getCircleId() != null) {
            resolvedCircleId = event.getCircleId();
        }
        if (resolvedCircleId != null) {
            executionContext.put("circleId", resolvedCircleId);
        }

        // Resolve cafId / businessKey / contextId
        Object resolvedCafId = null;
        for (String k : new String[] { "cafId", "caf_id", "cafid", "CafId", "CAF_ID" }) {
            if (executionContext.get(k) != null) {
                resolvedCafId = executionContext.get(k);
                break;
            }
            if (payloadMap.get(k) != null) {
                resolvedCafId = payloadMap.get(k);
                break;
            }
        }
        if (resolvedCafId == null && event.getCafId() != null) {
            resolvedCafId = event.getCafId();
        }
        if (resolvedCafId != null) {
            executionContext.putIfAbsent("cafId", resolvedCafId);
        }

        String effectiveContextId = event.getContextId() != null ? event.getContextId() : (resolvedCafId != null ? String.valueOf(resolvedCafId) : event.getCafId());
        String businessKey = event.getBusinessKey() != null ? event.getBusinessKey() : (resolvedCafId != null ? String.valueOf(resolvedCafId) : effectiveContextId);

        String workflowKey = event.getWorkflowKey();
        if (workflowKey == null || workflowKey.isBlank()) {
            if (payloadMap.get("workflowKey") != null) workflowKey = String.valueOf(payloadMap.get("workflowKey"));
            else if (payloadMap.get("workflow_key") != null) workflowKey = String.valueOf(payloadMap.get("workflow_key"));
            else if (executionContext.get("workflowKey") != null) workflowKey = String.valueOf(executionContext.get("workflowKey"));
            else workflowKey = "order_processing";
        }

        log.info("Received Kafka workflow trigger: workflowKey={}, contextId={}, businessKey={}, circleId={}",
                workflowKey, effectiveContextId, businessKey, resolvedCircleId);

        try {
            ExecutionRequestDto request = new ExecutionRequestDto();
            request.setContextId(effectiveContextId);
            request.setBusinessKey(businessKey);
            request.setCircleId(resolvedCircleId);
            request.setContext(executionContext);

            executionService.execute(workflowKey, request);
            log.info("Asynchronously triggered workflow execution for key={} and contextId={}, circleId={}",
                    workflowKey, effectiveContextId, resolvedCircleId);
        } catch (Exception e) {
            log.error("Failed to execute workflow for key={} and contextId={}: {}",
                    workflowKey, effectiveContextId, e.getMessage(), e);
            throw e; // Delegate to Kafka error handler / DLQ
        }
    }

    /**
     * Listen to 'workflow-bucket-resolution' topic to resolve bucket manual tasks.
     */
    @KafkaListener(topics = "workflow-bucket-resolution", groupId = "atlas-workflow-group")
    public void handleBucketResolution(@org.springframework.messaging.handler.annotation.Payload Object rawPayload,
                                       @org.springframework.messaging.handler.annotation.Header(value = "X-User-Id", required = false) String userId) {
        Object payload = rawPayload;
        if (payload instanceof org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> cr) {
            payload = cr.value();
        }
        BucketResolutionEvent event;
        if (payload instanceof BucketResolutionEvent bre) {
            event = bre;
        } else if (payload instanceof String str) {
            try {
                event = OBJECT_MAPPER.readValue(str, BucketResolutionEvent.class);
            } catch (Exception ex) {
                throw new IllegalArgumentException("Failed to deserialize BucketResolutionEvent JSON string", ex);
            }
        } else {
            event = OBJECT_MAPPER.convertValue(payload, BucketResolutionEvent.class);
        }
        log.info("Received BucketResolutionEvent: instanceId={}, businessKey={}, bucketId={}, outcome={}",
                event.getInstanceId(), event.getBusinessKey(), event.getBucketId(), event.getOutcome());
        
        try {
            // Trust the Identity Header if provided (Identity Propagation over Kafka)
            String resolvedBy = (userId != null && !userId.isBlank()) ? userId : event.getResolvedBy();
            
            String instanceId = event.getInstanceId();
            if (instanceId == null || instanceId.isBlank()) {
                String key = event.getBusinessKey() != null ? event.getBusinessKey() : event.getCafId();
                if (key != null && !key.isBlank()) {
                    instanceId = workflowInstanceRepository.findFirstByBusinessKeyOrderByCreatedAtDesc(key)
                            .map(com.vi.atlas.workflow.entity.WorkflowInstance::getId)
                            .orElse(null);
                }
            }
            if (instanceId == null || instanceId.isBlank()) {
                throw new IllegalArgumentException("Cannot resolve bucket without valid instanceId or businessKey/cafId. Provided key: " + event.getBusinessKey());
            }

            bucketResolutionService.resolveBucket(
                    instanceId,
                    event.getBucketId(),
                    event.getOutcome(),
                    resolvedBy,
                    event.getResolutionNotes()
            );
            log.info("Successfully routed bucket resolution for instanceId={} (businessKey={})", instanceId, event.getBusinessKey());
        } catch (Exception e) {
            log.error("Failed to process bucket resolution for event: {}", event, e);
            throw e; // Delegate to Kafka error handler / DLQ
        }
    }
}
