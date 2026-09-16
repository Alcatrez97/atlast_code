package com.vi.atlas.workflow.service;

import com.vi.atlas.common.dto.*;
import com.vi.atlas.workflow.entity.EventDefinition;
import com.vi.atlas.workflow.entity.WorkflowInstance;
import com.vi.atlas.workflow.repository.EventDefinitionRepository;
import com.vi.atlas.workflow.repository.WorkflowInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests out-of-order parallel ingestion workflows:
 * Scenario A: Documents submitted first, then CAF submitted.
 * Scenario B: CAF submitted first, then Documents submitted.
 */
@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
@Transactional
@ActiveProfiles("test")
@DirtiesContext
public class OutOfOrderCafSubmissionTest {

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private EventDefinitionRepository eventDefinitionRepository;

    @Autowired
    private WorkflowInstanceRepository instanceRepository;

    @Autowired
    private EventRoutingService eventRoutingService;

    private String workflowKey;

    @BeforeEach
    public void setUp() {
        registerEvent("DOCUMENTS_STAGED");
        registerEvent("CAF_STAGED");

        workflowKey = "CAF_ORCH_" + UUID.randomUUID().toString().substring(0, 8);
        buildOutOfOrderWorkflow(workflowKey);
    }

    private void registerEvent(String eventKey) {
        eventDefinitionRepository.findByEventKey(eventKey)
                .ifPresent(existing -> {
                    eventDefinitionRepository.delete(existing);
                    eventDefinitionRepository.flush();
                });

        EventDefinition eventDef = new EventDefinition();
        eventDef.setId(UUID.randomUUID().toString());
        eventDef.setEventKey(eventKey);
        eventDef.setName(eventKey + " Event");
        eventDef.setDescription("Event: " + eventKey);
        eventDef.setKafkaTopic("caf-events");
        eventDef.setCorrelationKeyPath("payload.orderId");
        eventDef.setActive(true);
        eventDefinitionRepository.saveAndFlush(eventDef);
    }

    private void buildOutOfOrderWorkflow(String key) {
        WorkflowDefinitionDto defDto = new WorkflowDefinitionDto();
        defDto.setKey(key);
        defDto.setName("Out-of-Order CAF Orchestrator " + key);

        WorkflowDefinitionDto createdDef = workflowService.createWorkflowDefinition(defDto);
        String versionId = createdDef.getVersions().get(0).getId();

        List<WorkflowNodeDto> nodes = new ArrayList<>();

        WorkflowNodeDto startNode = new WorkflowNodeDto();
        startNode.setId("start");
        startNode.setType("START");
        startNode.setLabel("Start");
        nodes.add(startNode);

        WorkflowNodeDto splitNode = new WorkflowNodeDto();
        splitNode.setId("split");
        splitNode.setType("PARALLEL");
        splitNode.setLabel("Parallel Split");
        nodes.add(splitNode);

        // Branch 1: Wait for Documents
        WorkflowNodeDto waitDocs = new WorkflowNodeDto();
        waitDocs.setId("wait-docs");
        waitDocs.setType("WAIT_EVENT");
        waitDocs.setLabel("Wait for Documents");
        waitDocs.getData().put("eventType", "DOCUMENTS_STAGED");
        nodes.add(waitDocs);

        // Branch 2: Wait for CAF
        WorkflowNodeDto waitCaf = new WorkflowNodeDto();
        waitCaf.setId("wait-caf");
        waitCaf.setType("WAIT_EVENT");
        waitCaf.setLabel("Wait for CAF");
        waitCaf.getData().put("eventType", "CAF_STAGED");
        nodes.add(waitCaf);

        // Convergence: JOIN (AND)
        WorkflowNodeDto joinNode = new WorkflowNodeDto();
        joinNode.setId("join");
        joinNode.setType("JOIN");
        joinNode.setLabel("Join Merge");
        joinNode.getData().put("joinType", "AND");
        nodes.add(joinNode);

        // Final Node: END
        WorkflowNodeDto endNode = new WorkflowNodeDto();
        endNode.setId("end");
        endNode.setType("END");
        endNode.setLabel("End");
        nodes.add(endNode);

        List<WorkflowEdgeDto> edges = new ArrayList<>();
        edges.add(createEdge("e-start", "start", "split"));
        edges.add(createEdge("e-split-docs", "split", "wait-docs"));
        edges.add(createEdge("e-split-caf", "split", "wait-caf"));
        edges.add(createEdge("e-docs-join", "wait-docs", "join"));
        edges.add(createEdge("e-caf-join", "wait-caf", "join"));
        edges.add(createEdge("e-join-end", "join", "end"));

        WorkflowGraphDto graph = new WorkflowGraphDto();
        graph.setNodes(nodes);
        graph.setEdges(edges);

        workflowService.updateDraftVersion(versionId, graph);
        workflowService.transitionVersionStatus(versionId, "REVIEW");
        workflowService.transitionVersionStatus(versionId, "APPROVED");
        workflowService.transitionVersionStatus(versionId, "PUBLISHED");
    }

    private WorkflowEdgeDto createEdge(String id, String source, String target) {
        WorkflowEdgeDto edge = new WorkflowEdgeDto();
        edge.setId(id);
        edge.setSource(source);
        edge.setTarget(target);
        return edge;
    }

    @Test
    public void testOutOfOrder_DocsFirst_ThenCaf() {
        String orderId = "ORD_DOCS_FIRST_" + UUID.randomUUID().toString().substring(0, 8);

        // 1. Kick off workflow
        ExecutionRequestDto request = new ExecutionRequestDto();
        request.setBusinessKey(orderId);
        Map<String, Object> initContext = new HashMap<>();
        initContext.put("orderId", orderId);
        request.setContext(initContext);

        ExecutionLogDto execLog = executionService.execute(workflowKey, request);
        String instanceId = execLog.getInstanceId();

        // Verify initial state is suspended waiting for both events
        assertEquals("WAITING", execLog.getStatus());
        WorkflowInstance inst = instanceRepository.findById(instanceId).orElseThrow();
        assertEquals("WAITING", inst.getStatus());

        // 2. Documents arrive FIRST
        Map<String, Object> docsPayload = new HashMap<>();
        docsPayload.put("orderId", orderId);
        docsPayload.put("docStagingId", "STG-DOC-001");
        docsPayload.put("docsReady", true);

        eventRoutingService.routeEvent("DOCUMENTS_STAGED", orderId, docsPayload);

        // 3. Verify workflow is STILL WAITING (does not complete prematurely!)
        WorkflowInstance afterDocsInst = instanceRepository.findById(instanceId).orElseThrow();
        assertEquals("WAITING", afterDocsInst.getStatus(), "Workflow must remain in WAITING state after first event");
        assertEquals(true, afterDocsInst.getSerializedContext().get("docsReady"));
        assertEquals("STG-DOC-001", afterDocsInst.getSerializedContext().get("docStagingId"));

        // 4. CAF arrives SECOND
        Map<String, Object> cafPayload = new HashMap<>();
        cafPayload.put("orderId", orderId);
        cafPayload.put("cafStagingId", "STG-CAF-002");
        cafPayload.put("msisdn", "9876543210");
        cafPayload.put("cafReady", true);

        eventRoutingService.routeEvent("CAF_STAGED", orderId, cafPayload);

        // 5. Verify workflow has now COMPLETED successfully
        WorkflowInstance finalInst = instanceRepository.findById(instanceId).orElseThrow();
        assertEquals("COMPLETED", finalInst.getStatus(), "Workflow must complete after both events arrive");
        assertEquals("end", finalInst.getCurrentNodeId());

        // Both payloads are present in the final context
        Map<String, Object> finalContext = finalInst.getSerializedContext();
        assertEquals(true, finalContext.get("docsReady"));
        assertEquals("STG-DOC-001", finalContext.get("docStagingId"));
        assertEquals(true, finalContext.get("cafReady"));
        assertEquals("STG-CAF-002", finalContext.get("cafStagingId"));
        assertEquals("9876543210", finalContext.get("msisdn"));
    }

    @Test
    public void testOutOfOrder_CafFirst_ThenDocs() {
        String orderId = "ORD_CAF_FIRST_" + UUID.randomUUID().toString().substring(0, 8);

        // 1. Kick off workflow
        ExecutionRequestDto request = new ExecutionRequestDto();
        request.setBusinessKey(orderId);
        Map<String, Object> initContext = new HashMap<>();
        initContext.put("orderId", orderId);
        request.setContext(initContext);

        ExecutionLogDto execLog = executionService.execute(workflowKey, request);
        String instanceId = execLog.getInstanceId();

        // Verify initial state is suspended waiting
        assertEquals("WAITING", execLog.getStatus());
        WorkflowInstance inst = instanceRepository.findById(instanceId).orElseThrow();
        assertEquals("WAITING", inst.getStatus());

        // 2. CAF arrives FIRST
        Map<String, Object> cafPayload = new HashMap<>();
        cafPayload.put("orderId", orderId);
        cafPayload.put("cafStagingId", "STG-CAF-009");
        cafPayload.put("msisdn", "9123456780");
        cafPayload.put("cafReady", true);

        eventRoutingService.routeEvent("CAF_STAGED", orderId, cafPayload);

        // 3. Verify workflow is STILL WAITING
        WorkflowInstance afterCafInst = instanceRepository.findById(instanceId).orElseThrow();
        assertEquals("WAITING", afterCafInst.getStatus(), "Workflow must remain in WAITING state after first event");
        assertEquals(true, afterCafInst.getSerializedContext().get("cafReady"));
        assertEquals("STG-CAF-009", afterCafInst.getSerializedContext().get("cafStagingId"));

        // 4. Documents arrive SECOND
        Map<String, Object> docsPayload = new HashMap<>();
        docsPayload.put("orderId", orderId);
        docsPayload.put("docStagingId", "STG-DOC-008");
        docsPayload.put("docsReady", true);

        eventRoutingService.routeEvent("DOCUMENTS_STAGED", orderId, docsPayload);

        // 5. Verify workflow has now COMPLETED successfully
        WorkflowInstance finalInst = instanceRepository.findById(instanceId).orElseThrow();
        assertEquals("COMPLETED", finalInst.getStatus(), "Workflow must complete after both events arrive");
        assertEquals("end", finalInst.getCurrentNodeId());

        // Both payloads are present in the final context
        Map<String, Object> finalContext = finalInst.getSerializedContext();
        assertEquals(true, finalContext.get("cafReady"));
        assertEquals("STG-CAF-009", finalContext.get("cafStagingId"));
        assertEquals("9123456780", finalContext.get("msisdn"));
        assertEquals(true, finalContext.get("docsReady"));
        assertEquals("STG-DOC-008", finalContext.get("docStagingId"));
    }
}
