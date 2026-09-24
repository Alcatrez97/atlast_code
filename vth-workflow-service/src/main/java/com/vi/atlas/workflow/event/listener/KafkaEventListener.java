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

    /**
     * Listen to 'caf-lifecycle' topic to start a new workflow execution.
     */
    @KafkaListener(topics = "caf-lifecycle", groupId = "atlas-workflow-group")
    public void handleCafSubmission(@org.springframework.messaging.handler.annotation.Payload Object rawPayload) {
        Object payload = rawPayload;
        if (payload instanceof org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> cr) {
            payload = cr.value();
        }
        CafSubmittedEvent event;
        if (payload instanceof CafSubmittedEvent cse) {
            event = cse;
        } else if (payload instanceof String str) {
            try {
                event = new com.fasterxml.jackson.databind.ObjectMapper().readValue(str, CafSubmittedEvent.class);
            } catch (Exception ex) {
                throw new IllegalArgumentException("Failed to deserialize CafSubmittedEvent JSON string", ex);
            }
        } else {
            event = new com.fasterxml.jackson.databind.ObjectMapper().convertValue(payload, CafSubmittedEvent.class);
        }
        log.info("Received CafSubmittedEvent: cafId={}, workflowKey={}", event.getCafId(), event.getWorkflowKey());
        try {
            ExecutionRequestDto request = new ExecutionRequestDto();
            request.setContextId(event.getCafId());
            request.setContext(event.getContext());
            
            String workflowKey = event.getWorkflowKey() != null ? event.getWorkflowKey() : "order_processing";
            executionService.execute(workflowKey, request);
            log.info("Asynchronously triggered workflow execution for key={} and contextId={}", workflowKey, event.getCafId());
        } catch (Exception e) {
            log.error("Failed to execute workflow for CAF {}: {}", event.getCafId(), e.getMessage(), e);
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
                event = new com.fasterxml.jackson.databind.ObjectMapper().readValue(str, BucketResolutionEvent.class);
            } catch (Exception ex) {
                throw new IllegalArgumentException("Failed to deserialize BucketResolutionEvent JSON string", ex);
            }
        } else {
            event = new com.fasterxml.jackson.databind.ObjectMapper().convertValue(payload, BucketResolutionEvent.class);
        }
        log.info("Received BucketResolutionEvent: instanceId={}, bucketId={}, outcome={}",
                event.getInstanceId(), event.getBucketId(), event.getOutcome());
        
        try {
            // Trust the Identity Header if provided (Identity Propagation over Kafka)
            String resolvedBy = (userId != null && !userId.isBlank()) ? userId : event.getResolvedBy();
            
            bucketResolutionService.resolveBucket(
                    event.getInstanceId(),
                    event.getBucketId(),
                    event.getOutcome(),
                    resolvedBy,
                    event.getResolutionNotes()
            );
            log.info("Successfully routed bucket resolution for instanceId={}", event.getInstanceId());
        } catch (Exception e) {
            log.error("Failed to process bucket resolution for instance {}: {}", event.getInstanceId(), e.getMessage(), e);
            throw e; // Delegate to Kafka error handler / DLQ
        }
    }
}
