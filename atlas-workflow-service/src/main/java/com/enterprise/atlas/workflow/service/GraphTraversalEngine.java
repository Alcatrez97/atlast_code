package com.enterprise.atlas.workflow.service;

import com.enterprise.atlas.common.dto.*;
import com.enterprise.atlas.workflow.entity.WorkflowDefinition;
import com.enterprise.atlas.workflow.entity.WorkflowInstance;
import com.enterprise.atlas.workflow.entity.WorkflowVersion;
import com.enterprise.atlas.workflow.repository.WorkflowInstanceRepository;
import com.enterprise.atlas.workflow.service.traversal.*;
import com.enterprise.atlas.workflow.service.traversal.node.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core graph traversal engine.
 *
 * <p>Walks a {@link WorkflowGraphDto} from the START node to an END (or terminal) node,
 * evaluating SpEL expressions on RULE / DECISION nodes against the provided context map.
 *
 * <p><b>Architecture note:</b> This class is the <em>orchestrator</em>. All business
 * logic has been extracted into focused collaborators in the
 * {@code com.enterprise.atlas.workflow.service.traversal} sub-package:
 * <ul>
 *   <li>{@link SpelEvaluator}        — SpEL expression evaluation &amp; explanation</li>
 *   <li>{@link EdgeSelector}         — edge-selection algorithms</li>
 *   <li>{@link RuntimeGraphManager}  — runtime-graph state</li>
 *   <li>{@link TaskRecorder}         — task-instance persistence</li>
 *   <li>{@link EventSubscriptionManager} — event subscription creation</li>
 *   <li>{@link BucketSuspensionManager}  — BUCKET node side-effects</li>
 *   <li>{@link ResumeRouter}         — resume-from-suspended logic</li>
 *   <li>{@link NodeExecutorRegistry} — per-node-type execution strategies</li>
 * </ul>
 *
 * <p><b>Rule evaluation contract (SpEL):</b>
 * Expressions are written in terms of a root object {@code context}, e.g.:
 * <pre>
 *   context['amount'] > 5000
 *   context.status == 'APPROVED'
 *   context['risk'] != null &amp;&amp; context['risk'] > 80
 * </pre>
 * The edge's {@code data.condition} field (String) holds the expression.
 * The first outgoing edge whose condition evaluates to {@code true} (Boolean) is taken.
 * If no edge matches, the first unconditional edge is taken as a fallback.
 */
@Component
public class GraphTraversalEngine {

    private static final Logger log = LoggerFactory.getLogger(GraphTraversalEngine.class);
    private static final int MAX_STEPS = 200; // circuit-breaker for infinite loops

    // ---- Collaborator beans ----

    @Autowired private SpelEvaluator            spelEvaluator;
    @Autowired private TaskRecorder             taskRecorder;
    @Autowired private NodeExecutorRegistry     nodeExecutorRegistry;
    @Autowired private WorkflowInstanceRepository instanceRepository;
    @Autowired private CommandRegistry          commandRegistry;
    @Autowired private ActivationBasedEngine    activationBasedEngine;
    @Autowired private SequentialEngine         sequentialEngine;
    @Autowired private TraversalHelper          traversalHelper;

    @Autowired @Lazy private EventRoutingService eventRoutingService;

    // ---- ThreadLocal traversal context (shared with collaborators) ----

    /** Public static ThreadLocal so traversal engine collaborators and node executors can access it. */
    public static final ThreadLocal<TraversalContext> CURRENT_TRAVERSAL = new ThreadLocal<>();

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Attempts to resume a suspended node <em>synchronously within the same
     * traversal pass</em> — delegates to {@link ResumeRouter}.
     */
    public static boolean trySynchronousResumption(String instanceId,
                                                    String eventType,
                                                    String targetNodeId,
                                                    Map<String, Object> payload) {
        return ResumeRouter.trySynchronousResumption(instanceId, eventType, targetNodeId, payload);
    }

    /**
     * TraversalResult value object — unchanged public API.
     */
    public static class TraversalResult {
        private final List<StepRecordDto> trace;
        private final boolean suspended;
        private final String suspendedNodeId;
        private final String suspendedNodeLabel;
        private final String outcomeBucketId;
        private final Map<String, Object> runtimeGraph;

        public TraversalResult(List<StepRecordDto> trace, boolean suspended,
                                String suspendedNodeId, String suspendedNodeLabel,
                                String outcomeBucketId, Map<String, Object> runtimeGraph) {
            this.trace             = trace;
            this.suspended         = suspended;
            this.suspendedNodeId   = suspendedNodeId;
            this.suspendedNodeLabel = suspendedNodeLabel;
            this.outcomeBucketId   = outcomeBucketId;
            this.runtimeGraph      = runtimeGraph;
        }

        public List<StepRecordDto> getTrace()          { return trace; }
        public boolean isSuspended()                   { return suspended; }
        public String getSuspendedNodeId()             { return suspendedNodeId; }
        public String getSuspendedNodeLabel()          { return suspendedNodeLabel; }
        public String getOutcomeBucketId()             { return outcomeBucketId; }
        public Map<String, Object> getRuntimeGraph()   { return runtimeGraph; }
    }

    // -----------------------------------------------------------------------
    // Main entry point
    // -----------------------------------------------------------------------

    /**
     * Execute the graph and return a structured {@link TraversalResult}.
     *
     * @param version     the published workflow version to execute
     * @param context     the caller-supplied context map
     * @param startNodeId optional node ID to resume execution from (if previously suspended)
     * @param instanceId  the workflow instance ID (may be {@code null} for replay/simulation)
     * @return {@code TraversalResult} detailing trace, status, and outcome/suspended node
     */
    public TraversalResult traverse(WorkflowVersion version,
                                     Map<String, Object> context,
                                     String startNodeId,
                                     String instanceId) {
        WorkflowGraphDto graph = version.getDefinition();
        List<StepRecordDto> trace = new ArrayList<>();

        // Resolve contextId for CustomerForm tracking
        String contextId = context instanceof LazyContextMap
                ? ((LazyContextMap) context).getContextId()
                : (String) context.get("contextId");

        log.info("Engine starting/resuming traversal for workflowKey={}, versionNumber={}, " +
                 "instanceId={}, startNodeId={}, contextId={}",
                version.getWorkflowDefinition().getKey(), version.getVersion(),
                instanceId, startNodeId, contextId);

        // ---- Build graph index ----
        Map<String, WorkflowNodeDto> nodeMap = graph.getNodes().stream()
                .collect(Collectors.toMap(WorkflowNodeDto::getId, n -> n));

        Map<String, List<WorkflowEdgeDto>> edgesBySource = new HashMap<>();
        Map<String, List<WorkflowEdgeDto>> edgesByTarget = new HashMap<>();
        for (WorkflowEdgeDto edge : graph.getEdges()) {
            edgesBySource.computeIfAbsent(edge.getSource(), k -> new ArrayList<>()).add(edge);
            edgesByTarget.computeIfAbsent(edge.getTarget(), k -> new ArrayList<>()).add(edge);
        }

        // ---- SpEL context ----
        Map<String, Object> root = Map.of("context", context);
        StandardEvaluationContext spelCtx = new StandardEvaluationContext(root);
        spelCtx.addPropertyAccessor(new org.springframework.context.expression.MapAccessor());
        spelCtx.setVariable("context", context);

        // ---- Load workflow instance & runtime graph ----
        WorkflowInstance instance = null;
        if (instanceId != null) {
            instance = instanceRepository.findById(instanceId).orElse(null);
        }

        Map<String, Object> runtimeGraph = (instance != null && instance.getRuntimeGraph() != null)
                ? instance.getRuntimeGraph() : new HashMap<>();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> activeNodes =
                (List<Map<String, Object>>) runtimeGraph.computeIfAbsent("nodes", k -> new ArrayList<>());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> activeEdges =
                (List<Map<String, Object>>) runtimeGraph.computeIfAbsent("edges", k -> new ArrayList<>());

        // ---- Pre-load task status cache ----
        Map<String, String> localTaskStatuses = taskRecorder.loadTaskStatuses(instanceId);

        // ---- Collect existing active node IDs ----
        Set<String> activeNodeIds = activeNodes.stream()
                .map(n -> (String) n.get("id"))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        // ---- Build TraversalContext (ThreadLocal) ----
        List<WorkflowNodeDto> activeFrontiers = new ArrayList<>();
        List<WorkflowNodeDto> suspendedNodes  = new ArrayList<>();

        TraversalContext travCtx = new TraversalContext();
        travCtx.instanceId        = instanceId;
        travCtx.activeFrontiers   = activeFrontiers;
        travCtx.nodeMap           = nodeMap;
        travCtx.edgesBySource     = edgesBySource;
        travCtx.edgesByTarget     = edgesByTarget;
        travCtx.activeEdges       = activeEdges;
        travCtx.context           = context;
        travCtx.suspendedNodes    = suspendedNodes;
        travCtx.localTaskStatuses = localTaskStatuses;
        travCtx.activeNodeIds     = activeNodeIds;
        CURRENT_TRAVERSAL.set(travCtx);
        TraversalContextHolder.set(travCtx);

        // ---- Build TraversalExecutionState ----
        int[] stepIdx = {0};
        TraversalExecutionState state = new TraversalExecutionState(
                version, instanceId, contextId, instance, context, spelCtx,
                nodeMap, edgesBySource, edgesByTarget,
                activeNodes, activeEdges, runtimeGraph, suspendedNodes, stepIdx);

        // ---- Wire CommandNodeExecutor delegates ----
        CommandNodeExecutor commandExecutor =
                nodeExecutorRegistry.get("COMMAND") instanceof CommandNodeExecutor
                        ? (CommandNodeExecutor) nodeExecutorRegistry.get("COMMAND")
                        : null;
        if (commandExecutor != null) {
            commandExecutor.setCommandDelegate(this::executeCommandNode);
            commandExecutor.setAsyncDelegate(this::triggerAsyncCommand);
        }

        try {
            // Decide which traversal algorithm to run
            if (isActivationBased(graph)) {
                return activationBasedEngine.runActivationBasedTraversal(graph, state, startNodeId, trace, nodeMap,
                        activeNodes, activeEdges, runtimeGraph, suspendedNodes, spelCtx,
                        instance, instanceId, contextId, version, stepIdx);
            } else {
                return sequentialEngine.runSequentialTraversal(graph, state, startNodeId, trace, nodeMap,
                        edgesBySource, activeNodes, activeEdges, runtimeGraph,
                        activeFrontiers, suspendedNodes, spelCtx, instance, instanceId,
                        contextId, version, stepIdx, context);
            }
        } finally {
            CURRENT_TRAVERSAL.remove();
            TraversalContextHolder.remove();
        }
    }

    // -----------------------------------------------------------------------
    // Activation-based traversal
    // -----------------------------------------------------------------------

    // Method removed after delegating to ActivationBasedEngine

    // -----------------------------------------------------------------------
    // Sequential (pointer-based) traversal
    // -----------------------------------------------------------------------

    // -----------------------------------------------------------------------
    // Activation-based helpers
    // -----------------------------------------------------------------------

    /** Determines whether the graph uses activation-condition / join-type patterns. */
    private boolean isActivationBased(WorkflowGraphDto graph) {
        for (WorkflowNodeDto node : graph.getNodes()) {
            if (node.getData() == null) continue;
            String cond = traversalHelper.extractString(node.getData(), "activationCondition");
            String rule = traversalHelper.extractString(node.getData(), "businessEligibilityRule");
            String join = traversalHelper.extractString(node.getData(), "joinType");
            if ((cond != null && !cond.trim().isEmpty())
                    || (rule != null && !rule.trim().isEmpty())
                    || (join != null && !join.trim().isEmpty())) {
                return true;
            }
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Command execution (kept here because it needs CommandRegistry)
    // -----------------------------------------------------------------------

    /**
     * Executes a registered {@link WorkflowCommand} for a COMMAND node.
     * Resolves the command type, builds the input map (with SpEL inputMapping),
     * executes the command, and applies outputMapping back to context.
     */
    private Map<String, Object> executeCommandNode(WorkflowNodeDto node,
                                                    WorkflowInstance instance,
                                                    Map<String, Object> context,
                                                    String instanceId,
                                                    String contextId,
                                                    WorkflowVersion version,
                                                    StandardEvaluationContext spelCtx) {
        String tempType = extractString(node.getData(), "commandType");
        if (tempType == null) tempType = extractString(node.getData(), "type");
        if (tempType == null) {
            throw new IllegalArgumentException(
                    "COMMAND node '" + node.getId() + "' has no configured commandType or type.");
        }
        final String commandType = tempType;

        WorkflowCommand command = commandRegistry.getCommand(commandType)
                .orElseThrow(() -> new IllegalArgumentException("Unknown command type: " + commandType));

        // Build input map from node data + execution metadata
        Map<String, Object> input = new HashMap<>();
        if (node.getData() != null) input.putAll(node.getData());
        input.put("_instanceId",  instanceId);
        input.put("_contextId",   contextId);
        input.put("_workflowKey", version.getWorkflowDefinition().getKey());
        input.put("_nodeId",      node.getId());
        input.put("_nodeLabel",   node.getLabel() != null ? node.getLabel() : node.getId());
        input.put("_context",     context);

        // Apply SpEL inputMapping
        Object inputMappingObj = node.getData() != null ? node.getData().get("inputMapping") : null;
        if (inputMappingObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<?, ?> inputMap = (Map<?, ?>) inputMappingObj;
            for (Map.Entry<?, ?> entry : inputMap.entrySet()) {
                String sourceExpr = String.valueOf(entry.getKey());
                String targetVar  = String.valueOf(entry.getValue());
                Object resolved;
                if (sourceExpr.contains(".") || sourceExpr.contains("[")
                        || sourceExpr.contains("'") || sourceExpr.contains("context")) {
                    try { resolved = spelEvaluator.evaluate(sourceExpr, spelCtx); }
                    catch (Exception e) { resolved = context.get(sourceExpr); }
                } else {
                    resolved = context.get(sourceExpr);
                }
                if (resolved != null) {
                    input.put(targetVar, resolved);
                    input.put(sourceExpr, resolved);
                }
            }
        }

        Map<String, Object> output;
        try {
            output = command.execute(input);
        } catch (Exception e) {
            log.error("Command execution failed for node '{}': {}", node.getId(), e.getMessage(), e);
            throw new RuntimeException("Command execution failed: " + e.getMessage(), e);
        }

        // Apply output mapping back to context
        Object outputMappingObj = node.getData() != null ? node.getData().get("outputMapping") : null;
        if (outputMappingObj instanceof Map && output != null) {
            @SuppressWarnings("unchecked")
            Map<?, ?> outputMap = (Map<?, ?>) outputMappingObj;
            for (Map.Entry<?, ?> entry : outputMap.entrySet()) {
                String outputKey       = String.valueOf(entry.getKey());
                String targetContextKey = String.valueOf(entry.getValue());
                Object val = output.get(outputKey);
                if (val != null) {
                    String contextKey = targetContextKey.startsWith("context.")
                            ? targetContextKey.substring(8) : targetContextKey;
                    context.put(contextKey, val);
                }
            }
        }

        return output != null ? output : Map.of();
    }

    /**
     * Schedules a COMMAND node to run asynchronously after the current DB transaction.
     * Fires a resume event via {@link EventRoutingService} when complete.
     */
    private void triggerAsyncCommand(WorkflowNodeDto node,
                                      WorkflowInstance instance,
                                      Map<String, Object> backgroundContext,
                                      String instanceId,
                                      String contextId,
                                      WorkflowVersion version,
                                      String eventType) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(100);
                StandardEvaluationContext bgSpel = new StandardEvaluationContext();
                bgSpel.setVariable("context", backgroundContext);
                Map<String, Object> commandOutput =
                        executeCommandNode(node, instance, backgroundContext, instanceId, contextId, version, bgSpel);
                eventRoutingService.routeEvent(eventType, instance.getBusinessKey(), commandOutput);
                log.info("Completed async command for node: {} and routed resume event.", node.getId());
            } catch (Exception ex) {
                log.error("Error in async command background task for node: {}", node.getId(), ex);
                eventRoutingService.routeEvent(eventType, instance.getBusinessKey(),
                        Map.of("error", ex.getMessage()));
            }
        });
    }

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------

    private String extractString(Map<String, Object> data, String key) {
        if (data == null) return null;
        Object val = data.get(key);
        return val != null ? String.valueOf(val) : null;
    }
}
