package com.vi.atlas.workflow.event.listener;

import com.vi.atlas.common.context.WorkflowContext;
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

    @Autowired
    private com.vi.atlas.workflow.repository.EntityRevertRepository entityRevertRepository;

    @Autowired
    private com.vi.atlas.workflow.repository.RevertStatusRepository revertStatusRepository;

    @Autowired(required = false)
    private com.vi.atlas.workflow.repository.BucketExecutionRepository bucketExecutionRepository;

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

        // Canonical context normalization via WorkflowContext
        WorkflowContext wfCtx = WorkflowContext.from(executionContext);
        if (event.getCircleId() != null) {
            wfCtx.asMap().putIfAbsent(WorkflowContext.KEY_CIRCLE_ID, event.getCircleId());
        }
        if (event.getCafId() != null) {
            wfCtx.asMap().putIfAbsent(WorkflowContext.KEY_CAF_ID, event.getCafId());
        }
        wfCtx.normalize();

        Integer resolvedCircleId = wfCtx.getCircleId();
        String effectiveContextId = event.getContextId() != null ? event.getContextId() : wfCtx.getContextId();
        String businessKey = event.getBusinessKey() != null ? event.getBusinessKey() : wfCtx.getBusinessKey();

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

        java.util.Map<String, Object> payloadMap = new java.util.HashMap<>();
        BucketResolutionEvent event = null;

        if (payload instanceof BucketResolutionEvent bre) {
            event = bre;
            try {
                payloadMap = OBJECT_MAPPER.convertValue(bre, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
            } catch (Exception ignored) {}
        } else if (payload instanceof java.util.Map<?, ?> m) {
            for (java.util.Map.Entry<?, ?> entry : m.entrySet()) {
                if (entry.getKey() != null) {
                    payloadMap.put(entry.getKey().toString(), entry.getValue());
                }
            }
            try {
                event = OBJECT_MAPPER.convertValue(payloadMap, BucketResolutionEvent.class);
            } catch (Exception ignored) {}
        } else if (payload instanceof String str && !str.isBlank()) {
            try {
                payloadMap = OBJECT_MAPPER.readValue(str, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
            } catch (Exception ignored) {}
            try {
                event = OBJECT_MAPPER.readValue(str, BucketResolutionEvent.class);
            } catch (Exception ex) {
                log.warn("Failed to deserialize BucketResolutionEvent directly from string: {}", ex.getMessage());
            }
        }

        if (event == null) {
            event = new BucketResolutionEvent();
        }

        WorkflowContext wfCtx = WorkflowContext.from(payloadMap);
        Long cafId = wfCtx.getCafId();
        if (cafId == null && event.getCafId() != null) {
            cafId = WorkflowContext.parseStrictNumeric(event.getCafId());
        }

        String businessKey = event.getBusinessKey();
        if (businessKey == null || businessKey.isBlank()) {
            businessKey = wfCtx.getBusinessKey();
        }
        if (businessKey == null || businessKey.isBlank()) {
            if (cafId != null) businessKey = String.valueOf(cafId);
            else if (wfCtx.getContextId() != null) businessKey = wfCtx.getContextId();
        }

        String bucketId = event.getBucketId();
        if (bucketId == null || bucketId.isBlank()) {
            if (payloadMap.get("bucket_id") != null) bucketId = String.valueOf(payloadMap.get("bucket_id"));
            else if (payloadMap.get("bucketId") != null) bucketId = String.valueOf(payloadMap.get("bucketId"));
        }

        String outcome = event.getOutcome();
        if (outcome == null || outcome.isBlank()) {
            if (payloadMap.get("outcome") != null) outcome = String.valueOf(payloadMap.get("outcome"));
            else if (payloadMap.get("status") != null) outcome = String.valueOf(payloadMap.get("status"));
            else outcome = "Accept";
        }

        String notes = event.getResolutionNotes();
        if (notes == null && payloadMap.get("resolutionNotes") != null) {
            notes = String.valueOf(payloadMap.get("resolutionNotes"));
        } else if (notes == null && payloadMap.get("resolution_notes") != null) {
            notes = String.valueOf(payloadMap.get("resolution_notes"));
        }

        String instanceId = event.getInstanceId();
        if (instanceId != null && instanceId.isBlank()) {
            instanceId = null;
        }

        log.info("Received Kafka bucket resolution payload: instanceId={}, businessKey={}, cafId={}, bucketId={}, outcome={}",
                instanceId, businessKey, cafId, bucketId, outcome);

        try {
            // 1. Resolve instanceId from active EntityRevert table if instanceId is absent
            if (instanceId == null && cafId != null) {
                java.util.List<com.vi.atlas.workflow.entity.EntityRevert> reverts = entityRevertRepository.findByCafId(cafId);
                for (com.vi.atlas.workflow.entity.EntityRevert er : reverts) {
                    if (er.isActive() && (bucketId == null || bucketId.equalsIgnoreCase(er.getBucketId()))
                            && !"COMPLETED".equalsIgnoreCase(er.getRevertStatus())
                            && !"REVERTED".equalsIgnoreCase(er.getRevertStatus())) {
                        instanceId = er.getWorkflowInstanceId();
                        if (bucketId == null) {
                            bucketId = er.getBucketId();
                        }
                        log.info("Resolved instanceId={} from active EntityRevert for cafId={}, bucketId={}",
                                instanceId, cafId, bucketId);
                        break;
                    }
                }
            }

            // 2. Resolve instanceId from WorkflowInstance repository by businessKey (prefer WAITING status)
            if (instanceId == null && businessKey != null && !businessKey.isBlank()) {
                instanceId = workflowInstanceRepository.findFirstByBusinessKeyAndStatusOrderByCreatedAtDesc(
                        businessKey, com.vi.atlas.workflow.entity.WorkflowInstanceStatus.WAITING)
                        .map(com.vi.atlas.workflow.entity.WorkflowInstance::getId)
                        .orElse(null);

                if (instanceId == null) {
                    instanceId = workflowInstanceRepository.findFirstByBusinessKeyOrderByCreatedAtDesc(businessKey)
                            .map(com.vi.atlas.workflow.entity.WorkflowInstance::getId)
                            .orElse(null);
                }
                if (instanceId != null) {
                    log.info("Resolved instanceId={} from WorkflowInstance by businessKey={}", instanceId, businessKey);
                }
            }

            // 3. Resolve instanceId from pending RevertStatus by formId / businessKey
            if (instanceId == null && businessKey != null && !businessKey.isBlank()) {
                java.util.List<com.vi.atlas.workflow.entity.RevertStatus> formReverts =
                        revertStatusRepository.findByFormIdOrderByCreatedAtDesc(businessKey);
                for (com.vi.atlas.workflow.entity.RevertStatus rs : formReverts) {
                    if (com.vi.atlas.workflow.entity.RevertStepStatus.PENDING.equals(rs.getStatus())
                            && (bucketId == null || bucketId.equalsIgnoreCase(rs.getBucketId()))) {
                        instanceId = rs.getWorkflowInstanceId();
                        if (bucketId == null) bucketId = rs.getBucketId();
                        log.info("Resolved instanceId={} from pending RevertStatus for formId={}, bucketId={}",
                                instanceId, businessKey, bucketId);
                        break;
                    }
                }
            }

            // 4. If bucketId is still unknown, attempt to resolve from pending BucketExecution
            if ((bucketId == null || bucketId.isBlank()) && instanceId != null && bucketExecutionRepository != null) {
                java.util.List<com.vi.atlas.workflow.entity.BucketExecution> pendingBuckets =
                        bucketExecutionRepository.findByWorkflowInstanceId(instanceId);
                for (com.vi.atlas.workflow.entity.BucketExecution be : pendingBuckets) {
                    if (!"RESOLVED".equalsIgnoreCase(be.getStatus())) {
                        bucketId = be.getBucketId();
                        log.info("Resolved pending bucketId={} from BucketExecution for instanceId={}", bucketId, instanceId);
                        break;
                    }
                }
            }

            if (instanceId == null || instanceId.isBlank()) {
                throw new IllegalArgumentException(String.format(
                        "Cannot resolve bucket without matching workflow instance. Provided identifiers: businessKey='%s', cafId='%s', bucketId='%s'",
                        businessKey, cafId, bucketId));
            }

            if (bucketId == null || bucketId.isBlank()) {
                throw new IllegalArgumentException(String.format(
                        "Cannot resolve bucket without valid bucketId for instanceId='%s', businessKey='%s'",
                        instanceId, businessKey));
            }

            // Normalize outcome prefix (e.g. 'A2Accept' -> 'Accept')
            if (outcome != null && outcome.startsWith(bucketId) && outcome.length() > bucketId.length()) {
                outcome = outcome.substring(bucketId.length());
            }

            // Trust the Identity Header if provided (Identity Propagation over Kafka)
            String resolvedBy = (userId != null && !userId.isBlank()) ? userId : event.getResolvedBy();
            if (resolvedBy == null || resolvedBy.isBlank()) {
                if (payloadMap.get("resolvedBy") != null) resolvedBy = String.valueOf(payloadMap.get("resolvedBy"));
                else if (payloadMap.get("resolved_by") != null) resolvedBy = String.valueOf(payloadMap.get("resolved_by"));
                else resolvedBy = "ExternalApprovalSystem";
            }

            bucketResolutionService.resolveBucket(
                    instanceId,
                    bucketId,
                    outcome,
                    resolvedBy,
                    notes
            );
            log.info("Successfully routed bucket resolution for instanceId={} (businessKey={}, bucketId={})",
                    instanceId, businessKey, bucketId);
        } catch (Exception e) {
            log.error("Failed to process bucket resolution for payload: {}", rawPayload, e);
            throw e; // Delegate to Kafka error handler / DLQ
        }
    }
}
