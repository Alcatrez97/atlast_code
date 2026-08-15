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
import org.springframework.transaction.annotation.Propagation;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
@Transactional
@ActiveProfiles("test")
@DirtiesContext
public class ParallelExecutionIntegrationTest {

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

    private String workflowKeySync;
    private String workflowKeyAsync;

    @BeforeEach
    public void setUp() {
        // Register predefined EventDefinitions
        registerEvent("PARALLEL_DONE");
        registerEvent("PARALLEL_DONE_ASYNC");

        // 1. Setup workflowKeySync: Emit command in Branch B (resolves wait synchronously)
        workflowKeySync = "PAR_WF_SYNC_" + UUID.randomUUID().toString().substring(0, 8);
        buildParallelWorkflow(workflowKeySync, "PARALLEL_DONE", true);

        // 2. Setup workflowKeyAsync: No emit command (Branch B is a simple script/rule node)
        workflowKeyAsync = "PAR_WF_ASYNC_" + UUID.randomUUID().toString().substring(0, 8);
        buildParallelWorkflow(workflowKeyAsync, "PARALLEL_DONE_ASYNC", false);
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
        eventDef.setDescription("Test parallel event: " + eventKey);
        eventDef.setKafkaTopic("parallel-events");
        eventDef.setCorrelationKeyPath("payload.cafId");
        eventDef.setActive(true);
        eventDefinitionRepository.saveAndFlush(eventDef);
    }

    private void buildParallelWorkflow(String key, String eventType, boolean emitInBranchB) {
        WorkflowDefinitionDto defDto = new WorkflowDefinitionDto();
        defDto.setKey(key);
        defDto.setName("Parallel Flow Test " + key);
        
        WorkflowDefinitionDto createdDef = workflowService.createWorkflowDefinition(defDto);
        String versionId = createdDef.getVersions().get(0).getId();

        List<WorkflowNodeDto> nodes = new ArrayList<>();
        
        WorkflowNodeDto startNode = new WorkflowNodeDto();
        startNode.setId("start");
        startNode.setType("START");
        startNode.setLabel("Start");
        nodes.add(startNode);

        WorkflowNodeDto parallelNode = new WorkflowNodeDto();
        parallelNode.setId("split");
        parallelNode.setType("PARALLEL");
        parallelNode.setLabel("Parallel Split");
        nodes.add(parallelNode);

        // Branch A: Wait Event Node
        WorkflowNodeDto waitNode = new WorkflowNodeDto();
        waitNode.setId("wait-branch");
        waitNode.setType("WAIT_EVENT");
        waitNode.setLabel("Wait For Signal");
        waitNode.getData().put("eventType", eventType);
        nodes.add(waitNode);

        // Branch B: Command Node (synchronous emission) or a simple Task Node
        if (emitInBranchB) {
            WorkflowNodeDto commandNode = new WorkflowNodeDto();
            commandNode.setId("emit-branch");
            commandNode.setType("COMMAND");
            commandNode.setLabel("Emit Completed Signal");
            commandNode.getData().put("commandType", "EMIT_EVENT");
            commandNode.getData().put("eventKey", eventType);
            Map<String, String> payloadMapping = new HashMap<>();
            payloadMapping.put("cafId", "context.cafId");
            commandNode.getData().put("payloadMapping", payloadMapping);
            nodes.add(commandNode);
        } else {
            WorkflowNodeDto commandNode = new WorkflowNodeDto();
            commandNode.setId("emit-branch");
            commandNode.setType("COMMAND");
            commandNode.setLabel("No-op Local Task");
            commandNode.getData().put("commandType", "UPDATE_FORM_STATUS");
            commandNode.getData().put("formStatus", "PROCESSING");
            nodes.add(commandNode);
        }

        WorkflowNodeDto joinNode = new WorkflowNodeDto();
        joinNode.setId("merge");
        joinNode.setType("JOIN");
        joinNode.setLabel("Join Merge");
        nodes.add(joinNode);

        WorkflowNodeDto endNode = new WorkflowNodeDto();
        endNode.setId("end");
        endNode.setType("END");
        endNode.setLabel("End");
        nodes.add(endNode);

        List<WorkflowEdgeDto> edges = new ArrayList<>();
        edges.add(createEdge("e-start", "start", "split"));
        edges.add(createEdge("e-split-wait", "split", "wait-branch"));
        edges.add(createEdge("e-split-emit", "split", "emit-branch"));
        edges.add(createEdge("e-wait-join", "wait-branch", "merge"));
        edges.add(createEdge("e-emit-join", "emit-branch", "merge"));
        edges.add(createEdge("e-join-end", "merge", "end"));

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
    public void testLogicalParallelSplitSynchronousResume() {
        String testCafId = "CAF_PARALLEL_SYNC";

        ExecutionRequestDto request = new ExecutionRequestDto();
        request.setBusinessKey(testCafId);
        Map<String, Object> context = new HashMap<>();
        context.put("cafId", testCafId);
        request.setContext(context);

        // Execute the workflow.
        // Because Branch B emits PARALLEL_DONE synchronously during the traversal of the split,
        // it triggers synchronous resumption of Branch A (wait-branch).
        // Since both branches converge at "merge" (JOIN) node inside the same traversal pass,
        // it resolves JOIN and finishes at "end".
        ExecutionLogDto execLog = executionService.execute(workflowKeySync, request);
        String instanceId = execLog.getInstanceId();

        // 1. Verify instance status completed immediately
        assertEquals("COMPLETED", execLog.getStatus());

        // 2. Verify all nodes in the execution trace were executed successfully
        boolean executedWait = execLog.getExecutionTrace().stream().anyMatch(step -> "wait-branch".equals(step.getNodeId()));
        boolean executedEmit = execLog.getExecutionTrace().stream().anyMatch(step -> "emit-branch".equals(step.getNodeId()));
        boolean executedJoin = execLog.getExecutionTrace().stream().anyMatch(step -> "merge".equals(step.getNodeId()));
        boolean executedEnd  = execLog.getExecutionTrace().stream().anyMatch(step -> "end".equals(step.getNodeId()));

        assertTrue(executedWait, "Wait node should have been executed");
        assertTrue(executedEmit, "Emit node should have been executed");
        assertTrue(executedJoin, "Join node should have been executed");
        assertTrue(executedEnd, "End node should have been executed");

        // 3. Verify database state reflects final status
        WorkflowInstance instance = instanceRepository.findById(instanceId).orElseThrow();
        assertEquals("COMPLETED", instance.getStatus());
        assertEquals("end", instance.getCurrentNodeId());
    }

    @Test
    public void testLogicalParallelSplitAsynchronousResume() {
        String testCafId = "CAF_PARALLEL_ASYNC";

        ExecutionRequestDto request = new ExecutionRequestDto();
        request.setBusinessKey(testCafId);
        Map<String, Object> context = new HashMap<>();
        context.put("cafId", testCafId);
        request.setContext(context);

        // Execute the workflow.
        // Branch B will execute but does not emit the event.
        // Branch A suspends at WAIT_EVENT.
        // JOIN Gateway blocks because Branch A has not arrived.
        ExecutionLogDto execLog = executionService.execute(workflowKeyAsync, request);
        String instanceId = execLog.getInstanceId();

        // 1. Verify instance is suspended waiting for the event
        assertEquals("WAITING", execLog.getStatus());
        assertEquals("wait-branch", execLog.getOutcomeNodeId());

        WorkflowInstance instance = instanceRepository.findById(instanceId).orElseThrow();
        assertEquals("WAITING", instance.getStatus());

        // 2. Verify wait subscription is active in database
        assertNotNull(instance.getCurrentNodeId());

        // 3. Route event externally to trigger resumption
        Map<String, Object> eventPayload = new HashMap<>();
        eventPayload.put("cafId", testCafId);
        eventPayload.put("amount", 95000);

        eventRoutingService.routeEvent("PARALLEL_DONE_ASYNC", testCafId, eventPayload);

        // 4. Verify workflow instance has now completed successfully
        WorkflowInstance finalInstance = instanceRepository.findById(instanceId).orElseThrow();
        assertEquals("COMPLETED", finalInstance.getStatus());
        assertEquals("end", finalInstance.getCurrentNodeId());
        
        // Verify event payload merged into workflow context
        assertEquals(95000, finalInstance.getSerializedContext().get("amount"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void testAsyncCommandNodeExecution() throws Exception {
        String workflowKey = "ASYNC_CMD_WF_" + UUID.randomUUID().toString().substring(0, 8);
        
        WorkflowDefinitionDto defDto = new WorkflowDefinitionDto();
        defDto.setKey(workflowKey);
        defDto.setName("Async Command Flow Test " + workflowKey);
        WorkflowDefinitionDto createdDef = workflowService.createWorkflowDefinition(defDto);
        String versionId = createdDef.getVersions().get(0).getId();

        List<WorkflowNodeDto> nodes = new ArrayList<>();
        
        WorkflowNodeDto startNode = new WorkflowNodeDto();
        startNode.setId("start");
        startNode.setType("START");
        startNode.setLabel("Start");
        nodes.add(startNode);

        WorkflowNodeDto cmdNode = new WorkflowNodeDto();
        cmdNode.setId("cmd-async");
        cmdNode.setType("COMMAND");
        cmdNode.setLabel("Asynchronous Task");
        cmdNode.getData().put("commandType", "UPDATE_FORM_STATUS");
        cmdNode.getData().put("formStatus", "COMPLETED_VIA_ASYNC");
        cmdNode.getData().put("executionMode", "ASYNC");
        nodes.add(cmdNode);

        WorkflowNodeDto endNode = new WorkflowNodeDto();
        endNode.setId("end");
        endNode.setType("END");
        endNode.setLabel("End");
        nodes.add(endNode);

        List<WorkflowEdgeDto> edges = new ArrayList<>();
        edges.add(createEdge("e1", "start", "cmd-async"));
        edges.add(createEdge("e2", "cmd-async", "end"));

        WorkflowGraphDto graph = new WorkflowGraphDto();
        graph.setNodes(nodes);
        graph.setEdges(edges);

        workflowService.updateDraftVersion(versionId, graph);
        workflowService.transitionVersionStatus(versionId, "REVIEW");
        workflowService.transitionVersionStatus(versionId, "APPROVED");
        workflowService.transitionVersionStatus(versionId, "PUBLISHED");

        ExecutionRequestDto request = new ExecutionRequestDto();
        request.setBusinessKey("CAF_ASYNC_CMD_" + UUID.randomUUID().toString().substring(0, 8));
        Map<String, Object> context = new HashMap<>();
        context.put("cafId", "CAF_ASYNC_CMD_ID");
        request.setContext(context);

        ExecutionLogDto execLog = executionService.execute(workflowKey, request);
        String instanceId = execLog.getInstanceId();

        assertEquals("WAITING", execLog.getStatus());
        assertEquals("cmd-async", execLog.getOutcomeNodeId());

        WorkflowInstance instance = instanceRepository.findById(instanceId).orElseThrow();
        assertEquals("WAITING", instance.getStatus());

        long expiry = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < expiry) {
            instance = instanceRepository.findById(instanceId).orElseThrow();
            if ("COMPLETED".equalsIgnoreCase(instance.getStatus())) {
                break;
            }
            Thread.sleep(500);
        }

        assertEquals("COMPLETED", instance.getStatus());
        assertEquals("end", instance.getCurrentNodeId());

        Map<String, Object> finalContext = instance.getSerializedContext();
        assertNotNull(finalContext.get("commandOutputs"));
        
        Map<?, ?> outputs = (Map<?, ?>) finalContext.get("commandOutputs");
        Map<?, ?> cmdOutput = (Map<?, ?>) outputs.get("cmd-async");
        assertNotNull(cmdOutput);
    }
}
