package com.vi.atlas.workflow.service;

import com.vi.atlas.common.dto.*;
import com.vi.atlas.workflow.entity.*;
import com.vi.atlas.workflow.repository.*;
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
public class CafJourneyIngestionIntegrationTest {

    @Autowired
    private CafJourneyIngestionService ingestionService;

    @Autowired
    private StagedPayloadRepository stagedPayloadRepository;

    @Autowired
    private CustomerFormRepository customerFormRepository;

    @Autowired
    private WorkflowInstanceRepository instanceRepository;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private EventDefinitionRepository eventDefinitionRepository;

    @BeforeEach
    public void setUp() {
        registerEvent("DOCUMENTS_STAGED");
        registerEvent("CAF_STAGED");
        registerEvent("GROUP_CREATION_REQUESTED");

        setupOrchestratorWorkflow(CafJourneyIngestionService.CAF_WORKFLOW_KEY);
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
        eventDef.setActive(true);
        eventDefinitionRepository.saveAndFlush(eventDef);
    }

    @Autowired
    private com.vi.atlas.workflow.repository.WorkflowDefinitionRepository definitionRepository;

    @Autowired
    private com.vi.atlas.workflow.repository.WorkflowVersionRepository versionRepository;

    private void setupOrchestratorWorkflow(String key) {
        WorkflowGraphDto graph = buildGraph();

        Optional<WorkflowDefinition> existing = definitionRepository.findByKey(key);
        if (existing.isPresent()) {
            WorkflowDefinition def = existing.get();
            Integer activeVerNum = def.getActiveVersion() != null ? def.getActiveVersion() : 1;
            WorkflowVersion ver = versionRepository.findByWorkflowDefinitionIdAndVersion(def.getId(), activeVerNum).orElse(null);
            if (ver != null) {
                ver.setDefinition(graph);
                ver.setStatus("PUBLISHED");
                versionRepository.saveAndFlush(ver);
                return;
            }
        }

        WorkflowDefinitionDto defDto = new WorkflowDefinitionDto();
        defDto.setKey(key);
        defDto.setName("CAF Submission Orchestrator");
        defDto.setDescription("Orchestrates out-of-order documents and CAF submissions");

        WorkflowDefinitionDto created = workflowService.createWorkflowDefinition(defDto);
        String versionId = created.getVersions().get(0).getId();

        workflowService.updateDraftVersion(versionId, graph);
        workflowService.transitionVersionStatus(versionId, "REVIEW");
        workflowService.transitionVersionStatus(versionId, "APPROVED");
        workflowService.transitionVersionStatus(versionId, "PUBLISHED");
    }

    private WorkflowGraphDto buildGraph() {

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

        WorkflowNodeDto waitDocs = new WorkflowNodeDto();
        waitDocs.setId("wait-docs");
        waitDocs.setType("WAIT_EVENT");
        waitDocs.setLabel("Wait for Documents");
        waitDocs.getData().put("eventType", "DOCUMENTS_STAGED");
        nodes.add(waitDocs);

        WorkflowNodeDto waitCaf = new WorkflowNodeDto();
        waitCaf.setId("wait-caf");
        waitCaf.setType("WAIT_EVENT");
        waitCaf.setLabel("Wait for CAF");
        waitCaf.getData().put("eventType", "CAF_STAGED");
        nodes.add(waitCaf);

        WorkflowNodeDto joinNode = new WorkflowNodeDto();
        joinNode.setId("join");
        joinNode.setType("JOIN");
        joinNode.setLabel("Join");
        joinNode.getData().put("joinType", "AND");
        nodes.add(joinNode);

        WorkflowNodeDto cmdNode = new WorkflowNodeDto();
        cmdNode.setId("cmd-finalize");
        cmdNode.setType("COMMAND");
        cmdNode.setLabel("Finalize Golden Record");
        cmdNode.getData().put("commandType", "FINALIZE_CAF_SUBMISSION");
        nodes.add(cmdNode);

        WorkflowNodeDto endNode = new WorkflowNodeDto();
        endNode.setId("end");
        endNode.setType("END");
        endNode.setLabel("End");
        nodes.add(endNode);

        List<WorkflowEdgeDto> edges = new ArrayList<>();
        edges.add(createEdge("e1", "start", "split"));
        edges.add(createEdge("e2", "split", "wait-docs"));
        edges.add(createEdge("e3", "split", "wait-caf"));
        edges.add(createEdge("e4", "wait-docs", "join"));
        edges.add(createEdge("e5", "wait-caf", "join"));
        edges.add(createEdge("e6", "join", "cmd-finalize"));
        edges.add(createEdge("e7", "cmd-finalize", "end"));

        WorkflowGraphDto graph = new WorkflowGraphDto();
        graph.setNodes(nodes);
        graph.setEdges(edges);
        return graph;
    }

    private WorkflowEdgeDto createEdge(String id, String source, String target) {
        WorkflowEdgeDto edge = new WorkflowEdgeDto();
        edge.setId(id);
        edge.setSource(source);
        edge.setTarget(target);
        return edge;
    }

    @Test
    public void testDocsFirst_ThenCaf_FinalizesGoldenRecord() {
        String trackingId = "TRK-DOCS-FIRST-" + UUID.randomUUID().toString().substring(0, 6);

        // 1. Client calls Submit Documents API first
        Map<String, Object> docsData = new HashMap<>();
        docsData.put("documentType", "AADHAAR");
        docsData.put("documentUrl", "https://vi.docs/aadhaar.pdf");
        docsData.put("aadhaarNo", "1234-5678-9012");

        CafJourneyIngestionService.IngestionResult docsResult =
                ingestionService.stageAndIngestDocuments(trackingId, 1, docsData);

        assertNotNull(docsResult.instanceId());
        assertEquals("DOCUMENTS_STAGED", docsResult.eventRouted());
        assertEquals("WAITING", docsResult.workflowStatus());
        assertFalse(docsResult.finalized());

        // Verify staged payload saved
        Optional<StagedPayload> stagedDocs = stagedPayloadRepository
                .findFirstByBusinessKeyAndPayloadTypeOrderByCreatedAtDesc(trackingId, "DOCUMENTS");
        assertTrue(stagedDocs.isPresent());
        assertEquals("STAGED", stagedDocs.get().getStatus());

        // Golden record must NOT exist yet
        Optional<CustomerForm> formBeforeCaf = customerFormRepository.findById(trackingId);
        assertTrue(formBeforeCaf.isEmpty(), "Golden CustomerForm should NOT be created before CAF arrives");

        // 2. Client calls Submit CAF API second
        Map<String, Object> cafData = new HashMap<>();
        cafData.put("customerName", "Priya Sharma");
        cafData.put("msisdn", "9876500001");
        cafData.put("kycType", "DIGITAL_KYC");

        CafJourneyIngestionService.IngestionResult cafResult =
                ingestionService.stageAndIngestCaf(trackingId, 1, cafData);

        // Workflow should now converge and complete
        assertEquals("COMPLETED", cafResult.workflowStatus());
        assertTrue(cafResult.finalized());

        // Verify golden CustomerForm record created atomically
        Optional<CustomerForm> formAfterCaf = customerFormRepository.findById(trackingId);
        assertTrue(formAfterCaf.isPresent(), "Golden CustomerForm must be created after both APIs arrive");
        assertEquals("Priya Sharma", formAfterCaf.get().getCustomerName());
        assertEquals("CAF_SUBMITTED_AND_ACTIVE", formAfterCaf.get().getFormStatus());

        // Verify staged payloads marked as CONSUMED
        List<StagedPayload> allStaged = stagedPayloadRepository.findAllByBusinessKey(trackingId);
        assertEquals(2, allStaged.size());
        for (StagedPayload sp : allStaged) {
            assertEquals("CONSUMED", sp.getStatus());
        }
    }

    @Test
    public void testCafFirst_ThenDocs_FinalizesGoldenRecord() {
        String trackingId = "TRK-CAF-FIRST-" + UUID.randomUUID().toString().substring(0, 6);

        // 1. Client calls Submit CAF API first
        Map<String, Object> cafData = new HashMap<>();
        cafData.put("customerName", "Rohit Verma");
        cafData.put("msisdn", "9876500002");
        cafData.put("kycType", "E_KYC");

        CafJourneyIngestionService.IngestionResult cafResult =
                ingestionService.stageAndIngestCaf(trackingId, 1, cafData);

        assertNotNull(cafResult.instanceId());
        assertEquals("CAF_STAGED", cafResult.eventRouted());
        assertEquals("WAITING", cafResult.workflowStatus());
        assertFalse(cafResult.finalized());

        // Golden record must NOT exist yet
        Optional<CustomerForm> formBeforeDocs = customerFormRepository.findById(trackingId);
        assertTrue(formBeforeDocs.isEmpty(), "Golden CustomerForm should NOT be created before Documents arrive");

        // 2. Client calls Submit Documents API second
        Map<String, Object> docsData = new HashMap<>();
        docsData.put("documentType", "PASSPORT");
        docsData.put("passportNo", "Z9876543");

        CafJourneyIngestionService.IngestionResult docsResult =
                ingestionService.stageAndIngestDocuments(trackingId, 1, docsData);

        // Workflow should now converge and complete
        assertEquals("COMPLETED", docsResult.workflowStatus());
        assertTrue(docsResult.finalized());

        // Verify golden CustomerForm record created atomically
        Optional<CustomerForm> formAfterDocs = customerFormRepository.findById(trackingId);
        assertTrue(formAfterDocs.isPresent(), "Golden CustomerForm must be created after both APIs arrive");
        assertEquals("Rohit Verma", formAfterDocs.get().getCustomerName());
        assertEquals("CAF_SUBMITTED_AND_ACTIVE", formAfterDocs.get().getFormStatus());

        // Verify staged payloads marked as CONSUMED
        List<StagedPayload> allStaged = stagedPayloadRepository.findAllByBusinessKey(trackingId);
        assertEquals(2, allStaged.size());
        for (StagedPayload sp : allStaged) {
            assertEquals("CONSUMED", sp.getStatus());
        }
    }

    @Test
    public void testJourneyStatusEndpoint() {
        String trackingId = "TRK-STATUS-" + UUID.randomUUID().toString().substring(0, 6);

        // Check before any submission
        Map<String, Object> statusInitial = ingestionService.getJourneyStatus(trackingId);
        assertEquals("NOT_STARTED", statusInitial.get("workflowStatus"));
        assertEquals(false, statusInitial.get("goldenRecordCreated"));

        // Stage docs
        ingestionService.stageAndIngestDocuments(trackingId, 1, Map.of("doc", "id.pdf"));

        Map<String, Object> statusMid = ingestionService.getJourneyStatus(trackingId);
        assertEquals("WAITING", statusMid.get("workflowStatus"));
        assertEquals(1, statusMid.get("stagedPayloadsCount"));
        assertEquals(false, statusMid.get("goldenRecordCreated"));

        // Stage CAF
        ingestionService.stageAndIngestCaf(trackingId, 1, Map.of("customerName", "Neha Gupta"));

        Map<String, Object> statusFinal = ingestionService.getJourneyStatus(trackingId);
        assertEquals("COMPLETED", statusFinal.get("workflowStatus"));
        assertEquals(true, statusFinal.get("goldenRecordCreated"));
        assertEquals("Neha Gupta", statusFinal.get("customerName"));
    }
}
