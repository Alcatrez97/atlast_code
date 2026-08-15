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

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
@Transactional
@ActiveProfiles("test")
@DirtiesContext
public class CommandWaitKafkaIntegrationTest {

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private EventRoutingService eventRoutingService;

    @Autowired
    private EventDefinitionRepository eventDefinitionRepository;

    @Autowired
    private WorkflowInstanceRepository instanceRepository;

    private String workflowKey;

    @BeforeEach
    public void setUp() {
        // 1. Register event definition for KAFKA_PAYMENT
        eventDefinitionRepository.findByEventKey("KAFKA_PAYMENT")
                .ifPresent(existing -> {
                    eventDefinitionRepository.delete(existing);
                    eventDefinitionRepository.flush();
                });

        EventDefinition eventDef = new EventDefinition();
        eventDef.setId(UUID.randomUUID().toString());
        eventDef.setEventKey("KAFKA_PAYMENT");
        eventDef.setName("Kafka Inbound Payment Event");
        eventDef.setKafkaTopic("payment-events");
        eventDef.setCorrelationKeyPath("businessKey");
        eventDef.setActive(true);
        eventDefinitionRepository.saveAndFlush(eventDef);

        // 2. Build workflow: START -> WAIT_EVENT (with selective payload mapping) -> RULE -> END
        workflowKey = "CMD_WAIT_KAFKA_" + UUID.randomUUID().toString().substring(0, 8);
        WorkflowDefinitionDto defDto = new WorkflowDefinitionDto();
        defDto.setKey(workflowKey);
        defDto.setName("Command Wait Kafka Workflow");

        WorkflowDefinitionDto createdDef = workflowService.createWorkflowDefinition(defDto);
        String versionId = createdDef.getVersions().get(0).getId();

        List<WorkflowNodeDto> nodes = new ArrayList<>();

        WorkflowNodeDto startNode = new WorkflowNodeDto();
        startNode.setId("start-node");
        startNode.setType("START");
        startNode.setLabel("Start");
        nodes.add(startNode);

        WorkflowNodeDto waitNode = new WorkflowNodeDto();
        waitNode.setId("wait-payment-event");
        waitNode.setType("WAIT_EVENT");
        waitNode.setLabel("Wait For Payment Event");
        waitNode.getData().put("eventType", "KAFKA_PAYMENT");

        // Selective Payload Mapping: extract specific fields from Kafka payload into context
        Map<String, String> selectiveMapping = new HashMap<>();
        selectiveMapping.put("kafkaTxId", "extractedTxId");
        selectiveMapping.put("rawAmount", "extractedAmount");
        waitNode.getData().put("payloadMapping", selectiveMapping);
        nodes.add(waitNode);

        WorkflowNodeDto ruleNode = new WorkflowNodeDto();
        ruleNode.setId("verify-payload-rule");
        ruleNode.setType("RULE");
        ruleNode.setLabel("Verify Extracted Context Rule");
        // SpEL checking the mapped context variables
        ruleNode.getData().put("expression", "context['extractedTxId'] != null && context['extractedAmount'] > 5000");
        nodes.add(ruleNode);

        WorkflowNodeDto endNode = new WorkflowNodeDto();
        endNode.setId("end-node");
        endNode.setType("END");
        endNode.setLabel("End");
        nodes.add(endNode);

        List<WorkflowEdgeDto> edges = new ArrayList<>();
        WorkflowEdgeDto e1 = new WorkflowEdgeDto();
        e1.setId("e-1");
        e1.setSource("start-node");
        e1.setTarget("wait-payment-event");
        edges.add(e1);

        WorkflowEdgeDto e2 = new WorkflowEdgeDto();
        e2.setId("e-2");
        e2.setSource("wait-payment-event");
        e2.setTarget("verify-payload-rule");
        edges.add(e2);

        WorkflowEdgeDto e3 = new WorkflowEdgeDto();
        e3.setId("e-3");
        e3.setSource("verify-payload-rule");
        e3.setTarget("end-node");
        e3.setLabel("true");
        e3.getData().put("condition", "true");
        edges.add(e3);

        WorkflowGraphDto graphDto = new WorkflowGraphDto();
        graphDto.setNodes(nodes);
        graphDto.setEdges(edges);

        workflowService.updateDraftVersion(versionId, graphDto);
        workflowService.transitionVersionStatus(versionId, "REVIEW");
        workflowService.transitionVersionStatus(versionId, "APPROVED");
        workflowService.transitionVersionStatus(versionId, "PUBLISHED");
    }

    @Test
    public void testCommandWaitPatternWithKafkaPayloadMapping() {
        String businessKey = "CAF_KAFKA_" + UUID.randomUUID().toString().substring(0, 8);

        // 1. Execute workflow â€” should suspend at WAIT_EVENT
        ExecutionRequestDto request = new ExecutionRequestDto();
        request.setContextId(businessKey);
        request.setBusinessKey(businessKey);
        request.setContext(Map.of("initialParam", "initVal"));

        ExecutionLogDto logDto = executionService.execute(workflowKey, request);
        assertNotNull(logDto);
        assertEquals("WAITING", logDto.getStatus());

        WorkflowInstance instance = instanceRepository.findById(logDto.getInstanceId()).orElseThrow();
        assertEquals("WAITING", instance.getStatus());

        // 2. Simulate Kafka Event arrival with payload containing multiple keys
        Map<String, Object> kafkaPayload = new HashMap<>();
        kafkaPayload.put("kafkaTxId", "TX-998877");
        kafkaPayload.put("rawAmount", 7500.0);
        kafkaPayload.put("unmappedField", "shouldBeIgnored");

        eventRoutingService.routeEvent("KAFKA_PAYMENT", businessKey, kafkaPayload);

        // 3. Verify workflow resumed, completed, and context received mapped variables
        WorkflowInstance completedInstance = instanceRepository.findById(logDto.getInstanceId()).orElseThrow();
        assertEquals("COMPLETED", completedInstance.getStatus());

        Map<String, Object> finalContext = completedInstance.getSerializedContext();
        assertNotNull(finalContext);
        assertEquals("TX-998877", finalContext.get("extractedTxId"));
        assertEquals(7500.0, finalContext.get("extractedAmount"));
        assertNull(finalContext.get("unmappedField"), "Unmapped fields should not leak into context when payloadMapping is specified");
    }
}
