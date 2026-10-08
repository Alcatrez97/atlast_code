package com.vi.atlas.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vi.atlas.common.dto.*;
import com.vi.atlas.workflow.entity.CustomerForm;
import com.vi.atlas.workflow.entity.RevertStatus;
import com.vi.atlas.workflow.entity.WorkflowInstance;
import com.vi.atlas.workflow.repository.CustomerFormRepository;
import com.vi.atlas.workflow.repository.RevertStatusRepository;
import com.vi.atlas.workflow.repository.WorkflowInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@DirtiesContext
public class CafBucketResolutionApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    private String workflowKey;

    @BeforeEach
    public void setUp() {
        workflowKey = "CAF_A2_WORKFLOW_" + UUID.randomUUID().toString().substring(0, 8);

        // 1. Create Workflow Definition with Bucket "A2"
        WorkflowDefinitionDto defDto = new WorkflowDefinitionDto();
        defDto.setKey(workflowKey);
        defDto.setName("CAF Onboarding with A2 Bucket");
        defDto.setDescription("Workflow demonstrating A2 bucket suspension and external API accept/reject resolution");

        WorkflowDefinitionDto createdDef = workflowService.createWorkflowDefinition(defDto);
        String versionId = createdDef.getVersions().get(0).getId();

        // 2. Build graph: START -> BUCKET (A2) -> DECISION -> END (Approved) / END (Rejected)
        List<WorkflowNodeDto> nodes = new ArrayList<>();

        WorkflowNodeDto startNode = new WorkflowNodeDto();
        startNode.setId("start-1");
        startNode.setType("START");
        startNode.setLabel("Start Customer Onboarding");
        nodes.add(startNode);

        WorkflowNodeDto bucketNode = new WorkflowNodeDto();
        bucketNode.setId("bucket-a2-node");
        bucketNode.setType("BUCKET");
        bucketNode.setLabel("A2 Manual Verification");
        bucketNode.getData().put("bucketId", "A2");
        nodes.add(bucketNode);

        WorkflowNodeDto decisionNode = new WorkflowNodeDto();
        decisionNode.setId("decision-1");
        decisionNode.setType("DECISION");
        decisionNode.setLabel("Evaluate Outcome");
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

        // 3. Edges with conditions
        List<WorkflowEdgeDto> edges = new ArrayList<>();

        WorkflowEdgeDto e1 = new WorkflowEdgeDto();
        e1.setId("e-1");
        e1.setSource("start-1");
        e1.setTarget("bucket-a2-node");
        edges.add(e1);

        WorkflowEdgeDto e2 = new WorkflowEdgeDto();
        e2.setId("e-2");
        e2.setSource("bucket-a2-node");
        e2.setTarget("decision-1");
        edges.add(e2);

        // Accept Branch
        WorkflowEdgeDto eAccept = new WorkflowEdgeDto();
        eAccept.setId("e-accept");
        eAccept.setSource("decision-1");
        eAccept.setTarget("end-approved");
        eAccept.setLabel("Accept Branch");
        eAccept.getData().put("condition", "context.lastOutcome == 'Accept' || context.form_status == 'A2Accept'");
        edges.add(eAccept);

        // Reject Branch
        WorkflowEdgeDto eReject = new WorkflowEdgeDto();
        eReject.setId("e-reject");
        eReject.setSource("decision-1");
        eReject.setTarget("end-rejected");
        eReject.setLabel("Reject Branch");
        eReject.getData().put("condition", "context.lastOutcome == 'Reject' || context.form_status == 'A2Reject'");
        edges.add(eReject);

        WorkflowGraphDto graph = new WorkflowGraphDto();
        graph.setNodes(nodes);
        graph.setEdges(edges);

        // Update draft and publish
        workflowService.updateDraftVersion(versionId, graph);
        workflowService.transitionVersionStatus(versionId, "REVIEW");
        workflowService.transitionVersionStatus(versionId, "APPROVED");
        workflowService.transitionVersionStatus(versionId, "PUBLISHED");
    }

    @Test
    public void testExternalApiAcceptUsingCafId() throws Exception {
        String cafId = "982001"; // Simulated CAF ID (primary key in context / form ID)

        // Step 1: Start workflow for this CAF ID
        ExecutionRequestDto startReq = new ExecutionRequestDto();
        startReq.setContextId(cafId);
        startReq.setBusinessKey(cafId);
        startReq.setContext(Map.of("cafId", cafId));

        ExecutionLogDto execution = executionService.execute(workflowKey, startReq);

        // Verify workflow is suspended at Bucket A2
        assertEquals("WAITING", execution.getStatus());
        assertEquals("bucket-a2-node", execution.getOutcomeNodeId());

        // Verify CustomerForm status was set to "A2 Pending"
        Optional<CustomerForm> formOpt = customerFormRepository.findById(Long.parseLong(cafId));
        assertTrue(formOpt.isPresent());
        assertEquals("A2 Pending", formOpt.get().getFormStatus());

        // Step 2: External System sends ACCEPT signal using only cafId via PUT /api/forms/{cafId}/status
        Map<String, String> acceptPayload = Map.of(
            "status", "A2Accept",
            "outcome", "Accept",
            "notes", "Customer identity verified successfully by external portal"
        );

        mockMvc.perform(put("/api/forms/{id}/status", cafId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(acceptPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formStatus").value("A2Accept"));

        // Step 3: Verify the workflow instance resumed and finished at Approved node
        WorkflowInstance instance = instanceRepository.findById(execution.getInstanceId()).orElseThrow();
        assertEquals("COMPLETED", instance.getStatus());
        assertEquals("end-approved", instance.getCurrentNodeId());

        // Verify RevertStatus audit record is COMPLETED
        List<RevertStatus> audits = revertStatusRepository.findByWorkflowInstanceIdOrderByCreatedAtDesc(instance.getId());
        assertFalse(audits.isEmpty());
        RevertStatus audit = audits.get(0);
        assertEquals("COMPLETED", audit.getStatus());
        assertEquals("A2", audit.getBucketId());
    }

    @Test
    public void testExternalApiRejectUsingCafId() throws Exception {
        String cafId = "982002"; // Simulated CAF ID

        // Step 1: Start workflow for this CAF ID
        ExecutionRequestDto startReq = new ExecutionRequestDto();
        startReq.setContextId(cafId);
        startReq.setBusinessKey(cafId);
        startReq.setContext(Map.of("cafId", cafId));

        ExecutionLogDto execution = executionService.execute(workflowKey, startReq);
        assertEquals("WAITING", execution.getStatus());

        // Step 2: External System sends REJECT signal using only cafId via PUT /api/forms/{cafId}/status
        Map<String, String> rejectPayload = Map.of(
            "status", "A2Reject",
            "outcome", "Reject",
            "notes", "Invalid documents provided"
        );

        mockMvc.perform(put("/api/forms/{id}/status", cafId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(rejectPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formStatus").value("A2Reject"));

        // Step 3: Verify the workflow instance resumed and finished at Rejected node
        WorkflowInstance instance = instanceRepository.findById(execution.getInstanceId()).orElseThrow();
        assertEquals("COMPLETED", instance.getStatus());
        assertEquals("end-rejected", instance.getCurrentNodeId());
    }

    @Test
    public void testEventApiResolutionUsingCafId() throws Exception {
        String cafId = "982003";

        // Step 1: Start workflow for this CAF ID
        ExecutionRequestDto startReq = new ExecutionRequestDto();
        startReq.setContextId(cafId);
        startReq.setBusinessKey(cafId);
        startReq.setContext(Map.of("cafId", cafId));

        ExecutionLogDto execution = executionService.execute(workflowKey, startReq);
        assertEquals("WAITING", execution.getStatus());

        // Step 2: External System publishes event using bucketId ("A2") and businessKey (cafId)
        Map<String, Object> eventBody = Map.of(
            "eventType", "A2",
            "businessKey", cafId,
            "payload", Map.of(
                "outcome", "Accept",
                "lastOutcome", "Accept",
                "form_status", "A2Accept",
                "resolvedBy", "ExternalEventGateway"
            )
        );

        mockMvc.perform(post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(eventBody)))
                .andExpect(status().isOk());

        // Step 3: Verify workflow resumed to Approved branch
        WorkflowInstance instance = instanceRepository.findById(execution.getInstanceId()).orElseThrow();
        assertEquals("COMPLETED", instance.getStatus());
        assertEquals("end-approved", instance.getCurrentNodeId());
    }
}
