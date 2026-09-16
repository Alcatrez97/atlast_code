package com.vi.atlas.workflow.service;

import com.vi.atlas.common.dto.ExecutionLogDto;
import com.vi.atlas.common.dto.ExecutionRequestDto;
import com.vi.atlas.workflow.entity.CustomerForm;
import com.vi.atlas.workflow.entity.StagedPayload;
import com.vi.atlas.workflow.entity.WorkflowInstance;
import com.vi.atlas.workflow.repository.CustomerFormRepository;
import com.vi.atlas.workflow.repository.StagedPayloadRepository;
import com.vi.atlas.workflow.repository.WorkflowInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Ingestion Gateway service providing "Start-or-Correlate" capabilities.
 * Manages staging of out-of-order API arrivals (Documents, CAF, Family Group)
 * and dispatches event signals to drive workflow convergence.
 */
@Service
public class CafJourneyIngestionService {

    private static final Logger log = LoggerFactory.getLogger(CafJourneyIngestionService.class);

    public static final String CAF_WORKFLOW_KEY = "caf-submission-orchestrator";
    public static final String FAMILY_WORKFLOW_KEY = "family-group-orchestrator";

    @Autowired
    private StagedPayloadRepository stagedPayloadRepository;

    @Autowired
    private WorkflowInstanceRepository workflowInstanceRepository;

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private EventRoutingService eventRoutingService;

    @Autowired
    private CustomerFormRepository customerFormRepository;

    /**
     * Staging & Ingestion handler for Document Submission API.
     */
    @Transactional
    public IngestionResult stageAndIngestDocuments(String trackingId, Integer circleId, Map<String, Object> docsPayload) {
        log.info("Received documents submission for trackingId={}, circleId={}", trackingId, circleId);

        // 1. Persist staged payload
        StagedPayload staged = new StagedPayload(trackingId, "DOCUMENTS", docsPayload, circleId);
        stagedPayloadRepository.save(staged);

        // 2. Start-or-Correlate Workflow
        WorkflowInstance instance = ensureCafWorkflowStarted(trackingId, circleId, docsPayload);

        // 3. Dispatch DOCUMENTS_STAGED event to advance workflow
        Map<String, Object> eventPayload = new HashMap<>(docsPayload != null ? docsPayload : Map.of());
        eventPayload.put("businessKey", trackingId);
        eventPayload.put("docsReady", true);
        eventRoutingService.routeEvent("DOCUMENTS_STAGED", trackingId, eventPayload);

        // 4. Return status
        return buildResult(trackingId, instance.getId(), "DOCUMENTS_STAGED");
    }

    /**
     * Staging & Ingestion handler for CAF Submission API.
     */
    @Transactional
    public IngestionResult stageAndIngestCaf(String trackingId, Integer circleId, Map<String, Object> cafPayload) {
        log.info("Received CAF submission for trackingId={}, circleId={}", trackingId, circleId);

        // 1. Persist staged payload
        StagedPayload staged = new StagedPayload(trackingId, "CAF", cafPayload, circleId);
        stagedPayloadRepository.save(staged);

        // 2. Start-or-Correlate Workflow
        WorkflowInstance instance = ensureCafWorkflowStarted(trackingId, circleId, cafPayload);

        // 3. Dispatch CAF_STAGED event to advance workflow
        Map<String, Object> eventPayload = new HashMap<>(cafPayload != null ? cafPayload : Map.of());
        eventPayload.put("businessKey", trackingId);
        eventPayload.put("cafReady", true);
        eventRoutingService.routeEvent("CAF_STAGED", trackingId, eventPayload);

        // 4. Return status
        return buildResult(trackingId, instance.getId(), "CAF_STAGED");
    }

    /**
     * Staging & Ingestion handler for Family Group Creation API.
     */
    @Transactional
    public IngestionResult stageAndIngestFamilyGroup(String groupId, String primaryTrackingId,
                                                    List<String> memberTrackingIds, Map<String, Object> groupPayload) {
        log.info("Received family group creation for groupId={}, primaryTrackingId={}", groupId, primaryTrackingId);

        Map<String, Object> data = new HashMap<>(groupPayload != null ? groupPayload : Map.of());
        data.put("groupId", groupId);
        data.put("primaryTrackingId", primaryTrackingId);
        data.put("memberTrackingIds", memberTrackingIds);

        // 1. Persist staged payload
        StagedPayload staged = new StagedPayload(groupId, "FAMILY_GROUP", data, 1);
        stagedPayloadRepository.save(staged);

        // 2. Start-or-Correlate Family Workflow
        Optional<WorkflowInstance> existing = workflowInstanceRepository
                .findFirstByWorkflowKeyAndBusinessKeyOrderByCreatedAtDesc(FAMILY_WORKFLOW_KEY, groupId);

        String instanceId;
        if (existing.isEmpty()) {
            ExecutionRequestDto req = new ExecutionRequestDto();
            req.setBusinessKey(groupId);
            data.put("circleId", 1);
            req.setContext(data);
            ExecutionLogDto response = executionService.execute(FAMILY_WORKFLOW_KEY, req);
            instanceId = response.getInstanceId();
        } else {
            instanceId = existing.get().getId();
        }

        // 3. Dispatch GROUP_CREATION_REQUESTED event
        eventRoutingService.routeEvent("GROUP_CREATION_REQUESTED", groupId, data);

        return buildResult(groupId, instanceId, "GROUP_CREATION_REQUESTED");
    }

    /**
     * Checks if a workflow instance already exists for this business key; if not, starts one.
     */
    private WorkflowInstance ensureCafWorkflowStarted(String trackingId, Integer circleId, Map<String, Object> initialContext) {
        Optional<WorkflowInstance> existing = workflowInstanceRepository
                .findFirstByWorkflowKeyAndBusinessKeyOrderByCreatedAtDesc(CAF_WORKFLOW_KEY, trackingId);

        if (existing.isPresent()) {
            return existing.get();
        }

        log.info("No active workflow instance found for trackingId={}. Starting new instance of {}", trackingId, CAF_WORKFLOW_KEY);
        ExecutionRequestDto req = new ExecutionRequestDto();
        req.setBusinessKey(trackingId);
        req.setContextId(trackingId);
        Map<String, Object> ctx = new HashMap<>(initialContext != null ? initialContext : Map.of());
        ctx.put("circleId", circleId != null ? circleId : 1);
        ctx.put("businessKey", trackingId);
        ctx.put("trackingId", trackingId);
        req.setContext(ctx);

        ExecutionLogDto response = executionService.execute(CAF_WORKFLOW_KEY, req);

        return workflowInstanceRepository.findById(response.getInstanceId())
                .orElseThrow(() -> new IllegalStateException("Failed to find created instance: " + response.getInstanceId()));
    }

    /**
     * Query overall journey status for tracking ID.
     */
    public Map<String, Object> getJourneyStatus(String trackingId) {
        Map<String, Object> status = new HashMap<>();
        status.put("trackingId", trackingId);

        List<StagedPayload> stagedList = stagedPayloadRepository.findAllByBusinessKey(trackingId);
        status.put("stagedPayloadsCount", stagedList.size());
        status.put("stagedTypes", stagedList.stream().map(StagedPayload::getPayloadType).toList());

        Optional<WorkflowInstance> instanceOpt = workflowInstanceRepository
                .findFirstByWorkflowKeyAndBusinessKeyOrderByCreatedAtDesc(CAF_WORKFLOW_KEY, trackingId);

        if (instanceOpt.isPresent()) {
            WorkflowInstance inst = instanceOpt.get();
            status.put("workflowInstanceId", inst.getId());
            status.put("workflowStatus", inst.getStatus());
            status.put("currentNodeId", inst.getCurrentNodeId());
        } else {
            status.put("workflowStatus", "NOT_STARTED");
        }

        Optional<CustomerForm> formOpt = customerFormRepository.findById(trackingId);
        if (formOpt.isPresent()) {
            status.put("goldenRecordCreated", true);
            status.put("customerFormStatus", formOpt.get().getFormStatus());
            status.put("customerName", formOpt.get().getCustomerName());
        } else {
            status.put("goldenRecordCreated", false);
        }

        return status;
    }

    private IngestionResult buildResult(String trackingId, String instanceId, String eventRouted) {
        WorkflowInstance current = workflowInstanceRepository.findById(instanceId).orElse(null);
        String currentStatus = current != null ? current.getStatus() : "UNKNOWN";
        boolean completed = "COMPLETED".equalsIgnoreCase(currentStatus);

        return new IngestionResult(
                trackingId,
                instanceId,
                eventRouted,
                currentStatus,
                completed
        );
    }

    public record IngestionResult(
            String trackingId,
            String instanceId,
            String eventRouted,
            String workflowStatus,
            boolean finalized
    ) {}
}
