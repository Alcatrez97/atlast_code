package com.enterprise.atlas.workflow.service;

import com.enterprise.atlas.common.dto.*;
import com.enterprise.atlas.workflow.entity.CustomerForm;
import com.enterprise.atlas.workflow.entity.RevertStatus;
import com.enterprise.atlas.workflow.entity.WorkflowInstance;
import com.enterprise.atlas.workflow.repository.CustomerFormRepository;
import com.enterprise.atlas.workflow.repository.RevertStatusRepository;
import com.enterprise.atlas.workflow.repository.WorkflowInstanceRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = {"caf-lifecycle", "workflow-bucket-tasks", "workflow-bucket-resolution"},
    bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@ActiveProfiles("kafka-test")
@DirtiesContext
public class KafkaIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private WorkflowInstanceRepository instanceRepository;

    @Autowired
    private CustomerFormRepository customerFormRepository;

    @Autowired
    private RevertStatusRepository revertStatusRepository;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    private Consumer<String, Object> consumer;
    private String workflowKey;
    private String definitionId;

    @BeforeEach
    public void setUp() {
        // Set up Kafka Consumer to read from workflow-bucket-tasks
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                embeddedKafka.getBrokersAsString(), "test-tasks-group", "true");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        consumerProps.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.enterprise.atlas.common.dto");

        ConsumerFactory<String, Object> cf = new DefaultKafkaConsumerFactory<>(consumerProps);
        consumer = cf.createConsumer();
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, "workflow-bucket-tasks");

        // Define workflowKey and set up workflow
        workflowKey = "KAFKA_TEST_WF_" + UUID.randomUUID().toString().substring(0, 8);

        WorkflowDefinitionDto defDto = new WorkflowDefinitionDto();
        defDto.setKey(workflowKey);
        defDto.setName("Kafka Integration Workflow");
        defDto.setDescription("Tests Kafka event driven starting and bucket resolution with Park");

        WorkflowDefinitionDto createdDef = workflowService.createWorkflowDefinition(defDto);
        definitionId = createdDef.getId();
        String initialVersionId = createdDef.getVersions().get(0).getId();

        // Build a mock graph containing START -> BUCKET (A2) -> DECISION -> 3 branches (Accept, Reject, Park)
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

        WorkflowNodeDto endParkedNode = new WorkflowNodeDto();
        endParkedNode.setId("end-parked");
        endParkedNode.setType("END");
        endParkedNode.setLabel("Parked Out");
        nodes.add(endParkedNode);

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

        // Edge taking A2Park branch
        WorkflowEdgeDto ePark = new WorkflowEdgeDto();
        ePark.setId("e-park");
        ePark.setSource("decision-1");
        ePark.setTarget("end-parked");
        ePark.setLabel("A2Park");
        ePark.getData().put("condition", "context.form_status == 'A2Park'");
        edges.add(ePark);

        WorkflowGraphDto graph = new WorkflowGraphDto();
        graph.setNodes(nodes);
        graph.setEdges(edges);

        workflowService.updateDraftVersion(initialVersionId, graph);
        workflowService.transitionVersionStatus(initialVersionId, "REVIEW");
        workflowService.transitionVersionStatus(initialVersionId, "APPROVED");
        workflowService.transitionVersionStatus(initialVersionId, "PUBLISHED");
    }

    @AfterEach
    public void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    public void testKafkaEventDrivenEndToEndFlow() throws Exception {
        String cafId = "CAF_" + UUID.randomUUID().toString().substring(0, 8);

        // 1. Submit CAF to Kafka topic 'caf-lifecycle'
        CafSubmittedEvent submitEvent = new CafSubmittedEvent(cafId, workflowKey, new HashMap<>());
        kafkaTemplate.send("caf-lifecycle", cafId, submitEvent);

        // 2. The engine should consume CafSubmittedEvent and start the workflow.
        // It will progress through START and then pause at BUCKET (A2).
        // Let's poll the database to find the running instance.
        WorkflowInstance instance = null;
        for (int i = 0; i < 50; i++) {
            List<WorkflowInstance> instances = instanceRepository.findByWorkflowKeyOrderByCreatedAtDesc(workflowKey);
            if (!instances.isEmpty()) {
                instance = instances.get(0);
                if ("WAITING".equals(instance.getStatus())) {
                    break;
                }
            }
            Thread.sleep(100);
        }

        assertNotNull(instance, "Workflow instance should have been started");
        assertEquals("WAITING", instance.getStatus());
        assertEquals("bucket-a2", instance.getCurrentNodeId());

        // Also assert that CustomerForm is updated to A2 Pending
        Optional<CustomerForm> formOpt = customerFormRepository.findById(cafId);
        assertTrue(formOpt.isPresent());
        assertEquals("A2 Pending", formOpt.get().getFormStatus());

        // 3. Verify BUCKET_READY is sent to 'workflow-bucket-tasks' topic.
        ConsumerRecord<String, Object> record = KafkaTestUtils.getSingleRecord(consumer, "workflow-bucket-tasks", java.time.Duration.ofMillis(10000));
        assertNotNull(record);
        
        BucketReadyEvent readyEvent = (BucketReadyEvent) record.value();
        assertEquals(instance.getId(), readyEvent.getInstanceId());
        assertEquals(cafId, readyEvent.getContextId());
        assertEquals("A2", readyEvent.getBucketId());
        assertTrue(readyEvent.getDependencyBucketIds().contains("A1"));

        // 4. Send BUCKET_COMPLETED event to 'workflow-bucket-resolution' topic with Park outcome.
        BucketResolutionEvent resolutionEvent = new BucketResolutionEvent(
                instance.getId(), "A2", "Park", "ReviewerJane", "Parking for manual document check");
        kafkaTemplate.send("workflow-bucket-resolution", instance.getId(), resolutionEvent);

        // 5. Verify workflow resumes and routes down decision paths to 'end-parked'.
        WorkflowInstance completedInstance = null;
        for (int i = 0; i < 50; i++) {
            completedInstance = instanceRepository.findById(instance.getId()).orElse(null);
            if (completedInstance != null && "COMPLETED".equals(completedInstance.getStatus())) {
                break;
            }
            Thread.sleep(100);
        }

        assertNotNull(completedInstance);
        assertEquals("COMPLETED", completedInstance.getStatus());
        assertEquals("end-parked", completedInstance.getCurrentNodeId());

        // Verify RevertStatus audit record is COMPLETED
        List<RevertStatus> audits = revertStatusRepository.findByWorkflowInstanceIdOrderByCreatedAtDesc(instance.getId());
        assertEquals(1, audits.size());
        RevertStatus audit = audits.get(0);
        assertEquals("COMPLETED", audit.getStatus());
        assertEquals("ReviewerJane", audit.getResolvedBy());
        assertEquals("Parking for manual document check", audit.getResolutionNotes());
        assertNotNull(audit.getCompletedAt());
    }
}
