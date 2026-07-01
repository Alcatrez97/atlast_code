package com.enterprise.atlas.workflow.service;

import com.enterprise.atlas.common.dto.WorkflowDefinitionDto;
import com.enterprise.atlas.common.dto.WorkflowGraphDto;
import com.enterprise.atlas.common.dto.WorkflowVersionDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
@Transactional
@ActiveProfiles("test")
public class WorkflowServiceTest {

    @Autowired
    private WorkflowService workflowService;

    @Test
    public void testCreateWorkflowDefinition() {
        WorkflowDefinitionDto dto = new WorkflowDefinitionDto();
        dto.setKey("ORDER_ORCHESTRATION");
        dto.setName("Order Orchestration Workflow");
        dto.setDescription("Manages customer orders");

        WorkflowDefinitionDto created = workflowService.createWorkflowDefinition(dto);

        assertNotNull(created.getId());
        assertEquals("ORDER_ORCHESTRATION", created.getKey());
        assertEquals("Order Orchestration Workflow", created.getName());
        assertNull(created.getActiveVersion());
        assertEquals(1, created.getVersions().size());

        WorkflowVersionDto version1 = created.getVersions().get(0);
        assertEquals(1, version1.getVersion());
        assertEquals("DRAFT", version1.getStatus());
    }

    @Test
    public void testUpdateDraftVersionAndImmutability() {
        WorkflowDefinitionDto def = new WorkflowDefinitionDto();
        def.setKey("DRAFT_TEST");
        def.setName("Draft Test");
        WorkflowDefinitionDto createdDef = workflowService.createWorkflowDefinition(def);
        String versionId = createdDef.getVersions().get(0).getId();

        // Update should succeed on DRAFT
        WorkflowGraphDto newGraph = new WorkflowGraphDto();
        newGraph.getMetadata().put("author", "Alice");

        WorkflowVersionDto updatedVer = workflowService.updateDraftVersion(versionId, newGraph);
        assertEquals("Alice", updatedVer.getDefinition().getMetadata().get("author"));

        // Transition to REVIEW
        workflowService.transitionVersionStatus(versionId, "REVIEW");

        // Try to update after transitioning out of DRAFT -> should fail
        assertThrows(IllegalStateException.class, () -> {
            workflowService.updateDraftVersion(versionId, newGraph);
        });
    }

    @Test
    public void testGovernanceFlowTransitions() {
        WorkflowDefinitionDto def = new WorkflowDefinitionDto();
        def.setKey("GOVERNANCE_TEST");
        def.setName("Governance Test");
        WorkflowDefinitionDto createdDef = workflowService.createWorkflowDefinition(def);
        String versionId = createdDef.getVersions().get(0).getId();

        // 1. Initial status is DRAFT. Trying to approve directly should fail.
        assertThrows(IllegalStateException.class, () -> {
            workflowService.transitionVersionStatus(versionId, "APPROVED");
        });

        // 2. Draft -> Review
        WorkflowVersionDto verReview = workflowService.transitionVersionStatus(versionId, "REVIEW");
        assertEquals("REVIEW", verReview.getStatus());

        // 3. Review -> Approved
        WorkflowVersionDto verApproved = workflowService.transitionVersionStatus(versionId, "APPROVED");
        assertEquals("APPROVED", verApproved.getStatus());

        // 4. Approved -> Published
        WorkflowVersionDto verPublished = workflowService.transitionVersionStatus(versionId, "PUBLISHED");
        assertEquals("PUBLISHED", verPublished.getStatus());

        // 5. Parent activeVersion should point to v1
        WorkflowDefinitionDto updatedDef = workflowService.getWorkflowDefinition(createdDef.getId());
        assertEquals(1, updatedDef.getActiveVersion());

        // 6. Try to delete a PUBLISHED version -> should fail
        assertThrows(IllegalStateException.class, () -> {
            workflowService.deleteWorkflowVersion(versionId);
        });
    }
}
