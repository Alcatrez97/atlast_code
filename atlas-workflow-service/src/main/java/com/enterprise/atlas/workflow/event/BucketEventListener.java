package com.enterprise.atlas.workflow.event;

import com.enterprise.atlas.workflow.entity.BucketExecution;
import com.enterprise.atlas.workflow.repository.ExecutionRepository;
import com.enterprise.atlas.workflow.repository.RevertStatusRepository;
import com.enterprise.atlas.workflow.service.ExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class BucketEventListener {

    private static final Logger log = LoggerFactory.getLogger(BucketEventListener.class);

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private RevertStatusRepository revertStatusRepository;

    @Autowired(required = false)
    private KafkaTemplate<String, Object> kafkaTemplate;

    @EventListener
    public void handleBucketReady(BucketReadySpringEvent event) {
        BucketExecution bex = event.getBucketExecution();
        if (kafkaTemplate == null) {
            log.warn("KafkaTemplate not available. Skipping publishing BucketReadyEvent for bucketId={}", bex.getBucketId());
            return;
        }
        log.info("Publishing BucketReadyEvent to Kafka for bucketId={}, instanceId={}", bex.getBucketId(), bex.getInstanceId());
        try {
            // Find contextId (cafId) associated with this execution
            String contextId = null;
            if (bex.getExecutionLogId() != null) {
                Optional<com.enterprise.atlas.workflow.entity.ExecutionLog> logOpt = executionRepository.findById(bex.getExecutionLogId());
                if (logOpt.isPresent()) {
                    contextId = logOpt.get().getContextId();
                }
            }
            if (contextId == null) {
                contextId = bex.getExecutionLogId(); // fallback
            }

            // Load parent dependencies
            java.util.List<String> dependencies = new java.util.ArrayList<>();
            if (bex.getInstanceId() != null && bex.getBucketId() != null) {
                Optional<com.enterprise.atlas.workflow.entity.RevertStatus> rsOpt = revertStatusRepository
                        .findByWorkflowInstanceIdAndBucketIdAndStatus(bex.getInstanceId(), bex.getBucketId(), com.enterprise.atlas.workflow.entity.RevertStepStatus.PENDING);
                if (rsOpt.isPresent() && rsOpt.get().getDependencyBucketIds() != null) {
                    try {
                        dependencies = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                                rsOpt.get().getDependencyBucketIds(),
                                new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {}
                        );
                    } catch (Exception e) {
                        // ignore/fallback
                    }
                }
            }

            com.enterprise.atlas.common.dto.BucketReadyEvent kafkaEvent = new com.enterprise.atlas.common.dto.BucketReadyEvent(
                    bex.getInstanceId(),
                    contextId,
                    bex.getBucketId(),
                    bex.getBucketName(),
                    bex.getPriority(),
                    bex.getSlaHours(),
                    dependencies
            );

            kafkaTemplate.send("workflow-bucket-tasks", bex.getBucketId(), kafkaEvent);
            log.info("Successfully published BucketReadyEvent to Kafka on topic 'workflow-bucket-tasks' for bucketId={}", bex.getBucketId());
        } catch (Exception ex) {
            log.error("Failed to publish BucketReadyEvent to Kafka: {}", ex.getMessage(), ex);
        }
    }
}
