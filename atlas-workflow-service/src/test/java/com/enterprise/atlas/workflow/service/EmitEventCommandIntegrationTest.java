package com.enterprise.atlas.workflow.service;

import com.enterprise.atlas.common.dto.*;
import com.enterprise.atlas.workflow.entity.EventDefinition;
import com.enterprise.atlas.workflow.entity.WorkflowInstance;
import com.enterprise.atlas.workflow.repository.EventDefinitionRepository;
import com.enterprise.atlas.workflow.repository.WorkflowInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
@Transactional
@ActiveProfiles("test")
@DirtiesContext
public class EmitEventCommandIntegrationTest {

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private EventDefinitionRepository eventDefinitionRepository;

    @Autowired
    private WorkflowInstanceRepository instanceRepository;

    private String waitWorkflowKey;
    private String emitWorkflowKey;

    @BeforeEach
    public void setUp() {
        // 1. Register predefined EventDefinition in the registry
        eventDefinitionRepository.findByEventKey("PAYMENT_RECEIVED")
                .ifPresent(existing -> {
                    eventDefinitionRepository.delete(existing);
                    eventDefinitionRepository.flush();
                });

        EventDefinition eventDef = new EventDefinition();
        eventDef.setId(UUID.randomUUID().toString());
        eventDef.setEventKey("PAYMENT_RECEIVED");
        eventDef.setName("Customer Payment Event");
        eventDef.setDescription("Test event emission");
        eventDef.setKafkaTopic("billing-events");
        eventDef.setCorrelationKeyPath("payload.cafId");
        eventDef.setActive(true);
        eventDefinitionRepository.saveAndFlush(eventDef);

        // 2. Build and publish WAIT workflow: START -> WAIT_EVENT -> END
        waitWorkflowKey = "WAIT_WF_" + UUID.randomUUID().toString().substring(0, 8);
        WorkflowDefinitionDto waitDefDto = new WorkflowDefinitionDto();
        waitDefDto.setKey(waitWorkflowKey);
        waitDefDto.setName("Wait Event Workflow");
        
        WorkflowDefinitionDto createdWaitDef = workflowService.createWorkflowDefinition(waitDefDto);
        String waitVersionId = createdWaitDef.getVersions().get(0).getId();

        List<WorkflowNodeDto> waitNodes = new ArrayList<>();
        WorkflowNodeDto startNode = new WorkflowNodeDto();
        startNode.setId("start-wait");
        startNode.setType("START");
        startNode.setLabel("Start");
        waitNodes.add(startNode);

        WorkflowNodeDto waitNode = new WorkflowNodeDto();
        waitNode.setId("wait-payment");
        waitNode.setType("WAIT_EVENT");
        waitNode.setLabel("Wait For Payment");
        waitNode.getData().put("eventType", "PAYMENT_RECEIVED");
        waitNodes.add(waitNode);

        WorkflowNodeDto endNode = new WorkflowNodeDto();
        endNode.setId("end-wait");
        endNode.setType("END");
        endNode.setLabel("End");
        waitNodes.add(endNode);

        List<WorkflowEdgeDto> waitEdges = new ArrayList<>();
        WorkflowEdgeDto e1 = new WorkflowEdgeDto();
        e1.setId("we-1");
        e1.setSource("start-wait");
        e1.setTarget("wait-payment");
        waitEdges.add(e1);

        WorkflowEdgeDto e2 = new WorkflowEdgeDto();
        e2.setId("we-2");
        e2.setSource("wait-payment");
        e2.setTarget("end-wait");
        waitEdges.add(e2);

        WorkflowGraphDto waitGraph = new WorkflowGraphDto();
        waitGraph.setNodes(waitNodes);
        waitGraph.setEdges(waitEdges);

        workflowService.updateDraftVersion(waitVersionId, waitGraph);
        workflowService.transitionVersionStatus(waitVersionId, "REVIEW");
        workflowService.transitionVersionStatus(waitVersionId, "APPROVED");
        workflowService.transitionVersionStatus(waitVersionId, "PUBLISHED");

        // 3. Build and publish EMIT workflow: START -> COMMAND (EMIT_EVENT) -> END
        emitWorkflowKey = "EMIT_WF_" + UUID.randomUUID().toString().substring(0, 8);
        WorkflowDefinitionDto emitDefDto = new WorkflowDefinitionDto();
        emitDefDto.setKey(emitWorkflowKey);
        emitDefDto.setName("Emit Event Workflow");

        WorkflowDefinitionDto createdEmitDef = workflowService.createWorkflowDefinition(emitDefDto);
        String emitVersionId = createdEmitDef.getVersions().get(0).getId();

        List<WorkflowNodeDto> emitNodes = new ArrayList<>();
        WorkflowNodeDto startEmitNode = new WorkflowNodeDto();
        startEmitNode.setId("start-emit");
        startEmitNode.setType("START");
        startEmitNode.setLabel("Start");
        emitNodes.add(startEmitNode);

        WorkflowNodeDto commandNode = new WorkflowNodeDto();
        commandNode.setId("command-emit");
        commandNode.setType("COMMAND");
        commandNode.setLabel("Emit Payment Received Event");
        commandNode.getData().put("commandType", "EMIT_EVENT");
        commandNode.getData().put("eventKey", "PAYMENT_RECEIVED");
        
        // Setup payload mapping
        Map<String, String> payloadMapping = new HashMap<>();
        payloadMapping.put("cafId", "context.targetCafId");
        payloadMapping.put("amount", "context.val");
        commandNode.getData().put("payloadMapping", payloadMapping);
        emitNodes.add(commandNode);

        WorkflowNodeDto endEmitNode = new WorkflowNodeDto();
        endEmitNode.setId("end-emit");
        endEmitNode.setType("END");
        endEmitNode.setLabel("End");
        emitNodes.add(endEmitNode);

        List<WorkflowEdgeDto> emitEdges = new ArrayList<>();
        WorkflowEdgeDto ee1 = new WorkflowEdgeDto();
        ee1.setId("ee-1");
        ee1.setSource("start-emit");
        ee1.setTarget("command-emit");
        emitEdges.add(ee1);

        WorkflowEdgeDto ee2 = new WorkflowEdgeDto();
        ee2.setId("ee-2");
        ee2.setSource("command-emit");
        ee2.setTarget("end-emit");
        emitEdges.add(ee2);

        WorkflowGraphDto emitGraph = new WorkflowGraphDto();
        emitGraph.setNodes(emitNodes);
        emitGraph.setEdges(emitEdges);

        workflowService.updateDraftVersion(emitVersionId, emitGraph);
        workflowService.transitionVersionStatus(emitVersionId, "REVIEW");
        workflowService.transitionVersionStatus(emitVersionId, "APPROVED");
        workflowService.transitionVersionStatus(emitVersionId, "PUBLISHED");
    }

    @Test
    public void testEmitEventResumesWaitingWorkflowInstance() {
        String targetCafId = "CAF_TEST_EMIT_99";

        // 1. Start wait workflow instance (it should suspend at WAIT_EVENT)
        ExecutionRequestDto waitReq = new ExecutionRequestDto();
        waitReq.setBusinessKey(targetCafId);
        waitReq.setContext(new HashMap<>());
        ExecutionLogDto waitExec = executionService.execute(waitWorkflowKey, waitReq);
        String waitInstanceId = waitExec.getInstanceId();

        // Verify it is suspended
        assertEquals("WAITING", waitExec.getStatus());
        assertEquals("wait-payment", waitExec.getOutcomeNodeId());

        // 2. Start emit workflow instance (it executes the COMMAND node and emits the event)
        ExecutionRequestDto emitReq = new ExecutionRequestDto();
        emitReq.setBusinessKey("CAF_EMITTER");
        Map<String, Object> emitCtx = new HashMap<>();
        emitCtx.put("targetCafId", targetCafId);
        emitCtx.put("val", 25000);
        emitReq.setContext(emitCtx);

        ExecutionLogDto emitExec = executionService.execute(emitWorkflowKey, emitReq);
        assertEquals("COMPLETED", emitExec.getStatus());

        // 3. Verify target wait workflow instance has resumed and completed successfully!
        WorkflowInstance waitInstance = instanceRepository.findById(waitInstanceId).orElseThrow();
        assertEquals("COMPLETED", waitInstance.getStatus());
        assertEquals("end-wait", waitInstance.getCurrentNodeId());

        // Verify serialized context received the values mapped
        Map<String, Object> finalContext = waitInstance.getSerializedContext();
        assertEquals(25000, finalContext.get("amount"));
        assertEquals(targetCafId, finalContext.get("cafId"));
    }
}
