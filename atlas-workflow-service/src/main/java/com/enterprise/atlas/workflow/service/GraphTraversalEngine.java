package com.enterprise.atlas.workflow.service;

import com.enterprise.atlas.common.dto.*;
import com.enterprise.atlas.workflow.entity.WorkflowDefinition;
import com.enterprise.atlas.workflow.entity.WorkflowVersion;
import com.enterprise.atlas.workflow.entity.Rule;
import com.enterprise.atlas.workflow.entity.Bucket;
import com.enterprise.atlas.workflow.entity.BucketExecution;
import com.enterprise.atlas.workflow.entity.SubscriptionStatus;
import com.enterprise.atlas.workflow.repository.RuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
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
 * <p><b>Rule evaluation contract (SpEL):</b>
 * Expressions are written in terms of a root object {@code context}, e.g.:
 * <pre>
 *   context['amount'] > 5000
 *   context.status == 'APPROVED'
 *   context['risk'] != null && context['risk'] > 80
 * </pre>
 * The edge's {@code data.condition} field (String) holds the expression.
 * The first outgoing edge whose condition evaluates to {@code true} (Boolean) is taken.
 * If no edge matches, the first unconditional edge is taken as a fallback.
 */
@Component
public class GraphTraversalEngine {

    private static final Logger log = LoggerFactory.getLogger(GraphTraversalEngine.class);

    @Autowired
    private RuleRepository ruleRepository;

    @Autowired
    private com.enterprise.atlas.workflow.repository.RevertStatusRepository revertStatusRepository;

    @Autowired
    private com.enterprise.atlas.workflow.repository.CustomerFormRepository customerFormRepository;

    @Autowired
    private com.enterprise.atlas.workflow.repository.WorkflowInstanceRepository instanceRepository;

    @Autowired
    private com.enterprise.atlas.workflow.repository.TaskInstanceRepository taskInstanceRepository;

    @Autowired
    private com.enterprise.atlas.workflow.repository.EventSubscriptionRepository eventSubscriptionRepository;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private ExecutionService executionService;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private EventRoutingService eventRoutingService;

    @Autowired
    private com.enterprise.atlas.workflow.repository.BucketExecutionRepository bucketExecutionRepository;

    @Autowired
    private com.enterprise.atlas.workflow.repository.BucketRepository bucketRepository;

    @Autowired
    private CommandRegistry commandRegistry;

    private static final ExpressionParser SPEL = new SpelExpressionParser();
    private static final int MAX_STEPS = 200; // circuit-breaker for infinite loops

    public static class TraversalResult {
        private final List<StepRecordDto> trace;
        private final boolean suspended;
        private final String suspendedNodeId;
        private final String suspendedNodeLabel;
        private final String outcomeBucketId;

        public TraversalResult(List<StepRecordDto> trace, boolean suspended, String suspendedNodeId, String suspendedNodeLabel, String outcomeBucketId) {
            this.trace = trace;
            this.suspended = suspended;
            this.suspendedNodeId = suspendedNodeId;
            this.suspendedNodeLabel = suspendedNodeLabel;
            this.outcomeBucketId = outcomeBucketId;
        }

        public List<StepRecordDto> getTrace() { return trace; }
        public boolean isSuspended() { return suspended; }
        public String getSuspendedNodeId() { return suspendedNodeId; }
        public String getSuspendedNodeLabel() { return suspendedNodeLabel; }
        public String getOutcomeBucketId() { return outcomeBucketId; }
    }

    private Map<String, Object> convertNodeToMap(WorkflowNodeDto node) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", node.getId());
        map.put("type", node.getType());
        map.put("label", node.getLabel());
        map.put("data", node.getData());
        map.put("position", node.getPosition());
        return map;
    }

    private Map<String, Object> convertEdgeToMap(WorkflowEdgeDto edge) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", edge.getId());
        map.put("source", edge.getSource());
        map.put("target", edge.getTarget());
        map.put("label", edge.getLabel());
        map.put("data", edge.getData());
        return map;
    }

    private com.enterprise.atlas.workflow.entity.TaskInstance recordTaskStart(com.enterprise.atlas.workflow.entity.WorkflowInstance instance, WorkflowNodeDto node, Map<String, Object> input) {
        if (instance == null) return null;
        com.enterprise.atlas.workflow.entity.TaskInstance ti = new com.enterprise.atlas.workflow.entity.TaskInstance();
        ti.setId(instance.getId() + "_" + node.getId() + "_" + System.currentTimeMillis());
        ti.setWorkflowInstance(instance);
        ti.setTaskType(node.getType());
        ti.setLabel(node.getLabel() != null ? node.getLabel() : node.getType());
        ti.setStatus("RUNNING");
        ti.setInputData(new HashMap<>(input));
        ti.setStartedAt(LocalDateTime.now());
        return taskInstanceRepository.save(ti);
    }

    private void recordTaskCompletion(com.enterprise.atlas.workflow.entity.TaskInstance ti, Map<String, Object> output, String status) {
        if (ti == null) return;
        ti.setStatus(status);
        ti.setOutputData(new HashMap<>(output));
        ti.setCompletedAt(LocalDateTime.now());
        taskInstanceRepository.save(ti);
    }

    private void createEventSubscription(com.enterprise.atlas.workflow.entity.WorkflowInstance instance, String eventType, String targetNodeId, Map<String, Object> filters) {
        if (instance == null) return;
        List<com.enterprise.atlas.workflow.entity.EventSubscription> existing = eventSubscriptionRepository
                .findByBusinessKeyAndEventTypeAndStatus(instance.getBusinessKey(), eventType, SubscriptionStatus.ACTIVE);
        boolean exists = existing.stream().anyMatch(sub -> sub.getTargetNodeId().equals(targetNodeId));
        if (exists) return;

        com.enterprise.atlas.workflow.entity.EventSubscription sub = new com.enterprise.atlas.workflow.entity.EventSubscription();
        sub.setId(UUID.randomUUID().toString());
        sub.setBusinessKey(instance.getBusinessKey());
        sub.setEventType(eventType);
        sub.setTargetNodeId(targetNodeId);
        sub.setWorkflowInstance(instance);
        sub.setFilterAttributes(filters != null ? filters : Map.of());
        sub.setStatus("ACTIVE");
        eventSubscriptionRepository.save(sub);
        log.info("Created EventSubscription for instance: {}, eventType: {}, targetNode: {}", instance.getId(), eventType, targetNodeId);
    }

    /**
     * Execute the graph and return a structured TraversalResult.
     *
     * @param version the published workflow version to execute
     * @param context the caller-supplied context map
     * @param startNodeId optional node ID to resume execution from (if suspended)
     * @return TraversalResult detailing trace, status, and outcome/suspended node
     */
    public TraversalResult traverse(WorkflowVersion version, Map<String, Object> context, String startNodeId, String instanceId) {
        WorkflowGraphDto graph = version.getDefinition();
        List<StepRecordDto> trace = new ArrayList<>();

        String contextId = null;
        if (context instanceof LazyContextMap) {
            contextId = ((LazyContextMap) context).getContextId();
        } else {
            contextId = (String) context.get("contextId");
        }

        log.info("Engine starting/resuming traversal for workflowKey={}, versionNumber={}, instanceId={}, startNodeId={}, contextId={}",
                 version.getWorkflowDefinition().getKey(), version.getVersion(), instanceId, startNodeId, contextId);

        // Build lookup maps
        Map<String, WorkflowNodeDto> nodeMap = graph.getNodes().stream()
                .collect(Collectors.toMap(WorkflowNodeDto::getId, n -> n));
        // edges by source node id
        Map<String, List<WorkflowEdgeDto>> edgesBySource = new HashMap<>();
        for (WorkflowEdgeDto edge : graph.getEdges()) {
            edgesBySource.computeIfAbsent(edge.getSource(), k -> new ArrayList<>()).add(edge);
        }

        // SpEL evaluation context – expose context map as root object named "context"
        Map<String, Object> root = Map.of("context", context);
        StandardEvaluationContext spelCtx = new StandardEvaluationContext(root);
        spelCtx.addPropertyAccessor(new org.springframework.context.expression.MapAccessor());
        spelCtx.setVariable("context", context);

        int stepIdx = 0;

        // Load instance for runtime graph persistence
        com.enterprise.atlas.workflow.entity.WorkflowInstance instance = null;
        if (instanceId != null) {
            instance = instanceRepository.findById(instanceId).orElse(null);
        }

        Map<String, Object> runtimeGraph = null;
        if (instance != null) {
            runtimeGraph = instance.getRuntimeGraph();
        }
        if (runtimeGraph == null) {
            runtimeGraph = new HashMap<>();
        }
        List<Map<String, Object>> activeNodes = (List<Map<String, Object>>) runtimeGraph.computeIfAbsent("nodes", k -> new ArrayList<>());
        List<Map<String, Object>> activeEdges = (List<Map<String, Object>>) runtimeGraph.computeIfAbsent("edges", k -> new ArrayList<>());

        // Check if any node in the graph has activationCondition
        boolean isActivationBased = false;
        for (WorkflowNodeDto node : graph.getNodes()) {
            if (node.getData() != null && node.getData().containsKey("activationCondition")) {
                String cond = extractString(node.getData(), "activationCondition");
                if (cond != null && !cond.trim().isEmpty()) {
                    isActivationBased = true;
                    break;
                }
            }
        }

        if (isActivationBased) {
            // --- REACTIVE ACTIVATION-BASED TRAVERSAL ---
            log.info("Running activation-based traversal loop for instance: {}", instanceId);

            // Find START node
            WorkflowNodeDto startNode = graph.getNodes().stream()
                    .filter(n -> "START".equalsIgnoreCase(n.getType()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Workflow graph has no START node"));

            // Initialize with START node if starting fresh
            boolean isFreshStart = activeNodes.isEmpty();
            if (isFreshStart) {
                activeNodes.add(convertNodeToMap(startNode));
                if (instance != null) {
                    instance.setRuntimeGraph(runtimeGraph);
                }
                StepRecordDto startStep = buildStep(stepIdx++, startNode, "ENTERED", null, null, null, "Workflow execution started (Activation-Based).");
                trace.add(startStep);
            } else if (startNodeId != null && !startNodeId.isBlank()) {
                // Resume logic: Log a step indicating we are resuming
                WorkflowNodeDto suspendedNode = nodeMap.get(startNodeId);
                if (suspendedNode != null) {
                    if ("SUB_WORKFLOW".equalsIgnoreCase(suspendedNode.getType())) {
                        // Perform output mapping
                        Map<String, Object> childOutputs = (Map<String, Object>) context.get("childOutputs");
                        if (childOutputs != null) {
                            Object outputMappingObj = suspendedNode.getData().get("outputMapping");
                            if (outputMappingObj instanceof Map) {
                                Map<?, ?> outputMap = (Map<?, ?>) outputMappingObj;
                                for (Map.Entry<?, ?> entry : outputMap.entrySet()) {
                                    String childVar = String.valueOf(entry.getKey());
                                    String parentVar = String.valueOf(entry.getValue());
                                    Object val = childOutputs.get(childVar);
                                    if (val != null) {
                                        context.put(parentVar, val);
                                    }
                                }
                            }
                            context.remove("childOutputs");
                            context.remove("childInstanceId");
                        }
                    } else if ("WAIT_EVENT".equalsIgnoreCase(suspendedNode.getType())) {
                        String routingValueStr = null;
                        for (String key : List.of("status", "value", "outcome")) {
                            if (context.containsKey(key) && context.get(key) != null) {
                                routingValueStr = String.valueOf(context.get(key));
                                break;
                            }
                        }
                        String targetNodeId = null;
                        Object routesObj = suspendedNode.getData() != null ? suspendedNode.getData().get("routes") : null;
                        if (routesObj instanceof List) {
                            List<?> routesList = (List<?>) routesObj;
                            for (Object routeItem : routesList) {
                                if (routeItem instanceof Map) {
                                    Map<?, ?> routeMap = (Map<?, ?>) routeItem;
                                    String val = String.valueOf(routeMap.get("value"));
                                    String target = String.valueOf(routeMap.get("target"));
                                    if (val != null && val.equalsIgnoreCase(routingValueStr)) {
                                        targetNodeId = target;
                                        break;
                                    }
                                }
                            }
                        }
                        if (targetNodeId == null && suspendedNode.getData() != null) {
                            targetNodeId = extractString(suspendedNode.getData(), "defaultRoute");
                        }
                        final String finalTargetNodeId = targetNodeId;
                        if (targetNodeId != null && !targetNodeId.isBlank() && nodeMap.containsKey(targetNodeId)) {
                            WorkflowNodeDto targetNode = nodeMap.get(targetNodeId);
                            boolean hasNode = activeNodes.stream().anyMatch(n -> finalTargetNodeId.equals(n.get("id")));
                            if (!hasNode) {
                                activeNodes.add(convertNodeToMap(targetNode));
                            }
                            final String startId = startNodeId;
                            String finalTarget = targetNodeId;
                            WorkflowEdgeDto edgeObj = graph.getEdges().stream()
                                    .filter(e -> startId.equals(e.getSource()) && finalTarget.equals(e.getTarget()))
                                    .findFirst()
                                    .orElse(null);
                            if (edgeObj != null) {
                                boolean hasEdge = activeEdges.stream().anyMatch(e -> edgeObj.getId().equals(e.get("id")));
                                if (!hasEdge) {
                                    activeEdges.add(convertEdgeToMap(edgeObj));
                                }
                            }
                        }
                    }

                    StepRecordDto resumeStep = new StepRecordDto();
                    resumeStep.setStepIndex(stepIdx++);
                    resumeStep.setNodeId(startNodeId);
                    resumeStep.setNodeType(suspendedNode.getType());
                    resumeStep.setLabel(suspendedNode.getLabel());
                    resumeStep.setStatus("RESUMED");
                    resumeStep.setNotes("Resumed workflow execution from suspended node: " + suspendedNode.getLabel());
                    resumeStep.setEnteredAt(LocalDateTime.now());
                    resumeStep.setExitedAt(LocalDateTime.now());
                    trace.add(resumeStep);
                }
            }

            boolean progress = true;
            boolean suspended = false;
            String suspendedNodeId = null;
            String suspendedNodeLabel = null;
            String outcomeBucketId = null;

            while (progress && !suspended && stepIdx < MAX_STEPS) {
                progress = false;

                for (WorkflowNodeDto node : graph.getNodes()) {
                    final String nodeId = node.getId();
                    boolean isAlreadyActive = activeNodes.stream().anyMatch(n -> nodeId.equals(n.get("id")));
                    if (isAlreadyActive) {
                        continue;
                    }

                    boolean canActivate = false;
                    String activationCond = null;
                    if (node.getData() != null && node.getData().containsKey("activationCondition")) {
                        activationCond = extractString(node.getData(), "activationCondition");
                    }

                    if (activationCond != null && !activationCond.trim().isEmpty()) {
                        Object result = evaluateSpel(activationCond, spelCtx);
                        if (Boolean.TRUE.equals(result)) {
                            canActivate = true;
                        }
                    } else {
                        // If no condition, it activates if at least one of its predecessor nodes is active
                        List<WorkflowEdgeDto> incomingEdges = graph.getEdges().stream()
                                .filter(e -> nodeId.equals(e.getTarget()))
                                .collect(Collectors.toList());
                        if (!incomingEdges.isEmpty()) {
                            boolean hasActivePredecessor = false;
                            for (WorkflowEdgeDto edge : incomingEdges) {
                                final String srcId = edge.getSource();
                                boolean isSrcActive = activeNodes.stream().anyMatch(n -> srcId.equals(n.get("id")));
                                if (isSrcActive) {
                                    hasActivePredecessor = true;
                                    break;
                                }
                            }
                            if (hasActivePredecessor) {
                                canActivate = true;
                            }
                        }
                    }

                    if (canActivate) {
                        activeNodes.add(convertNodeToMap(node));

                        // Map in incoming edges to build runtime graph visually
                        List<WorkflowEdgeDto> incomingEdges = graph.getEdges().stream()
                                .filter(e -> nodeId.equals(e.getTarget()))
                                .collect(Collectors.toList());
                        for (WorkflowEdgeDto edge : incomingEdges) {
                            final String srcId = edge.getSource();
                            boolean isSrcActive = activeNodes.stream().anyMatch(n -> srcId.equals(n.get("id")));
                            if (isSrcActive) {
                                boolean isEdgeAlreadyActive = activeEdges.stream().anyMatch(e -> edge.getId().equals(e.get("id")));
                                if (!isEdgeAlreadyActive) {
                                    activeEdges.add(convertEdgeToMap(edge));
                                }
                            }
                        }

                        if (instance != null) {
                            instance.setRuntimeGraph(runtimeGraph);
                        }

                        String nodeType = Optional.ofNullable(node.getType()).orElse("UNKNOWN").toUpperCase();
                        StepRecordDto step = new StepRecordDto();
                        step.setStepIndex(stepIdx++);
                        step.setNodeId(node.getId());
                        step.setNodeType(nodeType);
                        step.setLabel(Optional.ofNullable(node.getLabel()).orElse(nodeType));
                        step.setEnteredAt(LocalDateTime.now());

                        if ("BUCKET".equals(nodeType)) {
                            com.enterprise.atlas.workflow.entity.TaskInstance ti = recordTaskStart(instance, node, context);
                            recordTaskCompletion(ti, Map.of(), "WAITING");

                            step.setStatus("WAITING");
                            step.setNotes("Suspended execution. Waiting on business outcome bucket: " + step.getLabel());
                            step.setExitedAt(LocalDateTime.now());
                            trace.add(step);

                            if (node.getData() != null) {
                                outcomeBucketId = (String) node.getData().get("bucketId");
                            }
                            if (outcomeBucketId == null) {
                                outcomeBucketId = node.getId();
                            }

                            createBucketRevertStatusAndFormPending(instanceId, contextId, outcomeBucketId, node, version);
                            createEventSubscription(instance, outcomeBucketId, node.getId(), Map.of());

                            suspended = true;
                            suspendedNodeId = node.getId();
                            suspendedNodeLabel = node.getLabel();
                            break;
                        } else if ("SUB_WORKFLOW".equals(nodeType)) {
                            com.enterprise.atlas.workflow.entity.TaskInstance ti = recordTaskStart(instance, node, context);
                            String childWorkflowKey = extractString(node.getData(), "childWorkflowKey");
                            if (childWorkflowKey == null || childWorkflowKey.isBlank()) {
                                step.setStatus("FAILED");
                                step.setNotes("SUB_WORKFLOW node has no childWorkflowKey configured.");
                                step.setExitedAt(LocalDateTime.now());
                                trace.add(step);
                                recordTaskCompletion(ti, Map.of(), "FAILED");
                                suspended = true;
                                break;
                            }

                            // Map input context
                            Map<String, Object> childInput = new HashMap<>();
                            if (instance != null && instance.getBusinessKey() != null) {
                                childInput.put("businessKey", instance.getBusinessKey());
                            }
                            if (context.get("businessKey") != null) {
                                childInput.put("businessKey", context.get("businessKey"));
                            }
                            if (context.get("cafId") != null) {
                                childInput.put("cafId", context.get("cafId"));
                            }

                            Object inputMappingObj = node.getData().get("inputMapping");
                            if (inputMappingObj instanceof Map) {
                                Map<?, ?> inputMap = (Map<?, ?>) inputMappingObj;
                                for (Map.Entry<?, ?> entry : inputMap.entrySet()) {
                                    String sourceVar = String.valueOf(entry.getKey());
                                    String targetVar = String.valueOf(entry.getValue());
                                    Object value = null;
                                    if (sourceVar.contains(".") || sourceVar.contains("[") || sourceVar.contains("'")) {
                                        try {
                                            value = evaluateSpel(sourceVar, spelCtx);
                                        } catch (Exception e) {
                                            value = context.get(sourceVar);
                                        }
                                    } else {
                                        value = context.get(sourceVar);
                                    }
                                    if (value != null) {
                                        childInput.put(targetVar, value);
                                    }
                                }
                            }

                            try {
                                ExecutionRequestDto childRequest = new ExecutionRequestDto();
                                childRequest.setBusinessKey(instance != null ? instance.getBusinessKey() : null);
                                childRequest.setContext(childInput);
                                childRequest.setContextId(UUID.randomUUID().toString());

                                ExecutionLogDto childExecution = executionService.execute(childWorkflowKey, childRequest);

                                if ("COMPLETED".equalsIgnoreCase(childExecution.getStatus())) {
                                    step.setStatus("COMPLETED");
                                    step.setNotes("Sub-workflow '" + childWorkflowKey + "' completed synchronously.");
                                    step.setExitedAt(LocalDateTime.now());
                                    trace.add(step);

                                    com.enterprise.atlas.workflow.entity.WorkflowInstance childInst = instanceRepository.findById(childExecution.getInstanceId()).orElse(null);
                                    Map<String, Object> childOutputs = childInst != null ? childInst.getSerializedContext() : Map.of();

                                    Object outputMappingObj = node.getData().get("outputMapping");
                                    if (outputMappingObj instanceof Map && childOutputs != null) {
                                        Map<?, ?> outputMap = (Map<?, ?>) outputMappingObj;
                                        for (Map.Entry<?, ?> entry : outputMap.entrySet()) {
                                            String childVar = String.valueOf(entry.getKey());
                                            String parentVar = String.valueOf(entry.getValue());
                                            Object val = childOutputs.get(childVar);
                                            if (val != null) {
                                                context.put(parentVar, val);
                                            }
                                        }
                                    }

                                    recordTaskCompletion(ti, childOutputs != null ? childOutputs : Map.of(), "COMPLETED");
                                } else {
                                    step.setStatus("WAITING");
                                    step.setNotes("Sub-workflow '" + childWorkflowKey + "' suspended. Waiting for child instance completion (ID: " + childExecution.getInstanceId() + ").");
                                    step.setExitedAt(LocalDateTime.now());
                                    trace.add(step);

                                    recordTaskCompletion(ti, Map.of("childInstanceId", childExecution.getInstanceId()), "WAITING");
                                    createEventSubscription(instance, "CHILD_WORKFLOW_COMPLETED", node.getId(), Map.of("childInstanceId", childExecution.getInstanceId()));

                                    suspended = true;
                                    suspendedNodeId = node.getId();
                                    suspendedNodeLabel = node.getLabel();
                                    break;
                                }
                            } catch (Exception e) {
                                log.error("Failed to execute child workflow '{}': {}", childWorkflowKey, e.getMessage(), e);
                                step.setStatus("FAILED");
                                step.setNotes("Failed to execute child workflow: " + e.getMessage());
                                step.setExitedAt(LocalDateTime.now());
                                trace.add(step);
                                recordTaskCompletion(ti, Map.of("error", e.getMessage()), "FAILED");
                                suspended = true;
                                break;
                            }
                        } else if ("COMMAND".equals(nodeType)) {
                            com.enterprise.atlas.workflow.entity.TaskInstance ti = recordTaskStart(instance, node, context);
                            String commandType = extractString(node.getData(), "commandType");
                            if (commandType == null) {
                                commandType = extractString(node.getData(), "type");
                            }
                            Map<String, Object> commandOutput = Map.of();
                            if (commandType != null) {
                                try {
                                    commandOutput = executeCommandNode(node, instance, context, instanceId, contextId, version, spelCtx);
                                    step.setStatus("COMPLETED");
                                    step.setNotes("Executed command: " + commandType);
                                } catch (Exception e) {
                                    step.setStatus("FAILED");
                                    step.setNotes("Failed to execute command: " + e.getMessage());
                                    recordTaskCompletion(ti, Map.of("error", e.getMessage()), "FAILED");
                                    suspended = true;
                                    break;
                                }
                            } else {
                                step.setStatus("COMPLETED");
                                step.setNotes("COMMAND node has no commandType configured.");
                            }
                            step.setExitedAt(LocalDateTime.now());
                            trace.add(step);
                            recordTaskCompletion(ti, commandOutput, "COMPLETED");
                        } else if ("WAIT_EVENT".equals(nodeType)) {
                            com.enterprise.atlas.workflow.entity.TaskInstance ti = recordTaskStart(instance, node, context);
                            recordTaskCompletion(ti, Map.of(), "WAITING");

                            String eventType = extractString(node.getData(), "eventType");
                            if (eventType == null || eventType.isBlank()) {
                                eventType = "GENERIC_EVENT";
                            }

                            step.setStatus("WAITING");
                            step.setNotes("Suspended execution. Waiting on event: " + eventType);
                            step.setExitedAt(LocalDateTime.now());
                            trace.add(step);

                            createEventSubscription(instance, eventType, node.getId(), Map.of());

                            suspended = true;
                            suspendedNodeId = node.getId();
                            suspendedNodeLabel = node.getLabel();
                            break;
                        } else if ("RULE".equals(nodeType)) {
                            com.enterprise.atlas.workflow.entity.TaskInstance ti = recordTaskStart(instance, node, context);
                            String expression = extractString(node.getData(), "expression");
                            String ruleId = extractString(node.getData(), "ruleId");
                            boolean expressionResolvedFailed = false;

                            if ((expression == null || expression.isBlank()) && (ruleId != null && !ruleId.isBlank())) {
                                Optional<Rule> ruleOpt = ruleRepository.findByRuleKey(ruleId);
                                if (ruleOpt.isPresent() && ruleOpt.get().isActive()) {
                                    expression = ruleOpt.get().getExpression();
                                    step.setNotes("Resolved registry rule [" + ruleId + "]: " + ruleOpt.get().getName());
                                } else {
                                    step.setStatus("FAILED");
                                    step.setNotes(ruleOpt.isEmpty() ? "Rule reference '" + ruleId + "' not found." : "Rule reference '" + ruleId + "' is inactive.");
                                    step.setExitedAt(LocalDateTime.now());
                                    trace.add(step);
                                    recordTaskCompletion(ti, Map.of(), "FAILED");
                                    suspended = true;
                                    break;
                                }
                            }

                            step.setExpression(expression);
                            if (expression != null && !expression.isBlank()) {
                                Object result = evaluateSpel(expression, spelCtx);
                                step.setExpressionResult(result);
                                boolean passed = Boolean.TRUE.equals(result);
                                step.setStatus("EVALUATED");
                                String explanation = explainEvaluation(expression, context, result);
                                step.setNotes(explanation);
                            } else {
                                step.setStatus("EVALUATED");
                                step.setNotes("RULE node has no expression or rule reference.");
                            }
                            step.setExitedAt(LocalDateTime.now());
                            trace.add(step);
                            recordTaskCompletion(ti, Map.of("expressionResult", step.getExpressionResult() != null ? step.getExpressionResult() : ""), "COMPLETED");
                        } else if ("DECISION".equals(nodeType)) {
                            com.enterprise.atlas.workflow.entity.TaskInstance ti = recordTaskStart(instance, node, context);
                            String expression = extractString(node.getData(), "expression");
                            Object fieldValue;
                            String expressionMeta;
                            String notePrefix;
                            
                            if (expression != null && !expression.isBlank()) {
                                fieldValue = evaluateSpel(expression, spelCtx);
                                expressionMeta = expression;
                                String explanation = explainEvaluation(expression, context, fieldValue);
                                step.setNotes(explanation);
                            } else {
                                String fieldKey = extractString(node.getData(), "decisionField");
                                if (fieldKey != null && fieldKey.trim().isEmpty()) {
                                    fieldKey = null;
                                }
                                fieldValue = fieldKey != null ? context.get(fieldKey) : null;
                                expressionMeta = fieldKey != null ? "context['" + fieldKey + "']" : null;
                                step.setNotes("Decision on context field: " + fieldKey + " = '" + fieldValue + "'");
                            }
                            
                            step.setExpression(expressionMeta);
                            step.setExpressionResult(fieldValue);
                            step.setStatus("ROUTED");
                            step.setExitedAt(LocalDateTime.now());
                            trace.add(step);
                            recordTaskCompletion(ti, Map.of("fieldValue", fieldValue != null ? fieldValue : ""), "COMPLETED");
                        } else if ("TIMER".equals(nodeType)) {
                            com.enterprise.atlas.workflow.entity.TaskInstance ti = recordTaskStart(instance, node, context);
                            String delayStr = extractString(node.getData(), "delayMs");
                            step.setStatus("COMPLETED");
                            step.setNotes("Timer node recorded (delay: " + (delayStr != null ? delayStr + "ms" : "unspecified") + ").");
                            step.setExitedAt(LocalDateTime.now());
                            trace.add(step);
                            recordTaskCompletion(ti, Map.of(), "COMPLETED");
                        } else if ("PARALLEL".equals(nodeType)) {
                            com.enterprise.atlas.workflow.entity.TaskInstance ti = recordTaskStart(instance, node, context);
                            step.setStatus("ENTERED");
                            step.setNotes("Parallel split recorded.");
                            step.setExitedAt(LocalDateTime.now());
                            trace.add(step);
                            recordTaskCompletion(ti, Map.of(), "COMPLETED");
                        } else if ("JOIN".equals(nodeType)) {
                            com.enterprise.atlas.workflow.entity.TaskInstance ti = recordTaskStart(instance, node, context);
                            step.setStatus("COMPLETED");
                            step.setNotes("Join convergence recorded.");
                            step.setExitedAt(LocalDateTime.now());
                            trace.add(step);
                            recordTaskCompletion(ti, Map.of(), "COMPLETED");
                        } else if ("END".equals(nodeType)) {
                            com.enterprise.atlas.workflow.entity.TaskInstance ti = recordTaskStart(instance, node, context);
                            step.setStatus("COMPLETED");
                            step.setNotes("Workflow execution reached END node.");
                            step.setExitedAt(LocalDateTime.now());
                            trace.add(step);
                            recordTaskCompletion(ti, Map.of(), "COMPLETED");
                        } else {
                            com.enterprise.atlas.workflow.entity.TaskInstance ti = recordTaskStart(instance, node, context);
                            step.setStatus("COMPLETED");
                            step.setExitedAt(LocalDateTime.now());
                            trace.add(step);
                            recordTaskCompletion(ti, Map.of(), "COMPLETED");
                        }

                        progress = true;
                        break;
                    }
                }
            }

            if (stepIdx >= MAX_STEPS) {
                log.warn("Activation-based loop halted: exceeded max steps ({})", MAX_STEPS);
            }

            return new TraversalResult(trace, suspended, suspendedNodeId, suspendedNodeLabel, outcomeBucketId);
        }

        // --- STANDARD SEQUENTIAL POINTER-BASED TRAVERSAL ---
        WorkflowNodeDto currentNode = null;

        if (startNodeId != null && !startNodeId.isBlank()) {
            // We are resuming from a suspended node (e.g. BUCKET)
            WorkflowNodeDto suspendedNode = nodeMap.get(startNodeId);
            if (suspendedNode == null) {
                throw new IllegalStateException("Suspended node not found in graph: " + startNodeId);
            }

            if ("SUB_WORKFLOW".equalsIgnoreCase(suspendedNode.getType())) {
                Map<String, Object> childOutputs = (Map<String, Object>) context.get("childOutputs");
                if (childOutputs != null) {
                    Object outputMappingObj = suspendedNode.getData().get("outputMapping");
                    if (outputMappingObj instanceof Map) {
                        Map<?, ?> outputMap = (Map<?, ?>) outputMappingObj;
                        for (Map.Entry<?, ?> entry : outputMap.entrySet()) {
                            String childVar = String.valueOf(entry.getKey());
                            String parentVar = String.valueOf(entry.getValue());
                            Object val = childOutputs.get(childVar);
                            if (val != null) {
                                context.put(parentVar, val);
                            }
                        }
                    }
                    context.remove("childOutputs");
                    context.remove("childInstanceId");
                }
            } else if ("WAIT_EVENT".equalsIgnoreCase(suspendedNode.getType())) {
                String routingValueStr = null;
                for (String key : List.of("status", "value", "outcome")) {
                    if (context.containsKey(key) && context.get(key) != null) {
                        routingValueStr = String.valueOf(context.get(key));
                        break;
                    }
                }
                
                String targetNodeId = null;
                Object routesObj = suspendedNode.getData() != null ? suspendedNode.getData().get("routes") : null;
                if (routesObj instanceof List) {
                    List<?> routesList = (List<?>) routesObj;
                    for (Object routeItem : routesList) {
                        if (routeItem instanceof Map) {
                            Map<?, ?> routeMap = (Map<?, ?>) routeItem;
                            String val = String.valueOf(routeMap.get("value"));
                            String target = String.valueOf(routeMap.get("target"));
                            if (val != null && val.equalsIgnoreCase(routingValueStr)) {
                                targetNodeId = target;
                                break;
                            }
                        }
                    }
                }
                
                if (targetNodeId == null && suspendedNode.getData() != null) {
                    targetNodeId = extractString(suspendedNode.getData(), "defaultRoute");
                }
                
                if (targetNodeId != null && !targetNodeId.isBlank() && nodeMap.containsKey(targetNodeId)) {
                    currentNode = nodeMap.get(targetNodeId);
                    
                    // Add suspendedNode to activeNodes if missing
                    final String startId = startNodeId;
                    boolean hasStart = activeNodes.stream().anyMatch(n -> startId.equals(n.get("id")));
                    if (!hasStart) {
                        activeNodes.add(convertNodeToMap(suspendedNode));
                    }
                    
                    // Find edge connecting suspendedNode to targetNodeId if exists, to activeEdges
                    String finalTarget = targetNodeId;
                    WorkflowEdgeDto edgeObj = graph.getEdges().stream()
                            .filter(e -> startId.equals(e.getSource()) && finalTarget.equals(e.getTarget()))
                            .findFirst()
                            .orElse(null);
                    if (edgeObj != null) {
                        boolean hasEdge = activeEdges.stream().anyMatch(e -> edgeObj.getId().equals(e.get("id")));
                        if (!hasEdge) {
                            activeEdges.add(convertEdgeToMap(edgeObj));
                        }
                    }
                    
                    if (instance != null) {
                        instance.setRuntimeGraph(runtimeGraph);
                    }
                    
                    StepRecordDto resumeStep = new StepRecordDto();
                    resumeStep.setStepIndex(stepIdx++);
                    resumeStep.setNodeId(startNodeId);
                    resumeStep.setNodeType(suspendedNode.getType());
                    resumeStep.setLabel(suspendedNode.getLabel());
                    resumeStep.setStatus("RESUMED");
                    resumeStep.setNotes("Resumed workflow from wait event node: " + suspendedNode.getLabel() + " (Routed value: " + routingValueStr + " -> " + targetNodeId + ")");
                    resumeStep.setEnteredAt(LocalDateTime.now());
                    resumeStep.setExitedAt(LocalDateTime.now());
                    trace.add(resumeStep);
                }
            }

            if (currentNode == null) {
                List<WorkflowEdgeDto> outEdges = edgesBySource.getOrDefault(startNodeId, List.of());
                if (!outEdges.isEmpty()) {
                    WorkflowEdgeDto chosen = null;
                    if (outEdges.size() == 1) {
                        chosen = outEdges.get(0);
                    } else {
                        chosen = chooseEdge(outEdges, spelCtx, true);
                    }
                    if (chosen != null) {
                        currentNode = nodeMap.get(chosen.getTarget());

                        // Add START node to activeNodes if missing
                        final String startId = startNodeId;
                        boolean hasStart = activeNodes.stream().anyMatch(n -> startId.equals(n.get("id")));
                        if (!hasStart) {
                            activeNodes.add(convertNodeToMap(suspendedNode));
                        }

                        // Add taken edge to activeEdges
                        final String finalEdgeId = chosen.getId();
                        boolean hasEdge = activeEdges.stream().anyMatch(e -> finalEdgeId.equals(e.get("id")));
                        if (!hasEdge) {
                            activeEdges.add(convertEdgeToMap(chosen));
                        }

                        if (instance != null) {
                            instance.setRuntimeGraph(runtimeGraph);
                        }

                        // Add a trace step indicating we resumed execution
                        StepRecordDto resumeStep = new StepRecordDto();
                        resumeStep.setStepIndex(stepIdx++);
                        resumeStep.setNodeId(startNodeId);
                        resumeStep.setNodeType(suspendedNode.getType());
                        resumeStep.setLabel(suspendedNode.getLabel());
                        resumeStep.setStatus("RESUMED");
                        resumeStep.setNotes("Resumed workflow from node: " + suspendedNode.getLabel() + " via edge: " + chosen.getId());
                        resumeStep.setEnteredAt(LocalDateTime.now());
                        resumeStep.setExitedAt(LocalDateTime.now());
                        trace.add(resumeStep);
                    }
                }
            }
        } else {
            // Find START node
            currentNode = graph.getNodes().stream()
                    .filter(n -> "START".equalsIgnoreCase(n.getType()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Workflow graph has no START node"));

            // Initialize with START node
            final String startId = currentNode.getId();
            boolean hasStart = activeNodes.stream().anyMatch(n -> startId.equals(n.get("id")));
            if (!hasStart) {
                activeNodes.add(convertNodeToMap(currentNode));
                if (instance != null) {
                    instance.setRuntimeGraph(runtimeGraph);
                }
            }
        }

        Set<String> visited = new HashSet<>();

        while (currentNode != null && stepIdx < MAX_STEPS) {
            if (visited.contains(currentNode.getId())) {
                StepRecordDto loopRecord = buildStep(stepIdx++, currentNode, "SKIPPED",
                        null, null, null, "Cycle detected – node already visited; halting.");
                trace.add(loopRecord);
                break;
            }
            visited.add(currentNode.getId());

            // Add node to runtime graph if not present
            final String curId = currentNode.getId();
            boolean hasNode = activeNodes.stream().anyMatch(n -> curId.equals(n.get("id")));
            if (!hasNode) {
                activeNodes.add(convertNodeToMap(currentNode));
                if (instance != null) {
                    instance.setRuntimeGraph(runtimeGraph);
                }
            }

            String nodeType = Optional.ofNullable(currentNode.getType()).orElse("UNKNOWN").toUpperCase();
            log.info("Engine traversing node ID: {}, Label: {}, Type: {}", currentNode.getId(), currentNode.getLabel(), nodeType);
            StepRecordDto step = new StepRecordDto();
            step.setStepIndex(stepIdx++);
            step.setNodeId(currentNode.getId());
            step.setNodeType(nodeType);
            step.setLabel(Optional.ofNullable(currentNode.getLabel()).orElse(nodeType));
            step.setEnteredAt(LocalDateTime.now());

            String edgeTakenId = null;
            WorkflowNodeDto nextNode = null;

            switch (nodeType) {
                case "START" -> {
                    com.enterprise.atlas.workflow.entity.TaskInstance ti = recordTaskStart(instance, currentNode, context);
                    step.setStatus("ENTERED");
                    step.setNotes("Workflow execution started.");
                    List<WorkflowEdgeDto> outEdges = edgesBySource.getOrDefault(currentNode.getId(), List.of());
                    if (!outEdges.isEmpty()) {
                        WorkflowEdgeDto taken = outEdges.get(0);
                        edgeTakenId = taken.getId();
                        nextNode = nodeMap.get(taken.getTarget());
                    }
                    recordTaskCompletion(ti, Map.of(), "COMPLETED");
                }

                case "COMMAND" -> {
                    com.enterprise.atlas.workflow.entity.TaskInstance ti = recordTaskStart(instance, currentNode, context);
                    String commandType = extractString(currentNode.getData(), "commandType");
                    if (commandType == null) {
                        commandType = extractString(currentNode.getData(), "type");
                    }
                    Map<String, Object> commandOutput = Map.of();
                    if (commandType != null) {
                        try {
                            commandOutput = executeCommandNode(currentNode, instance, context, instanceId, contextId, version, spelCtx);
                            step.setStatus("COMPLETED");
                            step.setNotes("Executed command: " + commandType);
                        } catch (Exception e) {
                            step.setStatus("FAILED");
                            step.setNotes("Failed to execute command: " + e.getMessage());
                            recordTaskCompletion(ti, Map.of("error", e.getMessage()), "FAILED");
                            nextNode = null;
                            break;
                        }
                    } else {
                        step.setStatus("COMPLETED");
                        step.setNotes("COMMAND node has no commandType configured.");
                    }
                    recordTaskCompletion(ti, commandOutput, "COMPLETED");
                    List<WorkflowEdgeDto> outEdges = edgesBySource.getOrDefault(currentNode.getId(), List.of());
                    if (!outEdges.isEmpty()) {
                        edgeTakenId = outEdges.get(0).getId();
                        nextNode = nodeMap.get(outEdges.get(0).getTarget());
                    }
                }

                case "WAIT_EVENT" -> {
                    com.enterprise.atlas.workflow.entity.TaskInstance ti = recordTaskStart(instance, currentNode, context);
                    recordTaskCompletion(ti, Map.of(), "WAITING");

                    String eventType = extractString(currentNode.getData(), "eventType");
                    if (eventType == null || eventType.isBlank()) {
                        eventType = "GENERIC_EVENT";
                    }

                    step.setStatus("WAITING");
                    step.setNotes("Suspended execution. Waiting on event: " + eventType);
                    step.setExitedAt(LocalDateTime.now());
                    trace.add(step);

                    createEventSubscription(instance, eventType, currentNode.getId(), Map.of());
                    log.info("Suspended execution at WAIT_EVENT Node ID: {}, Label: {}. Registered subscription on eventType: {}", currentNode.getId(), currentNode.getLabel(), eventType);

                    return new TraversalResult(trace, true, currentNode.getId(), currentNode.getLabel(), null);
                }

                case "RULE" -> {
                    com.enterprise.atlas.workflow.entity.TaskInstance ti = recordTaskStart(instance, currentNode, context);
                    // RULE node: evaluate the SpEL expression stored in data.expression
                    String expression = extractString(currentNode.getData(), "expression");
                    String ruleId = extractString(currentNode.getData(), "ruleId");

                    if ((expression == null || expression.isBlank()) && (ruleId != null && !ruleId.isBlank())) {
                        // Resolve from registry
                        Optional<Rule> ruleOpt = ruleRepository.findByRuleKey(ruleId);
                        if (ruleOpt.isPresent() && ruleOpt.get().isActive()) {
                            expression = ruleOpt.get().getExpression();
                            step.setNotes("Resolved registry rule [" + ruleId + "]: " + ruleOpt.get().getName());
                        } else {
                            step.setStatus("FAILED");
                            if (ruleOpt.isEmpty()) {
                                      step.setNotes("Rule reference '" + ruleId + "' not found in registry.");
                            } else {
                                      step.setNotes("Rule reference '" + ruleId + "' is inactive in registry.");
                            }
                            step.setExitedAt(LocalDateTime.now());
                            trace.add(step);
                            recordTaskCompletion(ti, Map.of(), "FAILED");
                            currentNode = null; // Halt traversal
                            break;
                        }
                    }

                    step.setExpression(expression);
                    if (expression != null && !expression.isBlank()) {
                        Object result = evaluateSpel(expression, spelCtx);
                        log.info("RULE Node Evaluation - NodeID: {}, Label: {}, Expression: {}, Evaluated Result: {}", currentNode.getId(), currentNode.getLabel(), expression, result);
                        step.setExpressionResult(result);
                        boolean passed = Boolean.TRUE.equals(result);
                        step.setStatus("EVALUATED");
                        String explanation = explainEvaluation(expression, context, result);
                        step.setNotes(explanation);

                        // Route: first edge whose condition matches the boolean result, or fallback edge
                        List<WorkflowEdgeDto> outEdges = edgesBySource.getOrDefault(currentNode.getId(), List.of());
                        WorkflowEdgeDto chosen = chooseEdge(outEdges, spelCtx, passed);
                        if (chosen != null) {
                            edgeTakenId = chosen.getId();
                            nextNode = nodeMap.get(chosen.getTarget());
                        }
                    } else {
                        step.setStatus("EVALUATED");
                        step.setNotes("RULE node has no expression or rule reference – taking first outgoing edge.");
                        List<WorkflowEdgeDto> outEdges = edgesBySource.getOrDefault(currentNode.getId(), List.of());
                        if (!outEdges.isEmpty()) {
                            edgeTakenId = outEdges.get(0).getId();
                            nextNode = nodeMap.get(outEdges.get(0).getTarget());
                        }
                    }
                    recordTaskCompletion(ti, Map.of("expressionResult", step.getExpressionResult() != null ? step.getExpressionResult() : ""), "COMPLETED");
                }

                case "DECISION" -> {
                    com.enterprise.atlas.workflow.entity.TaskInstance ti = recordTaskStart(instance, currentNode, context);
                    // DECISION node: evaluate custom expression or read a context field, then match it to an edge condition
                    String expression = extractString(currentNode.getData(), "expression");
                    Object fieldValue;
                    String expressionMeta;
                    
                    if (expression != null && !expression.isBlank()) {
                        fieldValue = evaluateSpel(expression, spelCtx);
                        expressionMeta = expression;
                        String explanation = explainEvaluation(expression, context, fieldValue);
                        step.setNotes(explanation);
                    } else {
                        String fieldKey = extractString(currentNode.getData(), "decisionField");
                        if (fieldKey != null && fieldKey.trim().isEmpty()) {
                            fieldKey = null;
                        }
                        fieldValue = fieldKey != null ? context.get(fieldKey) : null;
                        expressionMeta = fieldKey != null ? "context['" + fieldKey + "']" : null;
                        step.setNotes("Decision on context field: " + fieldKey + " = '" + fieldValue + "'");
                    }
                    
                    step.setExpression(expressionMeta);
                    step.setExpressionResult(fieldValue);
                    log.info("DECISION Node Evaluation - NodeID: {}, Label: {}, Field/Expr: {}, Resolved Value: {}", currentNode.getId(), currentNode.getLabel(), expressionMeta, fieldValue);
                    step.setStatus("ROUTED");

                    List<WorkflowEdgeDto> outEdges = edgesBySource.getOrDefault(currentNode.getId(), List.of());
                    WorkflowEdgeDto chosen = matchDecisionEdge(outEdges, fieldValue, spelCtx);
                    if (chosen != null) {
                        edgeTakenId = chosen.getId();
                        nextNode = nodeMap.get(chosen.getTarget());
                    } else {
                        step.setNotes("DECISION: no matching edge for value '" + fieldValue + "' – halting.");
                    }
                    recordTaskCompletion(ti, Map.of("fieldValue", fieldValue != null ? fieldValue : ""), "COMPLETED");
                }

                case "BUCKET" -> {
                    com.enterprise.atlas.workflow.entity.TaskInstance ti = recordTaskStart(instance, currentNode, context);
                    recordTaskCompletion(ti, Map.of(), "WAITING");

                    step.setStatus("WAITING");
                    step.setNotes("Suspended execution. Waiting on business outcome bucket: " + step.getLabel());
                    step.setExitedAt(LocalDateTime.now());
                    trace.add(step);

                    String outcomeBucketId = null;
                    if (currentNode.getData() != null) {
                        outcomeBucketId = (String) currentNode.getData().get("bucketId");
                    }
                    if (outcomeBucketId == null) {
                        outcomeBucketId = currentNode.getId();
                    }

                    createBucketRevertStatusAndFormPending(instanceId, contextId, outcomeBucketId, currentNode, version);
                    createEventSubscription(instance, outcomeBucketId, currentNode.getId(), Map.of());
                    log.info("Suspended execution at BUCKET Node ID: {}, Label: {}. Registered subscription on bucketId: {}", currentNode.getId(), currentNode.getLabel(), outcomeBucketId);

                    return new TraversalResult(trace, true, currentNode.getId(), currentNode.getLabel(), outcomeBucketId);
                }

                case "SUB_WORKFLOW" -> {
                    com.enterprise.atlas.workflow.entity.TaskInstance ti = recordTaskStart(instance, currentNode, context);
                    String childWorkflowKey = extractString(currentNode.getData(), "childWorkflowKey");
                    if (childWorkflowKey == null || childWorkflowKey.isBlank()) {
                        step.setStatus("FAILED");
                        step.setNotes("SUB_WORKFLOW node has no childWorkflowKey configured.");
                        step.setExitedAt(LocalDateTime.now());
                        trace.add(step);
                        recordTaskCompletion(ti, Map.of(), "FAILED");
                        currentNode = null;
                        break;
                    }

                    // Map input context
                    Map<String, Object> childInput = new HashMap<>();
                    if (instance != null && instance.getBusinessKey() != null) {
                        childInput.put("businessKey", instance.getBusinessKey());
                    }
                    if (context.get("businessKey") != null) {
                        childInput.put("businessKey", context.get("businessKey"));
                    }
                    if (context.get("cafId") != null) {
                        childInput.put("cafId", context.get("cafId"));
                    }

                    Object inputMappingObj = currentNode.getData().get("inputMapping");
                    if (inputMappingObj instanceof Map) {
                        Map<?, ?> inputMap = (Map<?, ?>) inputMappingObj;
                        for (Map.Entry<?, ?> entry : inputMap.entrySet()) {
                            String sourceVar = String.valueOf(entry.getKey());
                            String targetVar = String.valueOf(entry.getValue());
                            Object value = null;
                            if (sourceVar.contains(".") || sourceVar.contains("[") || sourceVar.contains("'")) {
                                try {
                                    value = evaluateSpel(sourceVar, spelCtx);
                                } catch (Exception e) {
                                    value = context.get(sourceVar);
                                }
                            } else {
                                value = context.get(sourceVar);
                            }
                            if (value != null) {
                                childInput.put(targetVar, value);
                            }
                        }
                    }

                    try {
                        ExecutionRequestDto childRequest = new ExecutionRequestDto();
                        childRequest.setBusinessKey(instance != null ? instance.getBusinessKey() : null);
                        childRequest.setContext(childInput);
                        childRequest.setContextId(UUID.randomUUID().toString());

                        ExecutionLogDto childExecution = executionService.execute(childWorkflowKey, childRequest);

                        if ("COMPLETED".equalsIgnoreCase(childExecution.getStatus())) {
                            step.setStatus("COMPLETED");
                            step.setNotes("Sub-workflow '" + childWorkflowKey + "' completed synchronously.");

                            com.enterprise.atlas.workflow.entity.WorkflowInstance childInst = instanceRepository.findById(childExecution.getInstanceId()).orElse(null);
                            Map<String, Object> childOutputs = childInst != null ? childInst.getSerializedContext() : Map.of();

                            Object outputMappingObj = currentNode.getData().get("outputMapping");
                            if (outputMappingObj instanceof Map && childOutputs != null) {
                                Map<?, ?> outputMap = (Map<?, ?>) outputMappingObj;
                                for (Map.Entry<?, ?> entry : outputMap.entrySet()) {
                                    String childVar = String.valueOf(entry.getKey());
                                    String parentVar = String.valueOf(entry.getValue());
                                    Object val = childOutputs.get(childVar);
                                    if (val != null) {
                                        context.put(parentVar, val);
                                    }
                                }
                            }

                            recordTaskCompletion(ti, childOutputs != null ? childOutputs : Map.of(), "COMPLETED");

                            List<WorkflowEdgeDto> outEdges = edgesBySource.getOrDefault(currentNode.getId(), List.of());
                            if (!outEdges.isEmpty()) {
                                edgeTakenId = outEdges.get(0).getId();
                                nextNode = nodeMap.get(outEdges.get(0).getTarget());
                            }
                        } else {
                            step.setStatus("WAITING");
                            step.setNotes("Sub-workflow '" + childWorkflowKey + "' suspended. Waiting for child instance completion (ID: " + childExecution.getInstanceId() + ").");
                            step.setExitedAt(LocalDateTime.now());
                            trace.add(step);

                            recordTaskCompletion(ti, Map.of("childInstanceId", childExecution.getInstanceId()), "WAITING");
                            createEventSubscription(instance, "CHILD_WORKFLOW_COMPLETED", currentNode.getId(), Map.of("childInstanceId", childExecution.getInstanceId()));

                            return new TraversalResult(trace, true, currentNode.getId(), currentNode.getLabel(), null);
                        }
                    } catch (Exception e) {
                        log.error("Failed to execute child workflow '{}': {}", childWorkflowKey, e.getMessage(), e);
                        step.setStatus("FAILED");
                        step.setNotes("Failed to execute child workflow: " + e.getMessage());
                        step.setExitedAt(LocalDateTime.now());
                        trace.add(step);
                        recordTaskCompletion(ti, Map.of("error", e.getMessage()), "FAILED");
                        currentNode = null;
                        break;
                    }
                }

                case "TIMER" -> {
                    com.enterprise.atlas.workflow.entity.TaskInstance ti = recordTaskStart(instance, currentNode, context);
                    String delayStr = extractString(currentNode.getData(), "delayMs");
                    step.setStatus("COMPLETED");
                    step.setNotes("Timer node recorded (delay: " + (delayStr != null ? delayStr + "ms" : "unspecified") + "). Execution continues.");
                    List<WorkflowEdgeDto> outEdges = edgesBySource.getOrDefault(currentNode.getId(), List.of());
                    if (!outEdges.isEmpty()) {
                        edgeTakenId = outEdges.get(0).getId();
                        nextNode = nodeMap.get(outEdges.get(0).getTarget());
                    }
                    recordTaskCompletion(ti, Map.of(), "COMPLETED");
                }

                case "PARALLEL" -> {
                    com.enterprise.atlas.workflow.entity.TaskInstance ti = recordTaskStart(instance, currentNode, context);
                    step.setStatus("ENTERED");
                    step.setNotes("Parallel split – fan-out recorded (logical; single-threaded traversal).");
                    List<WorkflowEdgeDto> outEdges = edgesBySource.getOrDefault(currentNode.getId(), List.of());
                    if (!outEdges.isEmpty()) {
                        edgeTakenId = outEdges.get(0).getId();
                        nextNode = nodeMap.get(outEdges.get(0).getTarget());
                    }
                    recordTaskCompletion(ti, Map.of(), "COMPLETED");
                }

                case "JOIN" -> {
                    com.enterprise.atlas.workflow.entity.TaskInstance ti = recordTaskStart(instance, currentNode, context);
                    step.setStatus("COMPLETED");
                    step.setNotes("Join convergence recorded.");
                    List<WorkflowEdgeDto> outEdges = edgesBySource.getOrDefault(currentNode.getId(), List.of());
                    if (!outEdges.isEmpty()) {
                        edgeTakenId = outEdges.get(0).getId();
                        nextNode = nodeMap.get(outEdges.get(0).getTarget());
                    }
                    recordTaskCompletion(ti, Map.of(), "COMPLETED");
                }

                case "END" -> {
                    com.enterprise.atlas.workflow.entity.TaskInstance ti = recordTaskStart(instance, currentNode, context);
                    step.setStatus("COMPLETED");
                    step.setNotes("Workflow execution reached END node.");
                    nextNode = null; // terminal
                    recordTaskCompletion(ti, Map.of(), "COMPLETED");
                    log.info("Reached END terminal node ID: {}, Label: {}", currentNode.getId(), currentNode.getLabel());
                }

                default -> {
                    com.enterprise.atlas.workflow.entity.TaskInstance ti = recordTaskStart(instance, currentNode, context);
                    step.setStatus("SKIPPED");
                    step.setNotes("Unknown node type '" + nodeType + "' – skipping.");
                    List<WorkflowEdgeDto> outEdges = edgesBySource.getOrDefault(currentNode.getId(), List.of());
                    if (!outEdges.isEmpty()) {
                        edgeTakenId = outEdges.get(0).getId();
                        nextNode = nodeMap.get(outEdges.get(0).getTarget());
                    }
                    recordTaskCompletion(ti, Map.of(), "COMPLETED");
                }
            }

            if (edgeTakenId != null) {
                final String finalEdgeId = edgeTakenId;
                log.info("NodeID: {} matched next Node ID: {} via edge ID: {}", currentNode.getId(), nextNode != null ? nextNode.getId() : "null", edgeTakenId);
                boolean hasEdge = activeEdges.stream().anyMatch(e -> finalEdgeId.equals(e.get("id")));
                if (!hasEdge) {
                    WorkflowEdgeDto edgeObj = graph.getEdges().stream()
                            .filter(e -> finalEdgeId.equals(e.getId()))
                            .findFirst()
                            .orElse(null);
                    if (edgeObj != null) {
                        activeEdges.add(convertEdgeToMap(edgeObj));
                    }
                }
            }

            if (instance != null) {
                instance.setRuntimeGraph(runtimeGraph);
            }

            step.setEdgeTaken(edgeTakenId);
            step.setExitedAt(LocalDateTime.now());
            step.setDurationMs(step.getEnteredAt() != null && step.getExitedAt() != null
                    ? java.time.Duration.between(step.getEnteredAt(), step.getExitedAt()).toMillis()
                    : 0L);
            trace.add(step);

            currentNode = nextNode;
        }

        if (stepIdx >= MAX_STEPS) {
            log.warn("Traversal halted: exceeded max steps ({}) for workflow '{}'", MAX_STEPS, version.getWorkflowDefinition().getKey());
        }

        return new TraversalResult(trace, false, null, null, null);
    }

    // ---- Helpers ----

    private WorkflowEdgeDto chooseEdge(List<WorkflowEdgeDto> edges, StandardEvaluationContext spelCtx, boolean ruleResult) {
        // Prefer an edge whose condition matches the rule result (true/false label)
        for (WorkflowEdgeDto edge : edges) {
            String cond = extractString(edge.getData(), "condition");
            if (cond == null || cond.isBlank()) continue;
            
            String trimmedCond = cond.trim();
            if ("true".equalsIgnoreCase(trimmedCond)) {
                if (ruleResult) return edge;
                continue;
            }
            if ("false".equalsIgnoreCase(trimmedCond)) {
                if (!ruleResult) return edge;
                continue;
            }

            // Also try evaluating arbitrary SpEL conditions on the edge
            Object result = evaluateSpel(cond, spelCtx);
            if (Boolean.TRUE.equals(result)) return edge;
        }
        // Fallback: first edge with no condition
        for (WorkflowEdgeDto edge : edges) {
            String cond = extractString(edge.getData(), "condition");
            if (cond == null || cond.isBlank()) return edge;
        }
        return edges.isEmpty() ? null : edges.get(0);
    }

    private WorkflowEdgeDto matchDecisionEdge(List<WorkflowEdgeDto> edges, Object fieldValue, StandardEvaluationContext spelCtx) {
        String fieldStr = fieldValue != null ? String.valueOf(fieldValue) : "";
        for (WorkflowEdgeDto edge : edges) {
            String cond = extractString(edge.getData(), "condition");
            if (cond == null || cond.isBlank()) continue;
            // String equality match
            if (cond.equalsIgnoreCase(fieldStr)) return edge;
            // SpEL expression on the edge
            Object result = evaluateSpel(cond, spelCtx);
            if (Boolean.TRUE.equals(result)) return edge;
        }
        // fallback to first unconditional edge
        for (WorkflowEdgeDto edge : edges) {
            String cond = extractString(edge.getData(), "condition");
            if (cond == null || cond.isBlank()) return edge;
        }
        return null;
    }

    private Object evaluateSpel(String expression, StandardEvaluationContext spelCtx) {
        try {
            Expression parsed = SPEL.parseExpression(expression);
            return parsed.getValue(spelCtx);
        } catch (EvaluationException | org.springframework.expression.ParseException ex) {
            log.warn("SpEL evaluation failed for expression '{}': {}", expression, ex.getMessage());
            return null;
        }
    }

    private String extractString(Map<String, Object> data, String key) {
        if (data == null) return null;
        Object val = data.get(key);
        return val != null ? String.valueOf(val) : null;
    }

    private StepRecordDto buildStep(int idx, WorkflowNodeDto node, String status,
                                     String expression, Object result, String edgeTaken, String notes) {
        StepRecordDto s = new StepRecordDto();
        s.setStepIndex(idx);
        s.setNodeId(node.getId());
        s.setNodeType(node.getType());
        s.setLabel(node.getLabel());
        s.setStatus(status);
        s.setExpression(expression);
        s.setExpressionResult(result);
        s.setEdgeTaken(edgeTaken);
        s.setNotes(notes);
        s.setEnteredAt(LocalDateTime.now());
        s.setExitedAt(LocalDateTime.now());
        return s;
    }

    private void createBucketRevertStatusAndFormPending(String instanceId, String formId, String bucketId, WorkflowNodeDto node, WorkflowVersion version) {
        if (formId == null || formId.isBlank() || instanceId == null || instanceId.isBlank() || bucketId == null || bucketId.isBlank()) {
            log.warn("Skipping RevertStatus creation due to missing formId={}, instanceId={}, or bucketId={}", formId, instanceId, bucketId);
            return;
        }

        // 1. Update CustomerForm.formStatus to "<bucketId> Pending"
        Optional<com.enterprise.atlas.workflow.entity.CustomerForm> formOpt = customerFormRepository.findById(formId);
        if (formOpt.isPresent()) {
            com.enterprise.atlas.workflow.entity.CustomerForm form = formOpt.get();
            form.setFormStatus(bucketId + " Pending");
            customerFormRepository.save(form);
            log.info("Updated CustomerForm status to '{} Pending' for formId={}", bucketId, formId);
        } else {
            // Auto-create CustomerForm if it does not exist (robustness)
            com.enterprise.atlas.workflow.entity.CustomerForm form = new com.enterprise.atlas.workflow.entity.CustomerForm();
            form.setId(formId);
            form.setCustomerName("Customer_" + formId.substring(0, Math.min(formId.length(), 8)));
            form.setFormStatus(bucketId + " Pending");
            customerFormRepository.save(form);
            log.info("Created CustomerForm and updated status to '{} Pending' for formId={}", bucketId, formId);
        }

        // 2. Insert RevertStatus record in PENDING state
        // Find previous completed RevertStatus for this instance to form a chain
        String previousStepId = null;
        List<com.enterprise.atlas.workflow.entity.RevertStatus> existing = revertStatusRepository.findByWorkflowInstanceIdOrderByCreatedAtDesc(instanceId);
        for (com.enterprise.atlas.workflow.entity.RevertStatus rs : existing) {
            if ("COMPLETED".equalsIgnoreCase(rs.getStatus())) {
                previousStepId = rs.getId();
                break;
            }
        }

        // Extract dependency list from Bucket Node custom properties ('dependencyBuckets')
        String dependencyBucketIds = null;
        if (node.getData() != null && node.getData().containsKey("dependencyBuckets")) {
            Object depVal = node.getData().get("dependencyBuckets");
            if (depVal != null) {
                try {
                    dependencyBucketIds = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(depVal);
                } catch (Exception ex) {
                    dependencyBucketIds = depVal.toString();
                }
            }
        }

        // Check if there's already an active PENDING RevertStatus for this bucket, form, and instance
        Optional<com.enterprise.atlas.workflow.entity.RevertStatus> existingPending = revertStatusRepository.findByWorkflowInstanceIdAndBucketIdAndStatus(instanceId, bucketId, com.enterprise.atlas.workflow.entity.RevertStepStatus.PENDING);
        if (existingPending.isPresent()) {
            log.info("PENDING RevertStatus already exists for bucketId={}, instanceId={}", bucketId, instanceId);
            return;
        }

        com.enterprise.atlas.workflow.entity.RevertStatus revert = new com.enterprise.atlas.workflow.entity.RevertStatus();
        revert.setId(UUID.randomUUID().toString());
        revert.setWorkflowInstanceId(instanceId);
        revert.setFormId(formId);
        revert.setBucketId(bucketId);
        revert.setBucketName(node.getLabel() != null ? node.getLabel() : bucketId);
        revert.setStatus("PENDING");
        revert.setPreviousStepId(previousStepId);
        revert.setDependencyBucketIds(dependencyBucketIds);
        revertStatusRepository.save(revert);
        log.info("Created PENDING RevertStatus entry for bucketId={}, instanceId={}, previousStepId={}", bucketId, instanceId, previousStepId);
    }

    private void createBucketExecution(String instanceId, String bucketId, String workflowKey) {
        Optional<com.enterprise.atlas.workflow.entity.Bucket> bucketOpt = bucketRepository.findByBucketId(bucketId);
        com.enterprise.atlas.workflow.entity.BucketExecution bex = new com.enterprise.atlas.workflow.entity.BucketExecution();
        bex.setId(UUID.randomUUID().toString());
        bex.setInstanceId(instanceId);
        bex.setWorkflowKey(workflowKey);
        bex.setBucketId(bucketId);
        if (bucketOpt.isPresent()) {
            com.enterprise.atlas.workflow.entity.Bucket b = bucketOpt.get();
            bex.setBucketName(b.getName());
            bex.setPriority(b.getPriority());
            bex.setSlaHours(b.getSlaHours());
        } else {
            bex.setBucketName(bucketId);
        }
        bex.setStatus("PENDING");
        bucketExecutionRepository.save(bex);
        log.info("Created BucketExecution in PENDING status for instanceId={}, bucketId={}", instanceId, bucketId);
    }

    private String explainEvaluation(String expression, Map<String, Object> context, Object result) {
        if (expression == null || expression.isBlank()) {
            return "No expression evaluated.";
        }
        Set<String> variablesUsed = new HashSet<>();
        java.util.regex.Pattern p1 = java.util.regex.Pattern.compile("context\\[['\"]([^'\"]+)['\"]\\]");
        java.util.regex.Matcher m1 = p1.matcher(expression);
        while (m1.find()) {
            variablesUsed.add(m1.group(1));
        }
        java.util.regex.Pattern p2 = java.util.regex.Pattern.compile("context\\.([a-zA-Z0-9_]+)");
        java.util.regex.Matcher m2 = p2.matcher(expression);
        while (m2.find()) {
            variablesUsed.add(m2.group(1));
        }

        Map<String, Object> valuesUsed = new HashMap<>();
        for (String var : variablesUsed) {
            valuesUsed.put(var, context.get(var));
        }

        Map<String, Object> explanation = new HashMap<>();
        explanation.put("rule", expression);
        explanation.put("condition", expression);
        explanation.put("variablesUsed", variablesUsed);
        explanation.put("actualValues", valuesUsed);
        explanation.put("result", result);
        explanation.put("outcome", Boolean.TRUE.equals(result) ? "PASS" : "FAIL");
        explanation.put("reason", Boolean.TRUE.equals(result) 
            ? "Condition evaluated to true with actual values: " + valuesUsed
            : "Condition evaluated to false with actual values: " + valuesUsed);

        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(explanation);
        } catch (Exception e) {
            return "Rule: " + expression + " | Result: " + result + " | Values: " + valuesUsed;
        }
    }

    private Map<String, Object> executeCommandNode(
            WorkflowNodeDto node, 
            com.enterprise.atlas.workflow.entity.WorkflowInstance instance, 
            Map<String, Object> context, 
            String instanceId, 
            String contextId, 
            WorkflowVersion version, 
            StandardEvaluationContext spelCtx) {
        
        String tempType = extractString(node.getData(), "commandType");
        if (tempType == null) {
            tempType = extractString(node.getData(), "type");
        }
        if (tempType == null) {
            throw new IllegalArgumentException("COMMAND node '" + node.getId() + "' has no configured commandType or type.");
        }
        final String commandType = tempType;

        // 1. Resolve strategy
        WorkflowCommand command = commandRegistry.getCommand(commandType)
                .orElseThrow(() -> new IllegalArgumentException("Unknown command type: " + commandType));

        // 2. Build input map
        Map<String, Object> input = new HashMap<>();
        if (node.getData() != null) {
            input.putAll(node.getData());
        }

        // Add execution context metadata
        input.put("_instanceId", instanceId);
        input.put("_contextId", contextId);
        input.put("_workflowKey", version.getWorkflowDefinition().getKey());
        input.put("_nodeId", node.getId());
        input.put("_nodeLabel", node.getLabel() != null ? node.getLabel() : node.getId());
        input.put("_context", context);

        // Resolve inputMapping SpEL expressions
        Object inputMappingObj = node.getData() != null ? node.getData().get("inputMapping") : null;
        if (inputMappingObj instanceof Map) {
            Map<?, ?> inputMap = (Map<?, ?>) inputMappingObj;
            for (Map.Entry<?, ?> entry : inputMap.entrySet()) {
                String sourceExpr = String.valueOf(entry.getKey());
                String targetVar = String.valueOf(entry.getValue());
                
                Object resolvedValue = null;
                if (sourceExpr.contains(".") || sourceExpr.contains("[") || sourceExpr.contains("'") || sourceExpr.contains("context")) {
                    try {
                        resolvedValue = evaluateSpel(sourceExpr, spelCtx);
                    } catch (Exception e) {
                        resolvedValue = context.get(sourceExpr);
                    }
                } else {
                    resolvedValue = context.get(sourceExpr);
                }
                
                if (resolvedValue != null) {
                    input.put(targetVar, resolvedValue);
                    // Also put under sourceExpr so that child workflow strategy or other strategies can read it as sourceExpr
                    input.put(sourceExpr, resolvedValue);
                }
            }
        }

        // 3. Execute
        Map<String, Object> output = null;
        try {
            output = command.execute(input);
        } catch (Exception e) {
            log.error("Command execution failed for node '{}': {}", node.getId(), e.getMessage(), e);
            throw new RuntimeException("Command execution failed: " + e.getMessage(), e);
        }

        // 4. Map output back to global context
        Object outputMappingObj = node.getData() != null ? node.getData().get("outputMapping") : null;
        if (outputMappingObj instanceof Map && output != null) {
            Map<?, ?> outputMap = (Map<?, ?>) outputMappingObj;
            for (Map.Entry<?, ?> entry : outputMap.entrySet()) {
                String outputKey = String.valueOf(entry.getKey());
                String targetContextKey = String.valueOf(entry.getValue());
                
                Object val = output.get(outputKey);
                if (val != null) {
                    // Strip "context." prefix if present
                    String contextKey = targetContextKey.startsWith("context.") ? targetContextKey.substring(8) : targetContextKey;
                    context.put(contextKey, val);
                }
            }
        }

        return output != null ? output : Map.of();
    }
}
