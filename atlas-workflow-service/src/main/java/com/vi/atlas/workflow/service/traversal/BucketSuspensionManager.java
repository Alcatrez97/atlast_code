package com.vi.atlas.workflow.service.traversal;

import com.vi.atlas.common.dto.WorkflowNodeDto;
import com.vi.atlas.workflow.entity.*;
import com.vi.atlas.workflow.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles all side-effects related to suspending a workflow at a BUCKET node:
 * <ol>
 *   <li>Updates (or creates) the {@link com.vi.atlas.workflow.entity.CustomerForm}
 *       record to {@code "<bucketId> Pending"}</li>
 *   <li>Creates a PENDING {@link RevertStatus} entry (for audit/rollback chains)</li>
 *   <li>Creates a PENDING {@link BucketExecution} workload entry (for the
 *       Bucket Workload dashboard)</li>
 * </ol>
 *
 * <p>Logic copied verbatim from {@code GraphTraversalEngine}:
 * {@code createBucketRevertStatusAndFormPending}, {@code createBucketExecution}.
 */
@Component
public class BucketSuspensionManager {

    private static final Logger log = LoggerFactory.getLogger(BucketSuspensionManager.class);

    @Autowired private RevertStatusRepository        revertStatusRepository;
    @Autowired private CustomerFormRepository        customerFormRepository;
    @Autowired private BucketExecutionRepository     bucketExecutionRepository;
    @Autowired private BucketRepository              bucketRepository;

    // -----------------------------------------------------------------------
    // CustomerForm + RevertStatus
    // -----------------------------------------------------------------------

    /**
     * Updates the CustomerForm status to {@code "<bucketId> Pending"} (auto-creating
     * the form if it does not exist) and inserts a new PENDING RevertStatus record
     * that chains to the most-recently completed step of this instance.
     *
     * @param instanceId the workflow instance ID
     * @param formId     the customer form / context ID
     * @param bucketId   the bucket identifier
     * @param node       the BUCKET node in the graph
     * @param version    the workflow version currently executing
     */
    public void createBucketRevertStatusAndFormPending(String instanceId,
                                                        String formId,
                                                        String bucketId,
                                                        WorkflowNodeDto node,
                                                        WorkflowVersion version) {
        if (formId == null || formId.isBlank()
                || instanceId == null || instanceId.isBlank()
                || bucketId == null || bucketId.isBlank()) {
            log.warn("Skipping RevertStatus creation â€” missing formId={}, instanceId={}, or bucketId={}",
                    formId, instanceId, bucketId);
            return;
        }

        // 1. Update or create CustomerForm
        Optional<CustomerForm> formOpt = customerFormRepository.findById(formId);
        if (formOpt.isPresent()) {
            CustomerForm form = formOpt.get();
            form.setFormStatus(bucketId + " Pending");
            customerFormRepository.save(form);
            log.info("Updated CustomerForm status to '{} Pending' for formId={}", bucketId, formId);
        } else {
            CustomerForm form = new CustomerForm();
            form.setId(formId);
            form.setCustomerName("Customer_" + formId.substring(0, Math.min(formId.length(), 8)));
            form.setFormStatus(bucketId + " Pending");
            customerFormRepository.save(form);
            log.info("Created CustomerForm with status '{} Pending' for formId={}", bucketId, formId);
        }

        // 2. Find the previous completed step to chain the revert trail
        String previousStepId = null;
        List<RevertStatus> existing =
                revertStatusRepository.findByWorkflowInstanceIdOrderByCreatedAtDesc(instanceId);
        for (RevertStatus rs : existing) {
            if ("COMPLETED".equalsIgnoreCase(rs.getStatus())) {
                previousStepId = rs.getId();
                break;
            }
        }

        // 3. Resolve dependency list from node custom property
        String dependencyBucketIds = null;
        if (node.getData() != null && node.getData().containsKey("dependencyBuckets")) {
            Object depVal = node.getData().get("dependencyBuckets");
            if (depVal != null) {
                try {
                    dependencyBucketIds =
                            new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(depVal);
                } catch (Exception ex) {
                    dependencyBucketIds = depVal.toString();
                }
            }
        }

        // 4. Idempotency: skip if a PENDING record already exists for this bucket+instance
        Optional<RevertStatus> existingPending =
                revertStatusRepository.findByWorkflowInstanceIdAndBucketIdAndStatus(
                        instanceId, bucketId, RevertStepStatus.PENDING);
        if (existingPending.isPresent()) {
            log.info("PENDING RevertStatus already exists for bucketId={}, instanceId={}", bucketId, instanceId);
            return;
        }

        // 5. Insert the PENDING RevertStatus record
        RevertStatus revert = new RevertStatus();
        revert.setId(UUID.randomUUID().toString());
        revert.setWorkflowInstanceId(instanceId);
        revert.setFormId(formId);
        revert.setBucketId(bucketId);
        revert.setBucketName(node.getLabel() != null ? node.getLabel() : bucketId);
        revert.setStatus("PENDING");
        revert.setPreviousStepId(previousStepId);
        revert.setDependencyBucketIds(dependencyBucketIds);
        revertStatusRepository.save(revert);

        log.info("Created PENDING RevertStatus for bucketId={}, instanceId={}, previousStepId={}",
                bucketId, instanceId, previousStepId);
    }

    // -----------------------------------------------------------------------
    // BucketExecution workload record
    // -----------------------------------------------------------------------

    /**
     * Creates a PENDING {@link BucketExecution} workload entry for the bucket
     * dashboard. This method exists for completeness but is currently not called
     * from within the traversal â€” preserved verbatim from the original code.
     *
     * @param instanceId  the workflow instance ID
     * @param bucketId    the bucket identifier
     * @param workflowKey the workflow key currently executing
     */
    public void createBucketExecution(String instanceId, String bucketId, String workflowKey) {
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
        log.info("Created BucketExecution (PENDING) for instanceId={}, bucketId={}", instanceId, bucketId);
    }
}
