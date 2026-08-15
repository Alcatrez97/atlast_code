package com.vi.atlas.workflow.service;

import com.vi.atlas.common.dto.*;
import com.vi.atlas.workflow.entity.TaskInstance;
import com.vi.atlas.workflow.entity.WorkflowInstance;
import com.vi.atlas.workflow.repository.TaskInstanceRepository;
import com.vi.atlas.workflow.repository.WorkflowInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
    "spring.datasource.url=jdbc:h2:mem:nodeacttest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL"
})
@Transactional
@ActiveProfiles("test")
@DirtiesContext
public class NodeActivationIntegrationTest {

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private WorkflowInstanceRepository instanceRepository;

    @Autowired
    private TaskInstanceRepository taskInstanceRepository;

    @Autowired
    private BucketResolutionService bucketResolutionService;

    private String workflowKey;

    @BeforeEach
    public void setUp() {
        workflowKey = "DYN_JOURNEY_" + UUID.randomUUID().toString().substring(0, 8);
        
        WorkflowDefinitionDto defDto = new WorkflowDefinitionDto();
        defDto.setKey(workflowKey);
        defDto.setName("Dynamic Eligibility Journey Test");
        defDto.setDescription("Tests separated structural and business activation gates");

        WorkflowDefinitionDto createdDef = workflowService.createWorkflowDefinition(defDto);
        String versionId = createdDef.getVersions().get(0).getId();

        // Nodes definition matching our design spec
        List<WorkflowNodeDto> nodes = new ArrayList<>();
        
        WorkflowNodeDto startNode = new WorkflowNodeDto();
        startNode.setId("start");
        startNode.setType("START");
        startNode.setLabel("Start");
        nodes.add(startNode);

        WorkflowNodeDto a2Node = new WorkflowNodeDto();
        a2Node.setId("a2");
        a2Node.setType("BUCKET");
        a2Node.setLabel("A2 Check");
        a2Node.getData().put("bucketId", "A2");
        a2Node.getData().put("joinType", "AND");
        a2Node.getData().put("businessEligibilityRule", "context.requiresA2 == true");
        nodes.add(a2Node);

        WorkflowNodeDto obccNode = new WorkflowNodeDto();
        obccNode.setId("obcc");
        obccNode.setType("BUCKET");
        obccNode.setLabel("OBCC");
        obccNode.getData().put("bucketId", "OBCC");
        obccNode.getData().put("joinType", "OR");
        obccNode.getData().put("businessEligibilityRule", "context.riskProfile == 'HIGH' || context.amount > 10000");
        nodes.add(obccNode);

        WorkflowNodeDto policeNode = new WorkflowNodeDto();
        policeNode.setId("police_verification");
        policeNode.setType("BUCKET");
        policeNode.setLabel("Police Verification");
        policeNode.getData().put("bucketId", "POLICE");
        policeNode.getData().put("joinType", "AND");
        policeNode.getData().put("businessEligibilityRule", "context.requiresBackgroundCheck == true");
        nodes.add(policeNode);

        WorkflowNodeDto dealerNode = new WorkflowNodeDto();
        dealerNode.setId("dealer_approval");
        dealerNode.setType("BUCKET");
        dealerNode.setLabel("Dealer Approval");
        dealerNode.getData().put("bucketId", "DEALER");
        dealerNode.getData().put("joinType", "AND");
        dealerNode.getData().put("businessEligibilityRule", "context.channel == 'DEALER'");
        nodes.add(dealerNode);

        WorkflowNodeDto doaNode = new WorkflowNodeDto();
        doaNode.setId("doa");
        doaNode.setType("BUCKET");
        doaNode.setLabel("DOA");
        doaNode.getData().put("bucketId", "DOA");
        doaNode.getData().put("joinType", "AND");
        doaNode.getData().put("businessEligibilityRule", "context.amount > 50000");
        nodes.add(doaNode);

        WorkflowNodeDto endNode = new WorkflowNodeDto();
        endNode.setId("end");
        endNode.setType("END");
        endNode.setLabel("End");
        endNode.getData().put("joinType", "OR");
        nodes.add(endNode);

        // Edges
        List<WorkflowEdgeDto> edges = new ArrayList<>();
        edges.add(createEdge("e-start-a2", "start", "a2"));
        edges.add(createEdge("e-start-obcc", "start", "obcc"));
        edges.add(createEdge("e-a2-obcc", "a2", "obcc"));
        edges.add(createEdge("e-a2-police", "a2", "police_verification"));
        edges.add(createEdge("e-police-dealer", "police_verification", "dealer_approval"));
        edges.add(createEdge("e-obcc-doa", "obcc", "doa"));
        edges.add(createEdge("e-dealer-end", "dealer_approval", "end"));
        edges.add(createEdge("e-doa-end", "doa", "end"));

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

    private String getTaskStatus(String instanceId, String nodeId) {
        List<TaskInstance> tasks = taskInstanceRepository.findByWorkflowInstanceIdOrderByStartedAtAsc(instanceId);
        String expectedPrefix = instanceId + "_" + nodeId + "_";
        for (TaskInstance ti : tasks) {
            if (ti.getId().startsWith(expectedPrefix)) {
                return ti.getStatus();
            }
        }
        return null;
    }

    @Test
    public void testScenarioA_OBCC_DOA() {
        String testCafId = "CAF_SCENARIO_A_" + UUID.randomUUID().toString().substring(0, 8);

        ExecutionRequestDto request = new ExecutionRequestDto();
        request.setBusinessKey(testCafId);
        request.setContextId(testCafId);
        
        Map<String, Object> context = new HashMap<>();
        context.put("requiresA2", false);
        context.put("riskProfile", "HIGH");
        context.put("amount", 75000);
        request.setContext(context);

        // 1. Initial execution.
        // Expect:
        // - start: COMPLETED
        // - a2: SKIPPED (requiresA2 == false)
        // - police_verification: SKIPPED (AND join, predecessor a2 is SKIPPED)
        // - dealer_approval: SKIPPED (AND join, predecessor police is SKIPPED)
        // - obcc: WAITING (OR join, start is COMPLETED, business eligibility TRUE)
        // - doa: Pending (predecessor obcc is not resolved yet)
        ExecutionLogDto execLog = executionService.execute(workflowKey, request);
        String instanceId = execLog.getInstanceId();

        assertEquals("WAITING", execLog.getStatus());
        assertEquals("obcc", execLog.getOutcomeNodeId());

        // Verify task statuses
        assertEquals("COMPLETED", getTaskStatus(instanceId, "start"));
        assertEquals("SKIPPED", getTaskStatus(instanceId, "a2"));
        assertEquals("SKIPPED", getTaskStatus(instanceId, "police_verification"));
        assertEquals("SKIPPED", getTaskStatus(instanceId, "dealer_approval"));
        assertEquals("WAITING", getTaskStatus(instanceId, "obcc"));
        assertNull(getTaskStatus(instanceId, "doa")); // obcc not resolved yet

        // 2. Resolve obcc
        bucketResolutionService.resolveBucket(instanceId, "OBCC", "Accept", "ApproverBob", "OBCC Accept");

        // Expect:
        // - obcc: COMPLETED
        // - doa: WAITING (amount 75000 > 50000)
        WorkflowInstance instanceAfterObcc = instanceRepository.findById(instanceId).orElseThrow();
        assertEquals("WAITING", instanceAfterObcc.getStatus());
        assertEquals("doa", instanceAfterObcc.getCurrentNodeId());

        assertEquals("COMPLETED", getTaskStatus(instanceId, "obcc"));
        assertEquals("WAITING", getTaskStatus(instanceId, "doa"));

        // 3. Resolve doa
        bucketResolutionService.resolveBucket(instanceId, "DOA", "Accept", "ApproverBob", "DOA Accept");

        // Expect:
        // - doa: COMPLETED
        // - end: COMPLETED (OR join, doa was COMPLETED)
        WorkflowInstance finalInstance = instanceRepository.findById(instanceId).orElseThrow();
        assertEquals("COMPLETED", finalInstance.getStatus());
        assertEquals("end", finalInstance.getCurrentNodeId());

        assertEquals("COMPLETED", getTaskStatus(instanceId, "doa"));
        assertEquals("COMPLETED", getTaskStatus(instanceId, "end"));
    }

    @Test
    public void testScenarioB_A2_Police_Dealer() {
        String testCafId = "CAF_SCENARIO_B_" + UUID.randomUUID().toString().substring(0, 8);

        ExecutionRequestDto request = new ExecutionRequestDto();
        request.setBusinessKey(testCafId);
        request.setContextId(testCafId);
        
        Map<String, Object> context = new HashMap<>();
        context.put("requiresA2", true);
        context.put("requiresBackgroundCheck", true);
        context.put("channel", "DEALER");
        context.put("riskProfile", "LOW");
        context.put("amount", 5000);
        request.setContext(context);

        // 1. Initial execution.
        // Expect:
        // - start: COMPLETED
        // - a2: WAITING (requiresA2 == true)
        // - police_verification: Pending (a2 not resolved)
        // - obcc: Pending (a2 not resolved - wait, start is completed, but it is OR join, why is it pending?
        //         Ah! Because obcc has two predecessors: start and a2. Since a2 is WAITING (not completed/skipped),
        //         the structural gate determines that NOT all predecessors are resolved yet. So obcc must wait!)
        ExecutionLogDto execLog = executionService.execute(workflowKey, request);
        String instanceId = execLog.getInstanceId();

        assertEquals("WAITING", execLog.getStatus());
        assertEquals("a2", execLog.getOutcomeNodeId());

        assertEquals("COMPLETED", getTaskStatus(instanceId, "start"));
        assertEquals("WAITING", getTaskStatus(instanceId, "a2"));
        assertNull(getTaskStatus(instanceId, "police_verification"));
        assertNull(getTaskStatus(instanceId, "obcc"));

        // 2. Resolve a2
        bucketResolutionService.resolveBucket(instanceId, "A2", "Accept", "ApproverBob", "A2 Accept");

        // Expect:
        // - a2: COMPLETED
        // - police_verification: WAITING (predecessor a2 completed, requiresBackgroundCheck true)
        // - obcc: SKIPPED (all predecessors resolved, OR join has active incoming, but business eligibility failed: LOW & amount 5000)
        // - doa: SKIPPED (AND join, predecessor obcc is SKIPPED)
        WorkflowInstance instanceAfterA2 = instanceRepository.findById(instanceId).orElseThrow();
        assertEquals("WAITING", instanceAfterA2.getStatus());
        assertEquals("police_verification", instanceAfterA2.getCurrentNodeId());

        assertEquals("COMPLETED", getTaskStatus(instanceId, "a2"));
        assertEquals("WAITING", getTaskStatus(instanceId, "police_verification"));
        assertEquals("SKIPPED", getTaskStatus(instanceId, "obcc"));
        assertEquals("SKIPPED", getTaskStatus(instanceId, "doa"));

        // 3. Resolve police_verification
        bucketResolutionService.resolveBucket(instanceId, "POLICE", "Accept", "ApproverBob", "Police Verification Accept");

        // Expect:
        // - police_verification: COMPLETED
        // - dealer_approval: WAITING (channel DEALER)
        WorkflowInstance instanceAfterPolice = instanceRepository.findById(instanceId).orElseThrow();
        assertEquals("WAITING", instanceAfterPolice.getStatus());
        assertEquals("dealer_approval", instanceAfterPolice.getCurrentNodeId());

        assertEquals("COMPLETED", getTaskStatus(instanceId, "police_verification"));
        assertEquals("WAITING", getTaskStatus(instanceId, "dealer_approval"));

        // 4. Resolve dealer_approval
        bucketResolutionService.resolveBucket(instanceId, "DEALER", "Accept", "ApproverBob", "Dealer Approved");

        // Expect:
        // - dealer_approval: COMPLETED
        // - end: COMPLETED (OR join, dealer_approval completed)
        WorkflowInstance finalInstance = instanceRepository.findById(instanceId).orElseThrow();
        assertEquals("COMPLETED", finalInstance.getStatus());
        assertEquals("end", finalInstance.getCurrentNodeId());

        assertEquals("COMPLETED", getTaskStatus(instanceId, "dealer_approval"));
        assertEquals("COMPLETED", getTaskStatus(instanceId, "end"));
    }

    @Test
    public void testRuleNodeFalseBranchActivation() {
        String ruleWfKey = "RULE_WF_" + UUID.randomUUID().toString().substring(0, 8);

        WorkflowDefinitionDto defDto = new WorkflowDefinitionDto();
        defDto.setKey(ruleWfKey);
        defDto.setName("Rule False Branch Test");

        WorkflowDefinitionDto createdDef = workflowService.createWorkflowDefinition(defDto);
        String versionId = createdDef.getVersions().get(0).getId();

        List<WorkflowNodeDto> nodes = new ArrayList<>();

        WorkflowNodeDto startNode = new WorkflowNodeDto();
        startNode.setId("start");
        startNode.setType("START");
        nodes.add(startNode);

        WorkflowNodeDto ruleNode = new WorkflowNodeDto();
        ruleNode.setId("rule1");
        ruleNode.setType("RULE");
        ruleNode.setLabel("Score Rule");
        ruleNode.getData().put("joinType", "AND");
        ruleNode.getData().put("expression", "context.score > 700");
        nodes.add(ruleNode);

        WorkflowNodeDto trueBucket = new WorkflowNodeDto();
        trueBucket.setId("bucket_true");
        trueBucket.setType("BUCKET");
        trueBucket.setLabel("True Bucket");
        trueBucket.getData().put("bucketId", "TRUE_B");
        trueBucket.getData().put("joinType", "AND");
        nodes.add(trueBucket);

        WorkflowNodeDto falseBucket = new WorkflowNodeDto();
        falseBucket.setId("bucket_false");
        falseBucket.setType("BUCKET");
        falseBucket.setLabel("False Bucket");
        falseBucket.getData().put("bucketId", "FALSE_B");
        falseBucket.getData().put("joinType", "AND");
        nodes.add(falseBucket);

        WorkflowNodeDto endNode = new WorkflowNodeDto();
        endNode.setId("end");
        endNode.setType("END");
        endNode.setLabel("End");
        endNode.getData().put("joinType", "OR");
        nodes.add(endNode);

        List<WorkflowEdgeDto> edges = new ArrayList<>();
        edges.add(createEdge("e-start-rule", "start", "rule1"));

        WorkflowEdgeDto trueEdge = createEdge("e-rule-true", "rule1", "bucket_true");
        trueEdge.getData().put("condition", "true");
        trueEdge.setLabel("true");
        edges.add(trueEdge);

        WorkflowEdgeDto falseEdge = createEdge("e-rule-false", "rule1", "bucket_false");
        falseEdge.getData().put("condition", "");
        falseEdge.setLabel("false");
        edges.add(falseEdge);

        edges.add(createEdge("e-true-end", "bucket_true", "end"));
        edges.add(createEdge("e-false-end", "bucket_false", "end"));

        WorkflowGraphDto graph = new WorkflowGraphDto();
        graph.setNodes(nodes);
        graph.setEdges(edges);

        workflowService.updateDraftVersion(versionId, graph);
        workflowService.transitionVersionStatus(versionId, "REVIEW");
        workflowService.transitionVersionStatus(versionId, "APPROVED");
        workflowService.transitionVersionStatus(versionId, "PUBLISHED");

        ExecutionRequestDto request = new ExecutionRequestDto();
        String busKey = "BK_RULE_" + UUID.randomUUID().toString().substring(0, 8);
        request.setBusinessKey(busKey);
        request.setContextId(busKey);
        request.setContext(Map.of("score", 500)); // Evaluates to false!

        ExecutionLogDto execLog = executionService.execute(ruleWfKey, request);
        String instanceId = execLog.getInstanceId();

        assertEquals("WAITING", execLog.getStatus());
        assertEquals("bucket_false", execLog.getOutcomeNodeId());

        assertEquals("COMPLETED", getTaskStatus(instanceId, "start"));
        assertEquals("COMPLETED", getTaskStatus(instanceId, "rule1"));
        assertEquals("SKIPPED", getTaskStatus(instanceId, "bucket_true"));
        assertEquals("WAITING", getTaskStatus(instanceId, "bucket_false"));
    }
}
