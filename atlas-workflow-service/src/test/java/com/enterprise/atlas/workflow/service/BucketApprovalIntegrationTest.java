package com.enterprise.atlas.workflow.service;

import com.enterprise.atlas.common.dto.*;
import com.enterprise.atlas.workflow.entity.CustomerForm;
import com.enterprise.atlas.workflow.entity.ExecutionLog;
import com.enterprise.atlas.workflow.entity.RevertStatus;
import com.enterprise.atlas.workflow.entity.WorkflowInstance;
import com.enterprise.atlas.workflow.repository.CustomerFormRepository;
import com.enterprise.atlas.workflow.repository.ExecutionRepository;
import com.enterprise.atlas.workflow.repository.RevertStatusRepository;
import com.enterprise.atlas.workflow.repository.WorkflowInstanceRepository;
import com.enterprise.atlas.workflow.scheduler.FormApprovalScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
@Transactional
@ActiveProfiles("test")
@DirtiesContext
public class BucketApprovalIntegrationTest {

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private CustomerFormRepository customerFormRepository;

    @Autowired
    private RevertStatusRepository revertStatusRepository;

    @Autowired
    private WorkflowInstanceRepository instanceRepository;

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private com.enterprise.atlas.workflow.service.BucketResolutionService bucketResolutionService;

    @Autowired
    private FormApprovalScheduler formApprovalScheduler;

    private String workflowKey;
    private String definitionId;

    @BeforeEach
    public void setUp() {
        workflowKey = "APPROVAL_TEST_WF_" + UUID.randomUUID().toString().substring(0, 8);
        
        WorkflowDefinitionDto defDto = new WorkflowDefinitionDto();
        defDto.setKey(workflowKey);
        defDto.setName("External Approval Test");
        defDto.setDescription("Tests external bucket approvals and revert status logging");

        WorkflowDefinitionDto createdDef = workflowService.createWorkflowDefinition(defDto);
        definitionId = createdDef.getId();
        String initialVersionId = createdDef.getVersions().get(0).getId();

        // 1. Build a mock graph containing START -> BUCKET (A2) -> DECISION -> END (Approved) / END (Rejected)
        // Nodes
        List<WorkflowNodeDto> nodes = new ArrayList<>();
        
        WorkflowNodeDto startNode = new WorkflowNodeDto();
        startNode.setId("start-1");
        startNode.setType("START");
        startNode.setLabel("Start");
        nodes.add(startNode);

        WorkflowNodeDto bucketNode = new WorkflowNodeDto();
        bucketNode.setId("bucket-a2");
        bucketNode.setType("BUCKET");
        bucketNode.setLabel("Form A2 Verification");
        bucketNode.getData().put("bucketId", "A2");
        // add designer-configured dependency
        bucketNode.getData().put("dependencyBuckets", Arrays.asList("A1"));
        nodes.add(bucketNode);

        WorkflowNodeDto decisionNode = new WorkflowNodeDto();
        decisionNode.setId("decision-1");
        decisionNode.setType("DECISION");
        decisionNode.setLabel("Check Outcome");
        decisionNode.getData().put("decisionField", "form_status");
        nodes.add(decisionNode);

        WorkflowNodeDto endApprovedNode = new WorkflowNodeDto();
        endApprovedNode.setId("end-approved");
        endApprovedNode.setType("END");
        endApprovedNode.setLabel("Approved Out");
        nodes.add(endApprovedNode);

        WorkflowNodeDto endRejectedNode = new WorkflowNodeDto();
        endRejectedNode.setId("end-rejected");
        endRejectedNode.setType("END");
        endRejectedNode.setLabel("Rejected Out");
        nodes.add(endRejectedNode);

        // Edges
        List<WorkflowEdgeDto> edges = new ArrayList<>();
        
        WorkflowEdgeDto e1 = new WorkflowEdgeDto();
        e1.setId("e-1");
        e1.setSource("start-1");
        e1.setTarget("bucket-a2");
        edges.add(e1);

        WorkflowEdgeDto e2 = new WorkflowEdgeDto();
        e2.setId("e-2");
        e2.setSource("bucket-a2");
        e2.setTarget("decision-1");
        edges.add(e2);

        // Edge taking A2Accept branch
        WorkflowEdgeDto eAccept = new WorkflowEdgeDto();
        eAccept.setId("e-accept");
        eAccept.setSource("decision-1");
        eAccept.setTarget("end-approved");
        eAccept.setLabel("A2Accept");
        eAccept.getData().put("condition", "context.form_status == 'A2Accept'");
        edges.add(eAccept);

        // Edge taking A2Reject branch
        WorkflowEdgeDto eReject = new WorkflowEdgeDto();
        eReject.setId("e-reject");
        eReject.setSource("decision-1");
        eReject.setTarget("end-rejected");
        eReject.setLabel("A2Reject");
        eReject.getData().put("condition", "context.form_status == 'A2Reject'");
        edges.add(eReject);

        // Set definition graph
        WorkflowGraphDto graph = new WorkflowGraphDto();
        graph.setNodes(nodes);
        graph.setEdges(edges);

        // Save v1 draft and publish it
        workflowService.updateDraftVersion(initialVersionId, graph);
        workflowService.transitionVersionStatus(initialVersionId, "REVIEW");
        workflowService.transitionVersionStatus(initialVersionId, "APPROVED");
        workflowService.transitionVersionStatus(initialVersionId, "PUBLISHED");
    }

    @Test
    public void testBucketEntryAndStatusChange() {
        String formId = "FORM_" + UUID.randomUUID().toString().substring(0, 8);

        // Execute workflow
        ExecutionRequestDto req = new ExecutionRequestDto();
        req.setContextId(formId);
        req.setContext(new HashMap<>());

        ExecutionLogDto execution = executionService.execute(workflowKey, req);

        // Assert workflow is suspended at BUCKET A2
        assertEquals("WAITING", execution.getStatus());
        assertEquals("bucket-a2", execution.getOutcomeNodeId());

        // Assert CustomerForm was updated to "A2 Pending"
        Optional<CustomerForm> formOpt = customerFormRepository.findById(formId);
        assertTrue(formOpt.isPresent());
        assertEquals("A2 Pending", formOpt.get().getFormStatus());

        // Assert RevertStatus record created in PENDING state
        Optional<RevertStatus> revertOpt = revertStatusRepository.findByFormIdAndBucketIdAndStatus(formId, "A2", com.enterprise.atlas.workflow.entity.RevertStepStatus.PENDING);
        assertTrue(revertOpt.isPresent());
        RevertStatus revert = revertOpt.get();
        assertEquals(execution.getInstanceId(), revert.getWorkflowInstanceId());
        assertNull(revert.getPreviousStepId()); // first bucket
        assertTrue(revert.getDependencyBucketIds().contains("A1")); // has manually configured dependency
    }

    @Test
    public void testEventResumptionApproved() {
        String formId = "FORM_" + UUID.randomUUID().toString().substring(0, 8);

        ExecutionRequestDto req = new ExecutionRequestDto();
        req.setContextId(formId);
        req.setContext(new HashMap<>());
        ExecutionLogDto execution = executionService.execute(workflowKey, req);
        String instanceId = execution.getInstanceId();

        // Trigger event approval
        bucketResolutionService.resolveBucket(instanceId, "A2", "Accept", "ApproverBob", "Verification complete");

        // Check instance is COMPLETED
        WorkflowInstance instance = instanceRepository.findById(instanceId).orElseThrow();
        assertEquals("COMPLETED", instance.getStatus());
        assertEquals("end-approved", instance.getCurrentNodeId());

        // Check RevertStatus audit record is COMPLETED
        List<RevertStatus> audits = revertStatusRepository.findByWorkflowInstanceIdOrderByCreatedAtDesc(instanceId);
        assertEquals(1, audits.size());
        RevertStatus audit = audits.get(0);
        assertEquals("COMPLETED", audit.getStatus());
        assertEquals("ApproverBob", audit.getResolvedBy());
        assertEquals("Verification complete", audit.getResolutionNotes());
        assertNotNull(audit.getCompletedAt());
    }

    @Test
    public void testSchedulerPollingResumptionRejected() {
        String formId = "FORM_" + UUID.randomUUID().toString().substring(0, 8);

        ExecutionRequestDto req = new ExecutionRequestDto();
        req.setContextId(formId);
        req.setContext(new HashMap<>());
        ExecutionLogDto execution = executionService.execute(workflowKey, req);
        String instanceId = execution.getInstanceId();

        // Mock external system changing form status in the DB directly
        CustomerForm form = customerFormRepository.findById(formId).orElseThrow();
        form.setFormStatus("A2Reject");
        customerFormRepository.saveAndFlush(form);

        // Run scheduler poller manually
        formApprovalScheduler.pollExternalApprovals();

        // Check instance is COMPLETED (routing to rejected node)
        WorkflowInstance instance = instanceRepository.findById(instanceId).orElseThrow();
        assertEquals("COMPLETED", instance.getStatus());
        assertEquals("end-rejected", instance.getCurrentNodeId());

        // Check RevertStatus audit record is COMPLETED
        List<RevertStatus> audits = revertStatusRepository.findByWorkflowInstanceIdOrderByCreatedAtDesc(instanceId);
        assertEquals(1, audits.size());
        RevertStatus audit = audits.get(0);
        assertEquals("COMPLETED", audit.getStatus());
        assertEquals("SchedulerPoller", audit.getResolvedBy());
        assertNotNull(audit.getCompletedAt());
    }

    @Test
    public void testRuleNodeFalseOutcome() {
        String key = "RULE_TEST_WF_" + UUID.randomUUID().toString().substring(0, 8);
        WorkflowDefinitionDto defDto = new WorkflowDefinitionDto();
        defDto.setKey(key);
        defDto.setName("Rule Node False Outcome Test");
        defDto.setDescription("Tests rule node evaluating to false does not take true edge");

        WorkflowDefinitionDto createdDef = workflowService.createWorkflowDefinition(defDto);
        String versionId = createdDef.getVersions().get(0).getId();

        List<WorkflowNodeDto> nodes = new ArrayList<>();
        WorkflowNodeDto startNode = new WorkflowNodeDto();
        startNode.setId("start-1");
        startNode.setType("START");
        startNode.setLabel("Start");
        nodes.add(startNode);

        WorkflowNodeDto ruleNode = new WorkflowNodeDto();
        ruleNode.setId("rule-1");
        ruleNode.setType("RULE");
        ruleNode.setLabel("Amount Check");
        ruleNode.getData().put("expression", "context.amount > 5000");
        nodes.add(ruleNode);

        WorkflowNodeDto endApprovedNode = new WorkflowNodeDto();
        endApprovedNode.setId("end-approved");
        endApprovedNode.setType("END");
        endApprovedNode.setLabel("Approved Out");
        nodes.add(endApprovedNode);

        WorkflowNodeDto endRejectedNode = new WorkflowNodeDto();
        endRejectedNode.setId("end-rejected");
        endRejectedNode.setType("END");
        endRejectedNode.setLabel("Rejected Out");
        nodes.add(endRejectedNode);

        List<WorkflowEdgeDto> edges = new ArrayList<>();
        WorkflowEdgeDto e1 = new WorkflowEdgeDto();
        e1.setId("e-1");
        e1.setSource("start-1");
        e1.setTarget("rule-1");
        edges.add(e1);

        WorkflowEdgeDto eTrue = new WorkflowEdgeDto();
        eTrue.setId("e-true");
        eTrue.setSource("rule-1");
        eTrue.setTarget("end-approved");
        eTrue.getData().put("condition", "true");
        edges.add(eTrue);

        WorkflowEdgeDto eFalse = new WorkflowEdgeDto();
        eFalse.setId("e-false");
        eFalse.setSource("rule-1");
        eFalse.setTarget("end-rejected");
        eFalse.getData().put("condition", "false");
        edges.add(eFalse);

        WorkflowGraphDto graph = new WorkflowGraphDto();
        graph.setNodes(nodes);
        graph.setEdges(edges);

        workflowService.updateDraftVersion(versionId, graph);
        workflowService.transitionVersionStatus(versionId, "REVIEW");
        workflowService.transitionVersionStatus(versionId, "APPROVED");
        workflowService.transitionVersionStatus(versionId, "PUBLISHED");

        // Execute workflow
        String formId = "FORM_" + UUID.randomUUID().toString().substring(0, 8);
        ExecutionRequestDto req = new ExecutionRequestDto();
        req.setContextId(formId);
        req.setContext(Map.of("amount", 1000)); // less than 5000 -> false

        ExecutionLogDto execution = executionService.execute(key, req);
        assertEquals("COMPLETED", execution.getStatus());
        assertEquals("end-rejected", execution.getOutcomeNodeId());
    }
}

