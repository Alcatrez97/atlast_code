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
    @Autowired private EntityRevertRepository        entityRevertRepository;
    @Autowired private com.vi.atlas.workflow.service.domain.EntityStatusSyncService entityStatusSyncService;

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
        java.util.Map<String, Object> fallbackCtx = (TraversalContextHolder.get() != null)
                ? TraversalContextHolder.get().context
                : null;
        createBucketRevertStatusAndFormPending(instanceId, formId, bucketId, node, version, fallbackCtx);
    }

    public void createBucketRevertStatusAndFormPending(String instanceId,
                                                        String formId,
                                                        String bucketId,
                                                        WorkflowNodeDto node,
                                                        WorkflowVersion version,
                                                        java.util.Map<String, Object> context) {
        if (context == null && TraversalContextHolder.get() != null) {
            context = TraversalContextHolder.get().context;
        }
        if (formId == null || formId.isBlank()
                || instanceId == null || instanceId.isBlank()
                || bucketId == null || bucketId.isBlank()) {
            log.warn("Skipping RevertStatus creation — missing formId={}, instanceId={}, or bucketId={}",
                    formId, instanceId, bucketId);
            return;
        }

        // 1. Resolve configurable pending form status
        String pendingStatus = null;
        if (node != null && node.getData() != null && node.getData().get("pendingFormStatus") != null) {
            String customStatus = node.getData().get("pendingFormStatus").toString().trim();
            if (!customStatus.isBlank()) {
                pendingStatus = customStatus;
            }
        }
        if (pendingStatus == null) {
            Optional<Bucket> bucketOpt = bucketRepository.findByBucketId(bucketId);
            if (bucketOpt.isPresent() && bucketOpt.get().getPendingFormStatus() != null
                    && !bucketOpt.get().getPendingFormStatus().isBlank()) {
                pendingStatus = bucketOpt.get().getPendingFormStatus().trim();
            }
        }
        if (pendingStatus == null) {
            pendingStatus = bucketId + " Pending";
        }

        // Update or create domain entity status
        String workflowKey = version != null && version.getWorkflowDefinition() != null
                ? version.getWorkflowDefinition().getKey()
                : null;
        entityStatusSyncService.syncStatus(workflowKey, formId, pendingStatus, bucketId, context);

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

        Long cafId = resolveCafId(formId, context);
        String resolvedFormId = (cafId != null) ? String.valueOf(cafId) : formId;

        // 5. Insert the PENDING RevertStatus record
        RevertStatus revert = new RevertStatus();
        revert.setId(UUID.randomUUID().toString());
        revert.setWorkflowInstanceId(instanceId);
        revert.setFormId(resolvedFormId);
        revert.setBucketId(bucketId);
        revert.setBucketName(node.getLabel() != null ? node.getLabel() : bucketId);
        revert.setStatus("PENDING");
        revert.setPreviousStepId(previousStepId);
        revert.setDependencyBucketIds(dependencyBucketIds);
        revertStatusRepository.save(revert);

        log.info("Created PENDING RevertStatus for bucketId={}, instanceId={}, previousStepId={}",
                bucketId, instanceId, previousStepId);

        // 6. Create PENDING EntityRevert record for review queue picker
        createPendingEntityRevert(instanceId, formId, bucketId, node, context);
    }

    private void createPendingEntityRevert(String instanceId, String formId, String bucketId, WorkflowNodeDto node, java.util.Map<String, Object> context) {
        try {
            if (context == null && TraversalContextHolder.get() != null) {
                context = TraversalContextHolder.get().context;
            }
            Long cafId = resolveCafId(formId, context);
            if (cafId == null) {
                log.warn("Skipping EntityRevert creation - could not resolve numeric cafId from formId={} or context", formId);
                return;
            }

            Integer circleId = resolveCircleId(context);

            Optional<EntityRevert> existingPending = entityRevertRepository
                    .findByWorkflowInstanceIdAndBucketIdAndRevertStatusAndActiveTrue(instanceId, bucketId, "PENDING");
            if (existingPending.isPresent()) {
                log.info("PENDING EntityRevert already exists for bucketId={}, instanceId={}", bucketId, instanceId);
                return;
            }

            // Resolve entTypeId from node data override, or bucket definition, or context, fallback to WORKFLOW_ENGINE
            String entTypeId = null;
            if (node != null && node.getData() != null && node.getData().get("entTypeId") != null) {
                String val = node.getData().get("entTypeId").toString().trim();
                if (!val.isBlank()) {
                    entTypeId = val;
                }
            }
            if (entTypeId == null) {
                Optional<Bucket> bucketOpt = bucketRepository.findByBucketId(bucketId);
                if (bucketOpt.isPresent() && bucketOpt.get().getEntTypeId() != null && !bucketOpt.get().getEntTypeId().isBlank()) {
                    entTypeId = bucketOpt.get().getEntTypeId().trim();
                }
            }
            if (entTypeId == null && context != null && context.get("entTypeId") != null) {
                entTypeId = context.get("entTypeId").toString().trim();
            }
            if (entTypeId == null || entTypeId.isBlank()) {
                entTypeId = "WORKFLOW_ENGINE";
            }

            int seqNumber = entityRevertRepository.findTopByCafIdOrderByRevertSeqNumberDesc(cafId)
                    .map(er -> er.getRevertSeqNumber() + 1)
                    .orElse(1);

            // Resolve initial revert status from node override or bucket definition, default to PENDING
            String initialRevertStatus = null;
            if (node != null && node.getData() != null && node.getData().get("pendingRevertStatus") != null) {
                String val = node.getData().get("pendingRevertStatus").toString().trim();
                if (!val.isBlank()) {
                    initialRevertStatus = val;
                }
            }
            if (initialRevertStatus == null) {
                Optional<Bucket> bucketOpt = bucketRepository.findByBucketId(bucketId);
                if (bucketOpt.isPresent() && bucketOpt.get().getPendingRevertStatus() != null && !bucketOpt.get().getPendingRevertStatus().isBlank()) {
                    initialRevertStatus = bucketOpt.get().getPendingRevertStatus().trim();
                }
            }
            if (initialRevertStatus == null || initialRevertStatus.isBlank()) {
                initialRevertStatus = "PENDING";
            }

            EntityRevert er = new EntityRevert();
            er.setCafId(cafId);
            er.setWorkflowInstanceId(instanceId);
            er.setRevertCategory("BUCKET");
            er.setBucketId(bucketId);
            er.setEntTypeId(entTypeId);
            er.setRevertStatus(initialRevertStatus);
            er.setActive(true);
            er.setCircleId(circleId);
            er.setRevertSeqNumber(seqNumber);

            if (context != null) {
                if (context.containsKey("entPersonId")) {
                    er.setEntPersonId(String.valueOf(context.get("entPersonId")));
                }
                if (context.containsKey("entOrgId")) {
                    er.setEntOrgId(String.valueOf(context.get("entOrgId")));
                }
            }

            entityRevertRepository.save(er);
            log.info("Created PENDING EntityRevert for cafId={}, bucketId={}, instanceId={}, circleId={}",
                    cafId, bucketId, instanceId, circleId);

        } catch (Exception e) {
            log.error("Failed to create PENDING EntityRevert for instanceId={}, bucketId={}: {}",
                    instanceId, bucketId, e.getMessage(), e);
        }
    }

    private Long resolveCafId(String formId, java.util.Map<String, Object> context) {
        if (context == null && TraversalContextHolder.get() != null) {
            context = TraversalContextHolder.get().context;
        }
        if (context != null) {
            for (String key : new String[] {
                    "cafId", "caf_id", "cafid", "CafId", "CAF_ID",
                    "businessKey", "business_key", "formId", "form_id"
            }) {
                Object cafObj = context.get(key);
                if (cafObj != null) {
                    Long parsed = com.vi.atlas.workflow.entity.CustomerForm.parseCafId(cafObj);
                    if (parsed != null) {
                        return parsed;
                    }
                }
            }
        }
        if (formId != null) {
            Long parsed = com.vi.atlas.workflow.entity.CustomerForm.parseCafId(formId);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private Integer resolveCircleId(java.util.Map<String, Object> context) {
        if (context == null && TraversalContextHolder.get() != null) {
            context = TraversalContextHolder.get().context;
        }
        if (context != null) {
            for (String key : new String[] {
                    "circleId", "circle_id", "circleid", "CircleId", "CIRCLE_ID",
                    "circle-id", "circleID", "CircleID", "CIRCLEID"
            }) {
                Object circleObj = context.get(key);
                if (circleObj != null) {
                    Long parsed = com.vi.atlas.workflow.entity.CustomerForm.parseCafId(circleObj);
                    if (parsed != null) {
                        return parsed.intValue();
                    }
                }
            }
        }
        return 1; // Default fallback circle ID
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
