package com.enterprise.atlas.workflow.scheduler;

import com.enterprise.atlas.workflow.entity.CustomerForm;
import com.enterprise.atlas.workflow.entity.ExecutionLog;
import com.enterprise.atlas.workflow.entity.RevertStatus;
import com.enterprise.atlas.workflow.entity.WorkflowInstance;
import com.enterprise.atlas.workflow.entity.WorkflowInstanceStatus;
import com.enterprise.atlas.workflow.repository.CustomerFormRepository;
import com.enterprise.atlas.workflow.repository.ExecutionRepository;
import com.enterprise.atlas.workflow.repository.RevertStatusRepository;
import com.enterprise.atlas.workflow.repository.WorkflowInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class FormApprovalScheduler {

    private static final Logger log = LoggerFactory.getLogger(FormApprovalScheduler.class);

    @Autowired
    private WorkflowInstanceRepository instanceRepository;

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private CustomerFormRepository customerFormRepository;

    @Autowired
    private RevertStatusRepository revertStatusRepository;

    @Autowired
    private com.enterprise.atlas.workflow.service.BucketResolutionService bucketResolutionService;

    @Scheduled(fixedDelay = 5000)
    public void pollExternalApprovals() {
        // 1. Find all active workflow instances in WAITING state
        List<WorkflowInstance> waitingInstances = instanceRepository.findByStatusOrderByCreatedAtDesc(WorkflowInstanceStatus.WAITING);
        if (waitingInstances.isEmpty()) {
            return;
        }

        log.trace("Polling external approvals: found {} instances in WAITING state", waitingInstances.size());

        for (WorkflowInstance instance : waitingInstances) {
            String instanceId = instance.getId();

            // 2. Fetch the corresponding ExecutionLog to get formId (contextId)
            List<ExecutionLog> logs = executionRepository.findByInstanceIdAndStatus(instanceId, WorkflowInstanceStatus.WAITING);
            if (logs.isEmpty()) {
                continue;
            }

            for (ExecutionLog executionLog : logs) {
                String formId = executionLog.getContextId();
                if (formId == null || formId.isBlank()) {
                    continue;
                }

                // 3. Find the active PENDING RevertStatus record for this instance
                List<RevertStatus> revertRecords = revertStatusRepository.findByWorkflowInstanceIdOrderByCreatedAtDesc(instanceId);
                RevertStatus pendingRevert = null;
                for (RevertStatus rs : revertRecords) {
                    if ("PENDING".equalsIgnoreCase(rs.getStatus())) {
                        pendingRevert = rs;
                        break;
                    }
                }

                if (pendingRevert == null) {
                    continue;
                }

                String bucketId = pendingRevert.getBucketId();
                String pendingStatusString = bucketId + " Pending";

                // 4. Check the CustomerForm status in the database
                Optional<CustomerForm> formOpt = customerFormRepository.findById(formId);
                if (formOpt.isPresent()) {
                    CustomerForm form = formOpt.get();
                    String currentFormStatus = form.getFormStatus();

                    // 5. If formStatus has changed from "<BucketId> Pending" to something else (e.g. "A2Accept" or "A2Reject")
                    if (currentFormStatus != null && !currentFormStatus.isBlank() && !currentFormStatus.equalsIgnoreCase(pendingStatusString)) {
                        log.info("Scheduler detected external status transition for formId={}: '{}' -> '{}'. Triggering resumption...",
                                formId, pendingStatusString, currentFormStatus);

                        String outcome = currentFormStatus;
                        if (currentFormStatus.startsWith(bucketId)) {
                            outcome = currentFormStatus.substring(bucketId.length());
                        }

                        bucketResolutionService.resolveBucket(
                                instanceId,
                                bucketId,
                                outcome,
                                "SchedulerPoller",
                                "Detected status transition from pending bucket '" + bucketId + "' to outcome status '" + currentFormStatus + "'"
                        );
                    }
                }
            }
        }
    }
}
