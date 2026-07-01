package com.enterprise.atlas.workflow.service;

import com.enterprise.atlas.workflow.entity.Bucket;
import com.enterprise.atlas.workflow.entity.BucketExecution;
import com.enterprise.atlas.workflow.entity.CustomerForm;
import com.enterprise.atlas.workflow.entity.RevertStatus;
import com.enterprise.atlas.workflow.repository.BucketExecutionRepository;
import com.enterprise.atlas.workflow.repository.BucketRepository;
import com.enterprise.atlas.workflow.repository.CustomerFormRepository;
import com.enterprise.atlas.workflow.repository.RevertStatusRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class CreateBucketCommand implements WorkflowCommand {

    private static final Logger log = LoggerFactory.getLogger(CreateBucketCommand.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private BucketRepository bucketRepository;

    @Autowired
    private BucketExecutionRepository bucketExecutionRepository;

    @Autowired
    private RevertStatusRepository revertStatusRepository;

    @Autowired
    private CustomerFormRepository customerFormRepository;

    @Override
    public String getCommandType() {
        return "CREATE_BUCKET";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> input) throws Exception {
        String instanceId = (String) input.get("_instanceId");
        String contextId = (String) input.get("_contextId");
        String workflowKey = (String) input.get("_workflowKey");

        String bucketId = (String) input.get("bucket");
        if (bucketId == null) {
            bucketId = (String) input.get("bucketId");
        }

        if (bucketId == null || bucketId.isBlank()) {
            log.warn("CREATE_BUCKET command failed: missing bucket or bucketId parameter.");
            return Map.of();
        }

        log.info("Executing CREATE_BUCKET command: bucketId={}, instanceId={}", bucketId, instanceId);

        // 1. Create BucketExecution
        Optional<Bucket> bucketOpt = bucketRepository.findByBucketId(bucketId);
        BucketExecution bex = new BucketExecution();
        bex.setId(UUID.randomUUID().toString());
        bex.setInstanceId(instanceId);
        bex.setWorkflowKey(workflowKey);
        bex.setBucketId(bucketId);
        if (bucketOpt.isPresent()) {
            Bucket b = bucketOpt.get();
            bex.setBucketName(b.getName());
            bex.setPriority(b.getPriority());
            bex.setSlaHours(b.getSlaHours());
        } else {
            bex.setBucketName(bucketId);
        }
        bex.setStatus("PENDING");
        bucketExecutionRepository.save(bex);
        log.info("Created BucketExecution in PENDING status for instanceId={}, bucketId={}", instanceId, bucketId);

        // 2. Update CustomerForm and create RevertStatus
        if (contextId != null && !contextId.isBlank()) {
            // Update Form Status
            Optional<CustomerForm> formOpt = customerFormRepository.findById(contextId);
            if (formOpt.isPresent()) {
                CustomerForm form = formOpt.get();
                form.setFormStatus(bucketId + " Pending");
                customerFormRepository.save(form);
            } else {
                CustomerForm form = new CustomerForm();
                form.setId(contextId);
                form.setCustomerName("Customer_" + contextId.substring(0, Math.min(contextId.length(), 8)));
                form.setFormStatus(bucketId + " Pending");
                customerFormRepository.save(form);
            }

            // Create RevertStatus
            String previousStepId = null;
            List<RevertStatus> existing = revertStatusRepository.findByWorkflowInstanceIdOrderByCreatedAtDesc(instanceId);
            for (RevertStatus rs : existing) {
                if ("COMPLETED".equalsIgnoreCase(rs.getStatus())) {
                    previousStepId = rs.getId();
                    break;
                }
            }

            String dependencyBucketIds = null;
            Object depVal = input.get("dependencyBuckets");
            if (depVal != null) {
                try {
                    dependencyBucketIds = MAPPER.writeValueAsString(depVal);
                } catch (Exception ex) {
                    dependencyBucketIds = depVal.toString();
                }
            }

            Optional<RevertStatus> existingPending = revertStatusRepository.findByWorkflowInstanceIdAndBucketIdAndStatus(instanceId, bucketId, com.enterprise.atlas.workflow.entity.RevertStepStatus.PENDING);
            if (existingPending.isEmpty()) {
                RevertStatus revert = new RevertStatus();
                revert.setId(UUID.randomUUID().toString());
                revert.setWorkflowInstanceId(instanceId);
                revert.setFormId(contextId);
                revert.setBucketId(bucketId);
                revert.setBucketName((String) input.getOrDefault("_nodeLabel", bucketId));
                revert.setStatus("PENDING");
                revert.setPreviousStepId(previousStepId);
                revert.setDependencyBucketIds(dependencyBucketIds);
                revertStatusRepository.save(revert);
                log.info("Created PENDING RevertStatus entry for bucketId={}, instanceId={}", bucketId, instanceId);
            }
        }

        return Map.of("bucketExecutionId", bex.getId(), "status", "PENDING");
    }
}
