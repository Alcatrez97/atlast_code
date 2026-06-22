package com.enterprise.atlas.workflow.service;

import com.enterprise.atlas.common.dto.*;
import com.enterprise.atlas.workflow.entity.EventDefinition;
import com.enterprise.atlas.workflow.repository.EventDefinitionRepository;
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
public class WorkflowValidationIntegrationTest {

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private EventDefinitionRepository eventDefinitionRepository;

    @BeforeEach
    public void setUp() {
        eventDefinitionRepository.deleteAll();
        eventDefinitionRepository.flush();
    }

    @Test
    public void testWaitEventValidationDraftToReview() {
        // 1. Create event definition: INACTIVE
        EventDefinition inactiveDef = new EventDefinition();
        inactiveDef.setId(UUID.randomUUID().toString());
        inactiveDef.setEventKey("INACTIVE_EVENT");
        inactiveDef.setName("Inactive Event");
        inactiveDef.setActive(false);
        eventDefinitionRepository.saveAndFlush(inactiveDef);

        // 2. Create draft workflow containing unregistered WAIT_EVENT
        String workflowKey = "VALIDATION_TEST_WF_" + UUID.randomUUID().toString().substring(0, 8);
        WorkflowDefinitionDto defDto = new WorkflowDefinitionDto();
        defDto.setKey(workflowKey);
        defDto.setName("Validation Test Workflow");
        WorkflowDefinitionDto createdDef = workflowService.createWorkflowDefinition(defDto);
        String versionId = createdDef.getVersions().get(0).getId();

        List<WorkflowNodeDto> nodes = new ArrayList<>();
        WorkflowNodeDto startNode = new WorkflowNodeDto();
        startNode.setId("start");
        startNode.setType("START");
        nodes.add(startNode);

        WorkflowNodeDto waitNode = new WorkflowNodeDto();
        waitNode.setId("wait-unregistered");
        waitNode.setType("WAIT_EVENT");
        waitNode.getData().put("eventType", "UNREGISTERED_EVENT");
        nodes.add(waitNode);

        WorkflowGraphDto graph = new WorkflowGraphDto();
        graph.setNodes(nodes);
        workflowService.updateDraftVersion(versionId, graph);

        // Assert transitioning to REVIEW throws IllegalArgumentException due to unregistered event type
        Exception exception1 = assertThrows(IllegalArgumentException.class, () -> {
            workflowService.transitionVersionStatus(versionId, "REVIEW");
        });
        assertTrue(exception1.getMessage().contains("references unregistered event type: 'UNREGISTERED_EVENT'"));

        // 3. Update WAIT_EVENT to reference inactive event
        waitNode.getData().put("eventType", "INACTIVE_EVENT");
        workflowService.updateDraftVersion(versionId, graph);

        // Assert transitioning to REVIEW throws IllegalArgumentException due to inactive event type
        Exception exception2 = assertThrows(IllegalArgumentException.class, () -> {
            workflowService.transitionVersionStatus(versionId, "REVIEW");
        });
        assertTrue(exception2.getMessage().contains("references inactive event type: 'INACTIVE_EVENT'"));

        // 4. Create active event definition and update WAIT_EVENT
        EventDefinition activeDef = new EventDefinition();
        activeDef.setId(UUID.randomUUID().toString());
        activeDef.setEventKey("ACTIVE_EVENT");
        activeDef.setName("Active Event");
        activeDef.setActive(true);
        eventDefinitionRepository.saveAndFlush(activeDef);

        waitNode.getData().put("eventType", "ACTIVE_EVENT");
        workflowService.updateDraftVersion(versionId, graph);

        // Transition should now succeed
        WorkflowVersionDto transitioned = workflowService.transitionVersionStatus(versionId, "REVIEW");
        assertEquals("REVIEW", transitioned.getStatus());
    }

    @Test
    public void testEmitEventCommandValidationDraftToReview() {
        // 1. Create draft workflow containing unregistered EMIT_EVENT command
        String workflowKey = "EMIT_VALIDATION_WF_" + UUID.randomUUID().toString().substring(0, 8);
        WorkflowDefinitionDto defDto = new WorkflowDefinitionDto();
        defDto.setKey(workflowKey);
        defDto.setName("Emit Validation Test Workflow");
        WorkflowDefinitionDto createdDef = workflowService.createWorkflowDefinition(defDto);
        String versionId = createdDef.getVersions().get(0).getId();

        List<WorkflowNodeDto> nodes = new ArrayList<>();
        WorkflowNodeDto startNode = new WorkflowNodeDto();
        startNode.setId("start");
        startNode.setType("START");
        nodes.add(startNode);

        WorkflowNodeDto commandNode = new WorkflowNodeDto();
        commandNode.setId("command");
        commandNode.setType("COMMAND");
        commandNode.getData().put("commandType", "EMIT_EVENT");
        commandNode.getData().put("eventKey", "UNREGISTERED_EMIT");
        nodes.add(commandNode);

        WorkflowGraphDto graph = new WorkflowGraphDto();
        graph.setNodes(nodes);
        workflowService.updateDraftVersion(versionId, graph);

        // Assert transitioning throws IllegalArgumentException due to unregistered event key
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            workflowService.transitionVersionStatus(versionId, "REVIEW");
        });
        assertTrue(exception.getMessage().contains("references unregistered event key: 'UNREGISTERED_EMIT'"));

        // 2. Create active event definition and update command
        EventDefinition activeDef = new EventDefinition();
        activeDef.setId(UUID.randomUUID().toString());
        activeDef.setEventKey("REGISTERED_EMIT");
        activeDef.setName("Registered Emit");
        activeDef.setActive(true);
        eventDefinitionRepository.saveAndFlush(activeDef);

        commandNode.getData().put("eventKey", "REGISTERED_EMIT");
        workflowService.updateDraftVersion(versionId, graph);

        // Transition should now succeed
        WorkflowVersionDto transitioned = workflowService.transitionVersionStatus(versionId, "REVIEW");
        assertEquals("REVIEW", transitioned.getStatus());
    }
}
