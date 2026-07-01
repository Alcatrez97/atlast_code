package com.enterprise.atlas.workflow.service;

import com.enterprise.atlas.workflow.entity.BucketExecution;
import com.enterprise.atlas.workflow.entity.CustomerForm;
import com.enterprise.atlas.workflow.entity.RevertStatus;
import com.enterprise.atlas.workflow.entity.WorkflowInstance;
import com.enterprise.atlas.workflow.repository.BucketExecutionRepository;
import com.enterprise.atlas.workflow.repository.CustomerFormRepository;
import com.enterprise.atlas.workflow.repository.RevertStatusRepository;
import com.enterprise.atlas.workflow.repository.WorkflowInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class BucketResolutionService {

    private static final Logger log = LoggerFactory.getLogger(BucketResolutionService.class);

    @Autowired
    private BucketExecutionRepository bucketExecutionRepository;

    @Autowired
    private RevertStatusRepository revertStatusRepository;

    @Autowired
    private CustomerFormRepository customerFormRepository;

    @Autowired
    private WorkflowInstanceRepository workflowInstanceRepository;

    @Autowired
    private EventRoutingService eventRoutingService;

    /**
     * Resolves a bucket task and resumes the correlated workflow instance.
     *
     * @param instanceId  the workflow instance ID
     * @param bucketId    the technical bucket ID (e.g. A2)
     * @param outcome     the resolution outcome (e.g. Accept, Reject, Park)
     * @param resolvedBy  identity of the user/system that resolved the task
     * @param notes       resolution/audit notes
     */
    public void resolveBucket(String instanceId, String bucketId, String outcome, String resolvedBy, String notes) {
        log.info("Resolving bucket for instanceId={}, bucketId={}, outcome={}, resolvedBy={}, notes='{}'",
                instanceId, bucketId, outcome, resolvedBy, notes);

        // 1. Update operational BucketExecution records to RESOLVED
        List<BucketExecution> pendingBex = bucketExecutionRepository.findPendingByInstanceIdAndBucketId(instanceId, bucketId);
        log.info("Found {} pending BucketExecution record(s) to resolve", pendingBex.size());
        for (BucketExecution bex : pendingBex) {
            bex.setStatus("RESOLVED");
            bex.setResolvedAt(LocalDateTime.now());
            bex.setResolvedBy(resolvedBy != null ? resolvedBy : "ExternalSystem");
            bex.setResolutionNotes(notes);
            bucketExecutionRepository.save(bex);
            log.info("Successfully resolved BucketExecution ID: {} for bucketId: {}", bex.getId(), bex.getBucketId());
        }

        // 2. Update audit trail RevertStatus to COMPLETED
        Optional<RevertStatus> pendingRevert = revertStatusRepository.findByWorkflowInstanceIdAndBucketIdAndStatus(instanceId, bucketId, com.enterprise.atlas.workflow.entity.RevertStepStatus.PENDING);
        if (pendingRevert.isPresent()) {
            RevertStatus rs = pendingRevert.get();
            rs.setStatus("COMPLETED");
            rs.setCompletedAt(LocalDateTime.now());
            rs.setResolvedBy(resolvedBy != null ? resolvedBy : "ExternalSystem");
            rs.setResolutionNotes(notes);
            revertStatusRepository.save(rs);
            log.info("Successfully completed RevertStatus ID: {} for bucketId: {}", rs.getId(), rs.getBucketId());
        } else {
            log.info("No pending RevertStatus record found for instanceId={} and bucketId={}", instanceId, bucketId);
        }

        // 3. Resolve parent businessKey and update CustomerForm status if applicable
        String businessKey = null;
        Optional<WorkflowInstance> instOpt = workflowInstanceRepository.findById(instanceId);
        if (instOpt.isPresent()) {
            businessKey = instOpt.get().getBusinessKey();
        }
        if (businessKey == null) {
            businessKey = instanceId; // fallback
        }

        // Load and update form status if form exists (using businessKey or instanceId as formId)
        String formId = businessKey;
        Optional<CustomerForm> formOpt = customerFormRepository.findById(formId);
        if (formOpt.isPresent()) {
            CustomerForm form = formOpt.get();
            String mappedStatus = bucketId + outcome; // e.g. A2Accept, A2Reject, A2Park
            form.setFormStatus(mappedStatus);
            customerFormRepository.save(form);
            log.info("Successfully updated CustomerForm ID: {} status to '{}'", formId, mappedStatus);
        } else {
            log.info("No CustomerForm found with ID: {}. Skipping status update.", formId);
        }

        // 4. Trigger engine resumption through EventRoutingService
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("lastOutcome", outcome);
        additionalContext.put("lastBucketId", bucketId);
        additionalContext.put("form_status", bucketId + outcome);
        additionalContext.put(bucketId + "_status", "RESOLVED");
        additionalContext.put(bucketId + "_resolvedBy", resolvedBy);
        additionalContext.put(bucketId + "_resolutionNotes", notes != null ? notes : "");
        additionalContext.put("resolvedBy", resolvedBy);
        additionalContext.put("notes", notes != null ? notes : "");

        log.info("Routing bucket resolution event: eventType={}, correlationKey={}, payload={}", bucketId, businessKey, additionalContext);
        eventRoutingService.routeEvent(bucketId, businessKey, additionalContext);
    }
}
