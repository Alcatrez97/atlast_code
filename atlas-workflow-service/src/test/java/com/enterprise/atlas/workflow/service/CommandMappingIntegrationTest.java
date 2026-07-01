package com.enterprise.atlas.workflow.service;

import com.enterprise.atlas.common.dto.*;
import com.enterprise.atlas.workflow.entity.WorkflowInstance;
import com.enterprise.atlas.workflow.repository.WorkflowInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
    }
)
@ActiveProfiles("test")
@DirtiesContext
public class CommandMappingIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private WorkflowInstanceRepository instanceRepository;

    private String workflowKey;
    private String versionId;

    @org.springframework.boot.test.context.TestConfiguration
    public static class TestConfig {
        @org.springframework.context.annotation.Bean
        public MockApiController mockApiController() {
            return new MockApiController();
        }
    }

    @RestController
    @RequestMapping("/test-mock")
    public static class MockApiController {
        @PostMapping("/check-risk")
        public Map<String, Object> checkRisk(@RequestBody Map<String, Object> request) {
            Double amount = null;
            if (request.get("amount") instanceof Number) {
                amount = ((Number) request.get("amount")).doubleValue();
            }
            String category = (String) request.get("riskCategory");

            Map<String, Object> response = new HashMap<>();
            if (amount != null && amount > 1000 && "HIGH".equalsIgnoreCase(category)) {
                response.put("riskScore", 95);
                response.put("decision", "REJECTED");
            } else {
                response.put("riskScore", 40);
                response.put("decision", "APPROVED");
            }
            return response;
        }
    }

    @BeforeEach
    public void setUp() {
        workflowKey = "REST_MAPPING_WF_" + UUID.randomUUID().toString().substring(0, 8);
        WorkflowDefinitionDto defDto = new WorkflowDefinitionDto();
        defDto.setKey(workflowKey);
        defDto.setName("REST Mapping Test");
        WorkflowDefinitionDto createdDef = workflowService.createWorkflowDefinition(defDto);
        versionId = createdDef.getVersions().get(0).getId();
    }

    @Test
    public void testHttpRestCommandInputOutputMappingAndDecision() {
        // Build graph: START -> COMMAND (REST) -> DECISION -> END (Approved) / END (Rejected)
        List<WorkflowNodeDto> nodes = new ArrayList<>();

        WorkflowNodeDto startNode = new WorkflowNodeDto();
        startNode.setId("start");
        startNode.setType("START");
        startNode.setLabel("Start");
        nodes.add(startNode);

        WorkflowNodeDto restCommandNode = new WorkflowNodeDto();
        restCommandNode.setId("rest-command");
        restCommandNode.setType("COMMAND");
        restCommandNode.setLabel("Call Mock Risk API");
        restCommandNode.getData().put("commandType", "REST");
        restCommandNode.getData().put("url", "http://localhost:" + port + "/test-mock/check-risk");
        restCommandNode.getData().put("method", "POST");

        // Input mapping: map SpEL values of global context into REST command inputs
        Map<String, String> inputMapping = new HashMap<>();
        inputMapping.put("context.val", "amount");
        inputMapping.put("context.category", "riskCategory");
        restCommandNode.getData().put("inputMapping", inputMapping);

        // Output mapping: map REST response keys back to global context keys
        Map<String, String> outputMapping = new HashMap<>();
        outputMapping.put("riskScore", "context.riskScore");
        outputMapping.put("decision", "context.decisionOutcome");
        restCommandNode.getData().put("outputMapping", outputMapping);
        nodes.add(restCommandNode);

        WorkflowNodeDto decisionNode = new WorkflowNodeDto();
        decisionNode.setId("decision");
        decisionNode.setType("DECISION");
        decisionNode.setLabel("Decide Risk");
        decisionNode.getData().put("decisionField", "decisionOutcome");
        nodes.add(decisionNode);

        WorkflowNodeDto approvedEndNode = new WorkflowNodeDto();
        approvedEndNode.setId("end-approved");
        approvedEndNode.setType("END");
        approvedEndNode.setLabel("Approved Out");
        nodes.add(approvedEndNode);

        WorkflowNodeDto rejectedEndNode = new WorkflowNodeDto();
        rejectedEndNode.setId("end-rejected");
        rejectedEndNode.setType("END");
        rejectedEndNode.setLabel("Rejected Out");
        nodes.add(rejectedEndNode);

        List<WorkflowEdgeDto> edges = new ArrayList<>();
        
        WorkflowEdgeDto e1 = new WorkflowEdgeDto();
        e1.setId("e1");
        e1.setSource("start");
        e1.setTarget("rest-command");
        edges.add(e1);

        WorkflowEdgeDto e2 = new WorkflowEdgeDto();
        e2.setId("e2");
        e2.setSource("rest-command");
        e2.setTarget("decision");
        edges.add(e2);

        WorkflowEdgeDto eApproved = new WorkflowEdgeDto();
        eApproved.setId("e-approved");
        eApproved.setSource("decision");
        eApproved.setTarget("end-approved");
        eApproved.setLabel("Approved Path");
        eApproved.getData().put("condition", "context.decisionOutcome == 'APPROVED'");
        edges.add(eApproved);

        WorkflowEdgeDto eRejected = new WorkflowEdgeDto();
        eRejected.setId("e-rejected");
        eRejected.setSource("decision");
        eRejected.setTarget("end-rejected");
        eRejected.setLabel("Rejected Path");
        eRejected.getData().put("condition", "context.decisionOutcome == 'REJECTED'");
        edges.add(eRejected);

        WorkflowGraphDto graph = new WorkflowGraphDto();
        graph.setNodes(nodes);
        graph.setEdges(edges);

        workflowService.updateDraftVersion(versionId, graph);
        workflowService.transitionVersionStatus(versionId, "REVIEW");
        workflowService.transitionVersionStatus(versionId, "APPROVED");
        workflowService.transitionVersionStatus(versionId, "PUBLISHED");

        // Executing case 1: High risk (amount > 1000 and category is HIGH)
        ExecutionRequestDto req1 = new ExecutionRequestDto();
        req1.setContextId("C1");
        Map<String, Object> ctx1 = new HashMap<>();
        ctx1.put("val", 5000);
        ctx1.put("category", "HIGH");
        req1.setContext(ctx1);

        ExecutionLogDto exec1 = executionService.execute(workflowKey, req1);
        assertEquals("COMPLETED", exec1.getStatus());
        assertEquals("end-rejected", exec1.getOutcomeNodeId());

        WorkflowInstance instance1 = instanceRepository.findById(exec1.getInstanceId()).orElseThrow();
        Map<String, Object> finalCtx1 = instance1.getSerializedContext();
        assertEquals(95, finalCtx1.get("riskScore"));
        assertEquals("REJECTED", finalCtx1.get("decisionOutcome"));

        // Executing case 2: Low risk (amount <= 1000)
        ExecutionRequestDto req2 = new ExecutionRequestDto();
        req2.setContextId("C2");
        Map<String, Object> ctx2 = new HashMap<>();
        ctx2.put("val", 500);
        ctx2.put("category", "HIGH");
        req2.setContext(ctx2);

        ExecutionLogDto exec2 = executionService.execute(workflowKey, req2);
        assertEquals("COMPLETED", exec2.getStatus());
        assertEquals("end-approved", exec2.getOutcomeNodeId());

        WorkflowInstance instance2 = instanceRepository.findById(exec2.getInstanceId()).orElseThrow();
        Map<String, Object> finalCtx2 = instance2.getSerializedContext();
        assertEquals(40, finalCtx2.get("riskScore"));
        assertEquals("APPROVED", finalCtx2.get("decisionOutcome"));
    }
}
