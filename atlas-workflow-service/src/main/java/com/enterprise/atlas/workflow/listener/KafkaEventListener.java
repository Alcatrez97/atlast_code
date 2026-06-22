package com.enterprise.atlas.workflow.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.enterprise.atlas.common.dto.BucketResolutionEvent;
import com.enterprise.atlas.common.dto.CafSubmittedEvent;
import com.enterprise.atlas.common.dto.ExecutionRequestDto;
import com.enterprise.atlas.workflow.repository.ExecutionRepository;
import com.enterprise.atlas.workflow.service.ExecutionService;

@Component
@Profile("!test")
public class KafkaEventListener {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventListener.class);

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private com.enterprise.atlas.workflow.service.BucketResolutionService bucketResolutionService;

    /**
     * Listen to 'caf-lifecycle' topic to start a new workflow execution.
     */
    @KafkaListener(topics = "caf-lifecycle", groupId = "atlas-workflow-group")
    public void handleCafSubmission(CafSubmittedEvent event) {
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
    public void handleBucketResolution(BucketResolutionEvent event) {
        log.info("Received BucketResolutionEvent: instanceId={}, bucketId={}, outcome={}",
                event.getInstanceId(), event.getBucketId(), event.getOutcome());
        
        try {
            bucketResolutionService.resolveBucket(
                    event.getInstanceId(),
                    event.getBucketId(),
                    event.getOutcome(),
                    event.getResolvedBy(),
                    event.getResolutionNotes()
            );
            log.info("Successfully routed bucket resolution for instanceId={}", event.getInstanceId());
        } catch (Exception e) {
            log.error("Failed to process bucket resolution for instance {}: {}", event.getInstanceId(), e.getMessage(), e);
            throw e; // Delegate to Kafka error handler / DLQ
        }
    }
}
