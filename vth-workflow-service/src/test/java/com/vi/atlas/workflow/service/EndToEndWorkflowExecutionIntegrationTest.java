package com.vi.atlas.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vi.atlas.common.dto.*;
import com.vi.atlas.workflow.entity.EntityRevert;
import com.vi.atlas.workflow.entity.RevertStatus;
import com.vi.atlas.workflow.entity.WorkflowInstance;
import com.vi.atlas.workflow.event.listener.KafkaEventListener;
import com.vi.atlas.workflow.repository.EntityRevertRepository;
import com.vi.atlas.workflow.repository.RevertStatusRepository;
import com.vi.atlas.workflow.repository.WorkflowInstanceRepository;
import com.vi.atlas.workflow.service.context.ContextSchemaService;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive End-to-End Integration Test verifying all 7 criteria:
 * 1. Defining workflow with Bucket node, Rule node, Command node, and ContextSchema.
 * 2. Creating & executing workflow via REST API method AND Kafka trigger method.
 * 3. Primary key cafId from context is picked up correctly by downstream nodes (Bucket node, etc.).
 * 4. Entries in postpaid_entity_revert table happen correctly with circleId, custom pending status, and bucketId.
 * 5. Workflow gets resumed correctly when suspended on a bucket, triggered via Kafka payload.
 * 6. Workflow finishes with COMPLETED status.
 * 7. Command node (MQ - Send to Kafka) maps outgoing payload from context schema and publishes to Kafka.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
    }
)
@ActiveProfiles("test")
@DirtiesContext
public class EndToEndWorkflowExecutionIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TestConfiguration
    public static class TestConfig {
        @Bean
        @Primary
        @SuppressWarnings("unchecked")
        public KafkaTemplate<String, Object> kafkaTemplate() {
            return mock(KafkaTemplate.class);
        }

        @Bean
        public KafkaEventListener kafkaEventListener() {
            return new KafkaEventListener();
        }
    }

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private ContextSchemaService contextSchemaService;

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private KafkaEventListener kafkaEventListener;

    @Autowired
    private WorkflowInstanceRepository instanceRepository;

    @Autowired
    private EntityRevertRepository entityRevertRepository;

    @Autowired
    private RevertStatusRepository revertStatusRepository;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    private String workflowKey;
    private String versionId;

    @BeforeEach
    public void setUp() {
        reset(kafkaTemplate);
        workflowKey = "E2E_WF_" + UUID.randomUUID().toString().substring(0, 8);

        // =====================================================================
        // CRITERION 1: Define ContextSchema with cafId as primary key
        // =====================================================================
        ContextSchemaDto schema = new ContextSchemaDto();
        schema.setWorkflowKey(workflowKey);
        schema.setName("E2E Order Processing Schema");
        schema.setDescription("Validates cafId primary key and circleId propagation");
        schema.setContextIdField("cafId"); // designate cafId as primary context identifier
        schema.setTargetTable("POSTPAID_ONBOARD_CAF");
        schema.setTargetPkColumn("caf_id");
        schema.setTargetStatusColumn("form_status");

        ContextFieldDto cafIdField = new ContextFieldDto();
        cafIdField.setFieldKey("cafId");
        cafIdField.setDisplayName("Primary CAF Identifier");
        cafIdField.setFieldType("NUMBER");
        cafIdField.setRequired(true);

        ContextFieldDto circleIdField = new ContextFieldDto();
        circleIdField.setFieldKey("circleId");
        circleIdField.setDisplayName("Telecom Circle ID");
        circleIdField.setFieldType("NUMBER");
        circleIdField.setRequired(true);

        ContextFieldDto riskScoreField = new ContextFieldDto();
        riskScoreField.setFieldKey("riskScore");
        riskScoreField.setDisplayName("Credit Risk Score");
        riskScoreField.setFieldType("NUMBER");
        riskScoreField.setRequired(false);

        ContextFieldDto amountField = new ContextFieldDto();
        amountField.setFieldKey("amount");
        amountField.setDisplayName("Order Amount");
        amountField.setFieldType("NUMBER");
        amountField.setRequired(false);

        schema.setFields(List.of(cafIdField, circleIdField, riskScoreField, amountField));
        contextSchemaService.createOrUpdateSchema(schema);

        // =====================================================================
        // CRITERION 1 (Cont): Define Workflow with RULE node, BUCKET node,
        // COMMAND node (MQ), and END node
        // =====================================================================
        WorkflowDefinitionDto defDto = new WorkflowDefinitionDto();
        defDto.setKey(workflowKey);
        defDto.setName("End to End Test Workflow");
        defDto.setDescription("Tests Rule -> Bucket -> Command (Kafka MQ) -> End pipeline");
        defDto.setCircleId(1);
        WorkflowDefinitionDto savedDef = workflowService.createWorkflowDefinition(defDto);
        versionId = savedDef.getVersions().get(0).getId();

        WorkflowGraphDto graph = new WorkflowGraphDto();

        // 1. START NODE
        WorkflowNodeDto startNode = new WorkflowNodeDto();
        startNode.setId("node-start");
        startNode.setType("START");
        startNode.setLabel("Start Order");

        // 2. RULE NODE (Evaluates if riskScore >= 50 or amount > 1000)
        WorkflowNodeDto ruleNode = new WorkflowNodeDto();
        ruleNode.setId("node-rule-risk");
        ruleNode.setType("RULE");
        ruleNode.setLabel("Evaluate Risk Score");
        ruleNode.getData().put("condition", "context.riskScore >= 50");

        // 3. BUCKET NODE (Manual review suspension with custom pending status & entTypeId)
        WorkflowNodeDto bucketNode = new WorkflowNodeDto();
        bucketNode.setId("node-bucket-a2");
        bucketNode.setType("BUCKET");
        bucketNode.setLabel("A2 Manual Document Review");
        bucketNode.getData().put("bucketId", "A2");
        bucketNode.getData().put("pendingRevertStatus", "A2_PENDING_MANUAL");
        bucketNode.getData().put("entTypeId", "AGENT_QUEUE");

        // 4. COMMAND NODE (MQ - Send Message to Kafka with mapped outgoing payload)
        WorkflowNodeDto commandNode = new WorkflowNodeDto();
        commandNode.setId("node-cmd-publish");
        commandNode.setType("COMMAND");
        commandNode.setLabel("Publish Approval To Kafka");
        commandNode.getData().put("commandType", "MQ");
        commandNode.getData().put("topic", "vth.downstream.notifications");
        commandNode.getData().put("key", "${context.cafId}");
        commandNode.getData().put("payloadMapping", Map.of(
                "cafId", "context.cafId",
                "circleId", "context.circleId",
                "status", "'APPROVED'",
                "resolvedBy", "context.resolvedBy"
        ));

        // 5. END NODE
        WorkflowNodeDto endNode = new WorkflowNodeDto();
        endNode.setId("node-end");
        endNode.setType("END");
        endNode.setLabel("Order Completed");

        graph.setNodes(List.of(startNode, ruleNode, bucketNode, commandNode, endNode));

        // EDGES:
        // start -> rule
        WorkflowEdgeDto edgeStartToRule = new WorkflowEdgeDto();
        edgeStartToRule.setId("edge-1");
        edgeStartToRule.setSource("node-start");
        edgeStartToRule.setTarget("node-rule-risk");

        // rule -> bucket (when condition context.riskScore >= 50 is TRUE)
        WorkflowEdgeDto edgeRuleToBucket = new WorkflowEdgeDto();
        edgeRuleToBucket.setId("edge-2");
        edgeRuleToBucket.setSource("node-rule-risk");
        edgeRuleToBucket.setTarget("node-bucket-a2");
        edgeRuleToBucket.getData().put("condition", "context.riskScore >= 50");

        // bucket -> command (when bucket completes/resumes)
        WorkflowEdgeDto edgeBucketToCmd = new WorkflowEdgeDto();
        edgeBucketToCmd.setId("edge-3");
        edgeBucketToCmd.setSource("node-bucket-a2");
        edgeBucketToCmd.setTarget("node-cmd-publish");

        // command -> end
        WorkflowEdgeDto edgeCmdToEnd = new WorkflowEdgeDto();
        edgeCmdToEnd.setId("edge-4");
        edgeCmdToEnd.setSource("node-cmd-publish");
        edgeCmdToEnd.setTarget("node-end");

        graph.setEdges(List.of(edgeStartToRule, edgeRuleToBucket, edgeBucketToCmd, edgeCmdToEnd));

        // Update draft and publish version
        workflowService.updateDraftVersion(versionId, graph);
        workflowService.transitionVersionStatus(versionId, "REVIEW");
        workflowService.transitionVersionStatus(versionId, "APPROVED");
        workflowService.transitionVersionStatus(versionId, "PUBLISHED");
    }

    /**
     * TEST A: REST API Method Trigger + Context Primary Key Resolution +
     * EntityRevert Table Insertion + Kafka Bucket Resumption + Command Kafka Publish.
     */
    @Test
    public void testFullPipeline_RestApiTrigger_BucketSuspension_KafkaResume_CommandPublish() throws Exception {
        Long testCafId = 880011L;
        Integer testCircleId = 22;

        // ---------------------------------------------------------------------
        // CRITERION 2: Trigger workflow execution via REST API method
        // ---------------------------------------------------------------------
        ExecutionRequestDto request = new ExecutionRequestDto();
        request.setContextId(String.valueOf(testCafId));
        request.setBusinessKey(String.valueOf(testCafId));
        request.setCircleId(testCircleId);
        request.setContext(new HashMap<>(Map.of(
                "cafId", testCafId,
                "circleId", testCircleId,
                "riskScore", 85,
                "amount", 2500
        )));

        ExecutionLogDto response = executionService.execute(workflowKey, request);
        assertNotNull(response);
        String instanceId = response.getInstanceId();
        assertNotNull(instanceId);

        // Verify workflow reached BUCKET node and suspended with WAITING status
        assertEquals("WAITING", response.getStatus());

        WorkflowInstance instance = instanceRepository.findById(instanceId).orElseThrow();
        assertEquals("WAITING", instance.getStatus());

        // ---------------------------------------------------------------------
        // CRITERION 3 & 4: Verify EntityRevert DB row created with cafId, circleId,
        // and custom pendingRevertStatus
        // ---------------------------------------------------------------------
        List<EntityRevert> reverts = entityRevertRepository.findByWorkflowInstanceId(instanceId);
        assertFalse(reverts.isEmpty(), "An EntityRevert record must be created on bucket suspension");

        EntityRevert revert = reverts.get(0);
        assertEquals(testCafId, revert.getCafId(), "cafId primary key must be correctly picked up by Bucket node");
        assertEquals(testCircleId, revert.getCircleId(), "circleId must match the value passed in context (22)");
        assertEquals("A2", revert.getBucketId(), "bucketId must be A2");
        assertEquals("A2_PENDING_MANUAL", revert.getRevertStatus(), "Custom pending revert status must be respected");
        assertEquals("AGENT_QUEUE", revert.getEntTypeId(), "Custom entTypeId must be respected");
        assertTrue(revert.isActive(), "EntityRevert must be active");

        // ---------------------------------------------------------------------
        // CRITERION 5: Resume workflow triggered via Kafka payload WITHOUT instanceId
        // (External approval systems only have business identifiers like cafId / businessKey)
        // ---------------------------------------------------------------------
        String kafkaResolutionPayload = String.format("""
            {
                "cafId": %d,
                "businessKey": "%s",
                "bucketId": "A2",
                "outcome": "Accept",
                "resolvedBy": "SeniorOfficer_42",
                "resolutionNotes": "All documents verified and accepted"
            }
            """, testCafId, testCafId);

        // Simulate incoming Kafka event on 'workflow-bucket-resolution' topic
        kafkaEventListener.handleBucketResolution(kafkaResolutionPayload, "SeniorOfficer_42");

        // ---------------------------------------------------------------------
        // CRITERION 6: Verify workflow finished with COMPLETED status
        // ---------------------------------------------------------------------
        WorkflowInstance completedInstance = instanceRepository.findById(instanceId).orElseThrow();
        assertEquals("COMPLETED", completedInstance.getStatus(), "Workflow must finish in COMPLETED status after resumption");

        // Verify EntityRevert row updated to COMPLETED
        EntityRevert updatedRevert = entityRevertRepository.findById(revert.getId()).orElseThrow();
        assertEquals("COMPLETED", updatedRevert.getRevertStatus());
        assertEquals("SeniorOfficer_42", updatedRevert.getRevertPersonId());

        // ---------------------------------------------------------------------
        // CRITERION 7: Verify Command Node (MQ) published message to Kafka
        // with outgoing mapped payload from context schema
        // ---------------------------------------------------------------------
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate, atLeastOnce()).send(
                eq("vth.downstream.notifications"),
                eq(String.valueOf(testCafId)),
                payloadCaptor.capture()
        );

        Map<String, Object> sentPayload = payloadCaptor.getValue();
        assertNotNull(sentPayload);
        assertEquals(String.valueOf(testCafId), String.valueOf(sentPayload.get("cafId")), "Mapped outgoing payload must contain cafId");
        assertEquals(String.valueOf(testCircleId), String.valueOf(sentPayload.get("circleId")), "Mapped outgoing payload must contain circleId");
        assertEquals("APPROVED", sentPayload.get("status"), "Mapped outgoing payload must contain literal status 'APPROVED'");
        assertEquals("SeniorOfficer_42", sentPayload.get("resolvedBy"), "Mapped outgoing payload must contain resolvedBy from resume context");
    }

    /**
     * TEST B: Workflow creation via Kafka Trigger method and Kafka bucket resolution WITHOUT instanceId.
     */
    @Test
    public void testFullPipeline_KafkaTrigger_BucketSuspension_KafkaResume() throws Exception {
        Long kafkaCafId = 990022L;
        Integer kafkaCircleId = 33;

        // ---------------------------------------------------------------------
        // CRITERION 2 (Kafka Method): Trigger workflow execution via Kafka payload
        // ---------------------------------------------------------------------
        String kafkaTriggerJson = String.format("""
            {
                "workflowKey": "%s",
                "cafId": "%s",
                "circleId": %d,
                "context": {
                    "cafId": %d,
                    "circleId": %d,
                    "riskScore": 90,
                    "amount": 5000
                }
            }
            """, workflowKey, kafkaCafId, kafkaCircleId, kafkaCafId, kafkaCircleId);

        // Ingest trigger from Kafka listener
        kafkaEventListener.handleWorkflowTrigger(kafkaTriggerJson);

        // Find the triggered workflow instance
        WorkflowInstance instance = instanceRepository.findFirstByBusinessKeyOrderByCreatedAtDesc(String.valueOf(kafkaCafId))
                .orElseThrow(() -> new AssertionError("Workflow instance should have been created from Kafka trigger"));

        assertEquals("WAITING", instance.getStatus());

        // Verify EntityRevert created with Kafka values
        List<EntityRevert> reverts = entityRevertRepository.findByWorkflowInstanceId(instance.getId());
        assertFalse(reverts.isEmpty());
        EntityRevert er = reverts.get(0);
        assertEquals(kafkaCafId, er.getCafId());
        assertEquals(kafkaCircleId, er.getCircleId());
        assertEquals("A2", er.getBucketId());
        assertEquals("A2_PENDING_MANUAL", er.getRevertStatus());

        // Resume via Kafka WITHOUT instanceId (External system only provides cafId and bucketId)
        String resolutionPayload = String.format("""
            {
                "cafId": %d,
                "bucketId": "A2",
                "outcome": "Accept",
                "resolvedBy": "AutoApprover"
            }
            """, kafkaCafId);

        kafkaEventListener.handleBucketResolution(resolutionPayload, "AutoApprover");

        // Verify completed
        WorkflowInstance resumed = instanceRepository.findById(instance.getId()).orElseThrow();
        assertEquals("COMPLETED", resumed.getStatus());
    }

    /**
     * TEST C: Kafka bucket resolution without instanceId, with auto-detected bucketId
     * and status prefix stripping ('A2Accept' -> 'Accept').
     */
    @Test
    public void testKafkaBucketResolution_WithoutInstanceId_AutoDetectedBucketAndStatus() throws Exception {
        Long testCafId = 771199L;
        Integer testCircleId = 18;

        ExecutionRequestDto request = new ExecutionRequestDto();
        request.setContextId(String.valueOf(testCafId));
        request.setBusinessKey(String.valueOf(testCafId));
        request.setCircleId(testCircleId);
        request.setContext(new HashMap<>(Map.of(
                "cafId", testCafId,
                "circleId", testCircleId,
                "riskScore", 75,
                "amount", 1200
        )));

        ExecutionLogDto response = executionService.execute(workflowKey, request);
        assertNotNull(response);
        String instanceId = response.getInstanceId();
        assertEquals("WAITING", response.getStatus());

        // Resolve via Kafka with ONLY cafId and telecom status 'A2Accept' (omits instanceId AND bucketId!)
        String minimalKafkaPayload = String.format("""
            {
                "cafId": %d,
                "status": "A2Accept",
                "resolvedBy": "ExternalTeleQueueService"
            }
            """, testCafId);

        kafkaEventListener.handleBucketResolution(minimalKafkaPayload, null);

        // Verify workflow resumed and finished in COMPLETED status
        WorkflowInstance completedInstance = instanceRepository.findById(instanceId).orElseThrow();
        assertEquals("COMPLETED", completedInstance.getStatus(),
                "Workflow must resolve and reach COMPLETED status even without instanceId or bucketId in Kafka payload");

        EntityRevert revert = entityRevertRepository.findByWorkflowInstanceId(instanceId).get(0);
        assertEquals("COMPLETED", revert.getRevertStatus());
        assertEquals("ExternalTeleQueueService", revert.getRevertPersonId());
    }
}
