package com.vi.atlas.workflow.service;

import com.vi.atlas.common.dto.ExecutionLogDto;
import com.vi.atlas.common.dto.ExecutionRequestDto;
import com.vi.atlas.common.dto.StepRecordDto;
import com.vi.atlas.common.dto.ValidationResultDto;
import com.vi.atlas.common.dto.WorkflowNodeDto;
import com.vi.atlas.workflow.entity.ExecutionLog;
import com.vi.atlas.workflow.entity.ExecutionLogDetail;
import com.vi.atlas.workflow.entity.WorkflowDefinition;
import com.vi.atlas.workflow.entity.WorkflowVersion;
import com.vi.atlas.workflow.entity.WorkflowInstance;
import com.vi.atlas.workflow.entity.WorkflowInstanceStatus;
import com.vi.atlas.workflow.entity.SubscriptionStatus;
import com.vi.atlas.workflow.repository.BucketRepository;
import com.vi.atlas.workflow.repository.ExecutionRepository;
import com.vi.atlas.workflow.repository.ExecutionLogDetailRepository;
import com.vi.atlas.workflow.repository.WorkflowDefinitionRepository;
import com.vi.atlas.workflow.repository.WorkflowVersionRepository;
import com.vi.atlas.workflow.repository.WorkflowInstanceRepository;
import java.util.Optional;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.vi.atlas.workflow.service.traversal.ResumeRouter;
import com.vi.atlas.workflow.service.traversal.GraphTraversalEngine;
import com.vi.atlas.workflow.service.context.ContextResolutionService;
import com.vi.atlas.workflow.service.context.ContextSchemaService;
import com.vi.atlas.workflow.service.context.LazyContextMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);

    private static final ObjectMapper MAPPER;

    static {
        MAPPER = new ObjectMapper();
        MAPPER.registerModule(new JavaTimeModule());
    }

    @Autowired private WorkflowDefinitionRepository definitionRepository;
    @Autowired private WorkflowVersionRepository versionRepository;
    @Autowired private ExecutionRepository executionRepository;
    @Autowired private ExecutionLogDetailRepository executionLogDetailRepository;
    @Autowired private GraphTraversalEngine traversalEngine;
    @Autowired private ContextSchemaService contextSchemaService;
    @Autowired private BucketRepository bucketRepository;
    @Autowired private BucketExecutionService bucketExecutionService;
    @Autowired private com.vi.atlas.workflow.repository.BucketExecutionRepository bucketExecutionRepository;
    @Autowired private ContextResolutionService contextResolutionService;
    @Autowired private WorkflowInstanceRepository instanceRepository;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private EventRoutingService eventRoutingService;

    @Autowired
    private com.vi.atlas.workflow.repository.EventSubscriptionRepository eventSubscriptionRepository;

    @Autowired
    private com.vi.atlas.workflow.repository.TaskInstanceRepository taskInstanceRepository;

    @Autowired
    private com.vi.atlas.workflow.repository.RevertStatusRepository revertStatusRepository;

    private void triggerChildCompletionIfApplicable(WorkflowInstance childInstance) {
        try {
            List<com.vi.atlas.workflow.entity.EventSubscription> subs = new java.util.ArrayList<>();
            subs.addAll(eventSubscriptionRepository.findByEventTypeAndStatus("CHILD_WORKFLOW_COMPLETED", SubscriptionStatus.ACTIVE));
            subs.addAll(eventSubscriptionRepository.findByEventTypeAndStatus("WORKFLOW_COMPLETED", SubscriptionStatus.ACTIVE));

            for (com.vi.atlas.workflow.entity.EventSubscription sub : subs) {
                Map<String, Object> filters = sub.getFilterAttributes();
                boolean matches = false;
                if (filters != null && filters.containsKey("childInstanceId")) {
                    matches = String.valueOf(childInstance.getId()).equals(String.valueOf(filters.get("childInstanceId")));
                } else {
                    boolean keyMatch = sub.getBusinessKey() != null && sub.getBusinessKey().equals(childInstance.getBusinessKey());
                    boolean typeMatch = true;
                    if (filters != null && filters.containsKey("workflow")) {
                        typeMatch = String.valueOf(filters.get("workflow")).equalsIgnoreCase(childInstance.getWorkflowKey());
                    }
                    matches = keyMatch && typeMatch;
                }

                if (matches) {
                    log.info("Child workflow instance {} completed. Triggering parent resumption for parent instance: {}", 
                        childInstance.getId(), sub.getWorkflowInstance().getId());
                    
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("childInstanceId", childInstance.getId());
                    payload.put("workflow", childInstance.getWorkflowKey());
                    
                    Object outcome = null;
                    if (childInstance.getSerializedContext() != null) {
                        payload.put("childOutputs", childInstance.getSerializedContext());
                        outcome = childInstance.getSerializedContext().get("outcome");
                        if (outcome == null) {
                            outcome = childInstance.getSerializedContext().get("status");
                        }
                    } else {
                        payload.put("childOutputs", Map.of());
                    }
                    if (outcome == null) {
                        outcome = childInstance.getStatus();
                    }
                    payload.put("outcome", outcome);
                    payload.put("status", childInstance.getStatus());
                    payload.put("event", "WORKFLOW_COMPLETED");

                    String childKey = childInstance.getWorkflowKey().replaceAll("[^a-zA-Z0-9_]", "_").toUpperCase();
                    payload.put(childKey + "_OUTCOME", outcome);
                    payload.put(childKey + "_STATUS", childInstance.getStatus());

                    eventRoutingService.routeEvent(sub.getEventType(), sub.getBusinessKey(), payload);
                }
            }
        } catch (Exception ex) {
            log.error("Failed to route completion event for child instance {}: {}", childInstance.getId(), ex.getMessage());
        }
    }

    /**
     * Execute the active published version for the given workflow key.
     */
    public ExecutionLogDto execute(String workflowKey, ExecutionRequestDto request) {
        // Resolve active published version
        WorkflowDefinition definition = definitionRepository.findByKey(workflowKey)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + workflowKey));

        if (definition.getActiveVersion() == null) {
            throw new IllegalStateException("Workflow '" + workflowKey + "' has no active published version. Publish a version first.");
        }

        WorkflowVersion version = versionRepository.findByWorkflowDefinitionIdAndVersion(
                        definition.getId(), definition.getActiveVersion())
                .orElseThrow(() -> new IllegalStateException("Published version not found for workflow: " + workflowKey));

        if (!"PUBLISHED".equalsIgnoreCase(version.getStatus())) {
            throw new IllegalStateException("Active version is not in PUBLISHED status.");
        }

        // Make context mutable to support default value injection & validation metadata
        Map<String, Object> context = request.getContext() != null
                ? new HashMap<>(request.getContext())
                : new HashMap<>();

        // Create parent WorkflowInstance
        String instanceId = UUID.randomUUID().toString();
        WorkflowInstance instance = new WorkflowInstance();
        instance.setId(instanceId);
        instance.setWorkflowKey(workflowKey);

        String businessKey = request.getBusinessKey();
        if (businessKey == null || businessKey.isBlank()) {
            businessKey = (String) context.get("businessKey");
        }
        if (businessKey == null || businessKey.isBlank()) {
            businessKey = (String) context.get("cafId");
        }
        if (businessKey == null || businessKey.isBlank()) {
            businessKey = "BK-" + instanceId.substring(0, 8);
        }
        instance.setBusinessKey(businessKey);

        log.info("Starting workflow execution. workflowKey={}, version={}, contextId={}, businessKey={}, context={}",
                workflowKey, version.getVersion(), request.getContextId(), businessKey, context);

        instance.setVersionId(version.getId());
        instance.setVersionNumber(version.getVersion());
        instance.setStatus("RUNNING");
        instance.setCreatedAt(LocalDateTime.now());
        instance.setUpdatedAt(LocalDateTime.now());
        instance = instanceRepository.saveAndFlush(instance);

        // Prepare execution log entity
        String execId = UUID.randomUUID().toString();
        String contextId = (request.getContextId() != null && !request.getContextId().isBlank())
                ? request.getContextId()
                : UUID.randomUUID().toString();

        // Validate context against the schema pre-flight
        ValidationResultDto valResult = contextSchemaService.validateContext(workflowKey, context);
        if (!valResult.isValid() || !valResult.getWarnings().isEmpty()) {
            context.put("_validation", valResult);
            log.warn("Workflow pre-flight schema validation warnings/failures for workflowKey={}: {}", workflowKey, valResult);
        } else {
            log.info("Workflow pre-flight schema validation passed for workflowKey={}", workflowKey);
        }

        // Wrap context in LazyContextMap for dynamic context resolution (Sprint 7)
        LazyContextMap lazyContext = new LazyContextMap(workflowKey, contextId, context, contextResolutionService);

        ExecutionLog executionLog = new ExecutionLog();
        executionLog.setId(execId);
        executionLog.setWorkflowKey(workflowKey);
        executionLog.setVersionId(version.getId());
        executionLog.setVersionNumber(version.getVersion());
        executionLog.setContextId(contextId);
        executionLog.setInstanceId(instanceId);
        executionLog.setStartedAt(LocalDateTime.now());
        executionLog.setStatus("RUNNING");

        long startMs = System.currentTimeMillis();
        String outcomeNodeId = null;
        String outcomeNodeLabel = null;
        String outcomeBucketId = null;
        List<StepRecordDto> trace = null;

        try {
            // Run the traversal against the lazy context map
            GraphTraversalEngine.TraversalResult result = traversalEngine.traverse(version, lazyContext, null, instanceId);
            trace = result.getTrace();

            // Enrich BUCKET steps with real-time bucket registry metadata
            for (StepRecordDto step : trace) {
                if ("BUCKET".equalsIgnoreCase(step.getNodeType())) {
                    WorkflowNodeDto node = version.getDefinition().getNodes().stream()
                            .filter(n -> n.getId().equals(step.getNodeId()))
                            .findFirst()
                            .orElse(null);
                    if (node != null && node.getData() != null) {
                        String bucketId = (String) node.getData().get("bucketId");
                        if (bucketId != null) {
                            bucketRepository.findByBucketId(bucketId).ifPresent(bucket -> {
                                step.setLabel(bucket.getName());
                                step.setNotes("Reached business outcome bucket: " + bucket.getName() +
                                        " (Priority: " + bucket.getPriority() +
                                        ", SLA: " + bucket.getSlaHours() + "h" +
                                        ", Owner: " + bucket.getOwnerGroup() + ")");
                            });
                        }
                    }
                }
            }

            outcomeNodeId = result.isSuspended() ? result.getSuspendedNodeId() : (trace.isEmpty() ? null : trace.get(trace.size() - 1).getNodeId());
            outcomeNodeLabel = result.isSuspended() ? result.getSuspendedNodeLabel() : (trace.isEmpty() ? null : trace.get(trace.size() - 1).getLabel());
            outcomeBucketId = result.getOutcomeBucketId();
            instance.setRuntimeGraph(result.getRuntimeGraph());

            executionLog.setOutcomeNodeId(outcomeNodeId);
            executionLog.setOutcomeNodeLabel(outcomeNodeLabel);
            executionLog.setStepCount(trace != null ? trace.size() : 0);
            if (outcomeBucketId != null) {
                executionLog.setOutcomeBucketId(outcomeBucketId);
            }

            if (result.isSuspended()) {
                instance.setStatus("WAITING");
                instance.setCurrentNodeId(result.getSuspendedNodeId());
                instance.setSerializedContext(new HashMap<>(lazyContext));
                
                executionLog.setStatus("WAITING");
            } else {
                boolean hasFailure = false;
                if (trace != null && !trace.isEmpty()) {
                    StepRecordDto lastStep = trace.get(trace.size() - 1);
                    if ("FAILED".equals(lastStep.getStatus())) {
                        hasFailure = true;
                    }
                }
                
                if (hasFailure) {
                    instance.setStatus("FAILED");
                    instance.setCurrentNodeId(outcomeNodeId);
                    instance.setSerializedContext(new HashMap<>(lazyContext));
                    
                    executionLog.setStatus("FAILED");
                } else {
                    instance.setStatus("COMPLETED");
                    instance.setCurrentNodeId(outcomeNodeId);
                    instance.setSerializedContext(new HashMap<>(lazyContext));
                    
                    executionLog.setStatus("COMPLETED");
                }
            }

        } catch (Exception ex) {
            log.error("Execution failed for workflow '{}': {}", workflowKey, ex.getMessage(), ex);
            instance.setStatus("FAILED");
            executionLog.setStatus("FAILED");
            executionLog.setErrorMessage(ex.getMessage());
        } finally {
            // Save resolved context & lazy events to database
            Map<String, Object> finalContext = new HashMap<>(lazyContext);
            finalContext.put("_resolutionEvents", lazyContext.getResolutionEvents());
            
            executionLog.setCompletedAt(LocalDateTime.now());
            executionLog.setTotalDurationMs(System.currentTimeMillis() - startMs);
            executionLog = executionRepository.saveAndFlush(executionLog);
            
            // Save Detail CLOBs vertically
            try {
                List<Map<String, Object>> traceMaps = trace != null ? trace.stream()
                        .map(s -> MAPPER.convertValue(s, new TypeReference<Map<String, Object>>() {}))
                        .collect(Collectors.toList()) : List.of();
                String inputContextJson = MAPPER.writeValueAsString(finalContext);
                String traceJson = MAPPER.writeValueAsString(traceMaps);
                
                ExecutionLogDetail detail = new ExecutionLogDetail();
                detail.setExecutionLog(executionLog);
                detail.setInputContextJson(inputContextJson);
                detail.setExecutionTraceJson(traceJson);
                executionLogDetailRepository.saveAndFlush(detail);
            } catch (Exception e) {
                log.error("Failed to save ExecutionLogDetail CLOBs for log {}", executionLog.getId(), e);
            }

            // Evict temporary transaction cache
            contextResolutionService.clearCacheForExecution(contextId);
        }

        instanceRepository.saveAndFlush(instance);

        if ("COMPLETED".equals(instance.getStatus())) {
            triggerChildCompletionIfApplicable(instance);
        }

        // Auto-create BucketExecution record(s) if execution suspended at a BUCKET
        if ("WAITING".equals(executionLog.getStatus())) {
            createBucketExecutionsForWaitingSteps(executionLog, trace, version);
        }

        return getExecution(executionLog.getId());
    }

    /**
     * Resumes a WAITING workflow instance starting from the recorded currentNodeId.
     */
    @org.springframework.retry.annotation.Retryable(
            retryFor = { org.springframework.orm.ObjectOptimisticLockingFailureException.class, jakarta.persistence.OptimisticLockException.class, IllegalStateException.class },
            maxAttempts = 5,
            backoff = @org.springframework.retry.annotation.Backoff(delay = 200, multiplier = 1.5)
    )
    public ExecutionLogDto resume(String instanceId, Map<String, Object> additionalContext) {
        log.info("Resuming workflow instance. instanceId={}, additionalContext={}", instanceId, additionalContext);
        WorkflowInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow instance not found: " + instanceId));
        
        if (!"WAITING".equalsIgnoreCase(instance.getStatus())) {
            throw new IllegalStateException("Instance " + instanceId + " is not in WAITING status (current: " + instance.getStatus() + ")");
        }

        // Load active version
        String versionId = instance.getVersionId();
        WorkflowVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalStateException("Version not found: " + versionId));

        // Auto-resolve any pending BucketExecution for the node we are resuming from
        String currentNodeId = instance.getCurrentNodeId();
        if (currentNodeId != null) {
            WorkflowNodeDto node = version.getDefinition().getNodes().stream()
                    .filter(n -> n.getId().equals(currentNodeId))
                    .findFirst()
                    .orElse(null);
            if (node != null && "BUCKET".equalsIgnoreCase(node.getType())) {
                String bucketId = null;
                if (node.getData() != null) {
                    bucketId = (String) node.getData().get("bucketId");
                }
                if (bucketId != null) {
                    try {
                        List<com.vi.atlas.workflow.entity.BucketExecution> pendingBex = 
                                bucketExecutionRepository.findPendingByInstanceIdAndBucketId(instanceId, bucketId);
                        for (com.vi.atlas.workflow.entity.BucketExecution bex : pendingBex) {
                            bex.setStatus("RESOLVED");
                            bex.setResolvedAt(LocalDateTime.now());
                            
                            String resolvedBy = null;
                            String notes = null;
                            if (additionalContext != null) {
                                resolvedBy = (String) additionalContext.get("resolvedBy");
                                if (resolvedBy == null) {
                                    resolvedBy = (String) additionalContext.get(bucketId + "_resolvedBy");
                                }
                                notes = (String) additionalContext.get("notes");
                                if (notes == null) {
                                    notes = (String) additionalContext.get(bucketId + "_resolutionNotes");
                                }
                            }
                            bex.setResolvedBy(resolvedBy != null ? resolvedBy : "ManualApprover");
                            bex.setResolutionNotes(notes != null ? notes : "Resolved during workflow resumption");
                            
                            bucketExecutionRepository.save(bex);
                            log.info("Auto-resolved BucketExecution {} during resume for bucket={}", bex.getId(), bucketId);
                        }
                    } catch (Exception ex) {
                        log.warn("Failed to auto-resolve BucketExecution during resume: {}", ex.getMessage());
                    }
                }
            }
        }

        String contextId = null;
        List<ExecutionLog> previousLogs = executionRepository.findByInstanceId(instanceId);
        if (previousLogs != null && !previousLogs.isEmpty()) {
            contextId = previousLogs.get(0).getContextId();
        }
        if (contextId == null || contextId.isBlank()) {
            contextId = UUID.randomUUID().toString();
        }

        // Load serialized context
        Map<String, Object> context = instance.getSerializedContext() != null
                ? new HashMap<>(instance.getSerializedContext())
                : new HashMap<>();

        if (additionalContext != null) {
            WorkflowNodeDto node = version.getDefinition().getNodes().stream()
                    .filter(n -> n.getId().equals(currentNodeId))
                    .findFirst()
                    .orElse(null);
            ResumeRouter.applyPayloadMapping(node != null ? node.getData() : null, additionalContext, context);
        }

        // Wrap in LazyContextMap
        LazyContextMap lazyContext = new LazyContextMap(instance.getWorkflowKey(), contextId, context, contextResolutionService);

        instance.setStatus("RUNNING");
        instance = instanceRepository.saveAndFlush(instance);

        // Update all previous WAITING execution logs for this instance to COMPLETED
        List<ExecutionLog> previousWaitingLogs = executionRepository.findByInstanceIdAndStatus(instanceId, WorkflowInstanceStatus.WAITING);
        for (ExecutionLog prevLog : previousWaitingLogs) {
            prevLog.setStatus("COMPLETED");
            prevLog.setCompletedAt(LocalDateTime.now());
            executionRepository.save(prevLog);
        }

        ExecutionLog executionLog = new ExecutionLog();
        executionLog.setId(UUID.randomUUID().toString());
        executionLog.setWorkflowKey(instance.getWorkflowKey());
        executionLog.setVersionId(version.getId());
        executionLog.setVersionNumber(version.getVersion());
        executionLog.setContextId(contextId);
        executionLog.setInstanceId(instanceId);
        executionLog.setStartedAt(LocalDateTime.now());
        executionLog.setStatus("RUNNING");

        long startMs = System.currentTimeMillis();
        String outcomeNodeId = null;
        String outcomeNodeLabel = null;
        String outcomeBucketId = null;
        List<StepRecordDto> trace = null;

        try {
            // Traverse starting from the suspended node
            GraphTraversalEngine.TraversalResult result = traversalEngine.traverse(version, lazyContext, instance.getCurrentNodeId(), instanceId);
            trace = result.getTrace();

            // Enrich BUCKET steps with real-time bucket registry metadata
            for (StepRecordDto step : trace) {
                if ("BUCKET".equalsIgnoreCase(step.getNodeType())) {
                    WorkflowNodeDto node = version.getDefinition().getNodes().stream()
                            .filter(n -> n.getId().equals(step.getNodeId()))
                            .findFirst()
                            .orElse(null);
                    if (node != null && node.getData() != null) {
                        String bucketId = (String) node.getData().get("bucketId");
                        if (bucketId != null) {
                            bucketRepository.findByBucketId(bucketId).ifPresent(bucket -> {
                                step.setLabel(bucket.getName());
                                step.setNotes("Reached business outcome bucket: " + bucket.getName() +
                                        " (Priority: " + bucket.getPriority() +
                                        ", SLA: " + bucket.getSlaHours() + "h" +
                                        ", Owner: " + bucket.getOwnerGroup() + ")");
                            });
                        }
                    }
                }
            }

            outcomeNodeId = result.isSuspended() ? result.getSuspendedNodeId() : (trace.isEmpty() ? null : trace.get(trace.size() - 1).getNodeId());
            outcomeNodeLabel = result.isSuspended() ? result.getSuspendedNodeLabel() : (trace.isEmpty() ? null : trace.get(trace.size() - 1).getLabel());
            outcomeBucketId = result.getOutcomeBucketId();
            instance.setRuntimeGraph(result.getRuntimeGraph());

            executionLog.setOutcomeNodeId(outcomeNodeId);
            executionLog.setOutcomeNodeLabel(outcomeNodeLabel);
            if (outcomeBucketId != null) {
                executionLog.setOutcomeBucketId(outcomeBucketId);
            }

            if (result.isSuspended()) {
                instance.setStatus("WAITING");
                instance.setCurrentNodeId(result.getSuspendedNodeId());
                instance.setSerializedContext(new HashMap<>(lazyContext));
                
                executionLog.setStatus("WAITING");
            } else {
                boolean hasFailure = false;
                if (!trace.isEmpty()) {
                    StepRecordDto lastStep = trace.get(trace.size() - 1);
                    if ("FAILED".equals(lastStep.getStatus())) {
                        hasFailure = true;
                    }
                }
                
                if (hasFailure) {
                    instance.setStatus("FAILED");
                    instance.setCurrentNodeId(outcomeNodeId);
                    instance.setSerializedContext(new HashMap<>(lazyContext));
                    
                    executionLog.setStatus("FAILED");
                } else {
                    instance.setStatus("COMPLETED");
                    instance.setCurrentNodeId(outcomeNodeId);
                    instance.setSerializedContext(new HashMap<>(lazyContext));
                    
                    executionLog.setStatus("COMPLETED");
                }
            }

        } catch (Exception ex) {
            log.error("Resume failed for instance '{}': {}", instanceId, ex.getMessage(), ex);
            instance.setStatus("FAILED");
            executionLog.setStatus("FAILED");
            executionLog.setErrorMessage(ex.getMessage());
        } finally {
            // Save resolved context & lazy events to database
            Map<String, Object> finalContext = new HashMap<>(lazyContext);
            finalContext.put("_resolutionEvents", lazyContext.getResolutionEvents());
            
            executionLog.setCompletedAt(LocalDateTime.now());
            executionLog.setTotalDurationMs(System.currentTimeMillis() - startMs);
            executionLog.setStepCount(trace != null ? trace.size() : 0);
            executionLog = executionRepository.saveAndFlush(executionLog);

            // Save Detail CLOBs vertically
            try {
                List<Map<String, Object>> traceMaps = trace != null ? trace.stream()
                        .map(s -> MAPPER.convertValue(s, new TypeReference<Map<String, Object>>() {}))
                        .collect(Collectors.toList()) : List.of();
                String inputContextJson = MAPPER.writeValueAsString(finalContext);
                String traceJson = MAPPER.writeValueAsString(traceMaps);
                
                ExecutionLogDetail detail = new ExecutionLogDetail();
                detail.setExecutionLog(executionLog);
                detail.setInputContextJson(inputContextJson);
                detail.setExecutionTraceJson(traceJson);
                executionLogDetailRepository.saveAndFlush(detail);
            } catch (Exception e) {
                log.error("Failed to save ExecutionLogDetail CLOBs for log {}", executionLog.getId(), e);
            }

            // Evict temporary transaction cache
            contextResolutionService.clearCacheForExecution(contextId);
        }

        instanceRepository.saveAndFlush(instance);

        if ("COMPLETED".equals(instance.getStatus())) {
            triggerChildCompletionIfApplicable(instance);
        }

        // Auto-create BucketExecution record(s) if execution suspended again at a BUCKET
        if ("WAITING".equals(executionLog.getStatus())) {
            createBucketExecutionsForWaitingSteps(executionLog, trace, version);
        }

        return getExecution(executionLog.getId());
    }

    @Transactional(readOnly = true)
    public List<ExecutionLogDto> getAllExecutions(int page, int size) {
        return getAllExecutions(page, size, null);
    }

    @Transactional(readOnly = true)
    public List<ExecutionLogDto> getAllExecutions(int page, int size, String circleId) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"));
        Page<ExecutionLog> pageResult;
        if (circleId != null && !circleId.isBlank() && !"ALL".equalsIgnoreCase(circleId.trim())) {
            List<Integer> circles = java.util.Arrays.stream(circleId.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty() && !"ALL".equalsIgnoreCase(s))
                    .map(s -> {
                        try {
                            return Integer.parseInt(s);
                        } catch (NumberFormatException e) {
                            return null;
                        }
                    })
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
            if (!circles.isEmpty()) {
                pageResult = executionRepository.findByCircleIdInOrderByStartedAtDesc(circles, pageable);
            } else {
                pageResult = executionRepository.findAllByOrderByStartedAtDesc(pageable);
            }
        } else {
            pageResult = executionRepository.findAllByOrderByStartedAtDesc(pageable);
        }
        return pageResult.getContent().stream().map(this::toLightDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ExecutionLogDto> getExecutionsByKey(String workflowKey) {
        return executionRepository.findByWorkflowKeyOrderByStartedAtDesc(workflowKey)
                .stream().map(this::toLightDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ExecutionLogDto getExecution(String execId) {
        ExecutionLog el = executionRepository.findById(execId)
                .orElseThrow(() -> new IllegalArgumentException("Execution log not found: " + execId));
        ExecutionLogDto dto = toLightDto(el);

        if (el.getInstanceId() != null) {
            List<ExecutionLog> allLogs = executionRepository.findByInstanceId(el.getInstanceId());
            List<ExecutionLog> prefixLogs = new java.util.ArrayList<>();
            for (ExecutionLog logEntry : allLogs) {
                prefixLogs.add(logEntry);
                if (logEntry.getId().equals(execId)) {
                    break;
                }
            }

            List<StepRecordDto> stitchedTrace = new java.util.ArrayList<>();
            int stepIndex = 0;
            for (ExecutionLog logEntry : prefixLogs) {
                Optional<ExecutionLogDetail> detailOpt = executionLogDetailRepository.findById(logEntry.getId());
                if (detailOpt.isPresent() && detailOpt.get().getExecutionTraceJson() != null) {
                    try {
                        List<Map<String, Object>> traceMaps = MAPPER.readValue(
                                detailOpt.get().getExecutionTraceJson(),
                                new TypeReference<List<Map<String, Object>>>() {}
                        );
                        List<StepRecordDto> steps = traceMaps.stream()
                                .map(m -> MAPPER.convertValue(m, StepRecordDto.class))
                                .collect(Collectors.toList());
                        for (StepRecordDto step : steps) {
                            step.setStepIndex(stepIndex++);
                            stitchedTrace.add(step);
                        }
                    } catch (Exception e) {
                        log.error("Failed to parse execution trace JSON for log {}", logEntry.getId(), e);
                    }
                }
            }
            dto.setExecutionTrace(stitchedTrace);
        }

        // Fetch input context from detail table
        executionLogDetailRepository.findById(execId).ifPresent(detail -> {
            if (detail.getInputContextJson() != null) {
                try {
                    Map<String, Object> ctx = MAPPER.readValue(
                            detail.getInputContextJson(),
                            new TypeReference<Map<String, Object>>() {}
                    );
                    dto.setInputContext(ctx);
                } catch (Exception e) {
                    log.error("Failed to parse input context JSON for log {}", execId, e);
                }
            }
        });

        return dto;
    }

    public void deleteExecution(String execId) {
        if (!executionRepository.existsById(execId)) {
            throw new IllegalArgumentException("Execution log not found: " + execId);
        }
        if (executionLogDetailRepository.existsById(execId)) {
            executionLogDetailRepository.deleteById(execId);
        }
        executionRepository.deleteById(execId);
    }

    // ---- Mapping ----

    private ExecutionLogDto toLightDto(ExecutionLog entity) {
        ExecutionLogDto dto = new ExecutionLogDto();
        dto.setId(entity.getId());
        dto.setInstanceId(entity.getInstanceId());
        dto.setWorkflowKey(entity.getWorkflowKey());
        dto.setVersionId(entity.getVersionId());
        dto.setVersionNumber(entity.getVersionNumber());
        dto.setContextId(entity.getContextId());
        dto.setCircleId(entity.getCircleId());
        dto.setStatus(entity.getStatus());
        dto.setOutcomeNodeId(entity.getOutcomeNodeId());
        dto.setOutcomeNodeLabel(entity.getOutcomeNodeLabel());
        dto.setStepCount(entity.getStepCount());
        dto.setStartedAt(entity.getStartedAt());
        dto.setCompletedAt(entity.getCompletedAt());
        dto.setTotalDurationMs(entity.getTotalDurationMs());
        dto.setErrorMessage(entity.getErrorMessage());
        // inputContext and executionTrace left empty for lightweight listing
        return dto;
    }

    public void terminateInstance(String instanceId) {
        log.info("Terminating workflow instance. instanceId={}", instanceId);
        WorkflowInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow instance not found: " + instanceId));

        // 1. Transition instance status to TERMINATED
        instance.setStatus("TERMINATED");
        instance.setUpdatedAt(LocalDateTime.now());
        instanceRepository.saveAndFlush(instance);

        // 2. Cancel active event subscriptions
        List<com.vi.atlas.workflow.entity.EventSubscription> subscriptions =
                eventSubscriptionRepository.findByWorkflowInstanceIdOrderByCreatedAtDesc(instanceId);
        for (com.vi.atlas.workflow.entity.EventSubscription sub : subscriptions) {
            if ("ACTIVE".equalsIgnoreCase(sub.getStatus())) {
                sub.setStatus("CANCELLED");
                eventSubscriptionRepository.save(sub);
                log.info("Cancelled EventSubscription: {}", sub.getId());
            }
        }

        // 3. Complete pending bucket task executions
        List<com.vi.atlas.workflow.entity.BucketExecution> bucketExecutions =
                bucketExecutionRepository.findByWorkflowInstanceId(instanceId);
        for (com.vi.atlas.workflow.entity.BucketExecution bex : bucketExecutions) {
            if ("PENDING".equalsIgnoreCase(bex.getStatus()) || "IN_REVIEW".equalsIgnoreCase(bex.getStatus())) {
                bex.setStatus("RESOLVED");
                bex.setResolvedAt(LocalDateTime.now());
                bex.setResolvedBy("SYSTEM");
                bex.setResolutionNotes("Cancelled due to workflow instance termination");
                bucketExecutionRepository.save(bex);
                log.info("Resolved BucketExecution: {}", bex.getId());
            }
        }

        // 4. Complete pending revert steps
        List<com.vi.atlas.workflow.entity.RevertStatus> revertSteps =
                revertStatusRepository.findByWorkflowInstanceIdOrderByCreatedAtDesc(instanceId);
        for (com.vi.atlas.workflow.entity.RevertStatus rs : revertSteps) {
            if ("PENDING".equalsIgnoreCase(rs.getStatus())) {
                rs.setStatus("COMPLETED");
                rs.setCompletedAt(LocalDateTime.now());
                rs.setResolvedBy("SYSTEM");
                rs.setResolutionNotes("Terminated due to workflow instance cancellation");
                revertStatusRepository.save(rs);
                log.info("Completed RevertStatus: {}", rs.getId());
            }
        }

        // 5. Fail active task instances
        List<com.vi.atlas.workflow.entity.TaskInstance> tasks =
                taskInstanceRepository.findByWorkflowInstanceIdOrderByStartedAtAsc(instanceId);
        for (com.vi.atlas.workflow.entity.TaskInstance ti : tasks) {
            if ("RUNNING".equalsIgnoreCase(ti.getStatus()) || "WAITING".equalsIgnoreCase(ti.getStatus())) {
                ti.setStatus("FAILED");
                ti.setCompletedAt(LocalDateTime.now());
                ti.setOutputData(Map.of("error", "Workflow instance terminated."));
                taskInstanceRepository.save(ti);
                log.info("Failed TaskInstance: {}", ti.getId());
            }
        }

        // 6. Update ExecutionLogs to FAILED status
        List<ExecutionLog> logs = executionRepository.findByInstanceIdAndStatus(instanceId, WorkflowInstanceStatus.RUNNING);
        List<ExecutionLog> waitingLogs = executionRepository.findByInstanceIdAndStatus(instanceId, WorkflowInstanceStatus.WAITING);
        List<ExecutionLog> allLogs = new java.util.ArrayList<>();
        allLogs.addAll(logs);
        allLogs.addAll(waitingLogs);
        for (ExecutionLog logEntry : allLogs) {
            logEntry.setStatus("FAILED");
            logEntry.setErrorMessage("Workflow execution terminated by user.");
            logEntry.setCompletedAt(LocalDateTime.now());
            executionRepository.save(logEntry);
        }
    }

    private void createBucketExecutionsForWaitingSteps(ExecutionLog executionLog, List<StepRecordDto> trace, WorkflowVersion version) {
        if (trace == null || version == null || version.getDefinition() == null) return;
        for (StepRecordDto step : trace) {
            if ("BUCKET".equalsIgnoreCase(step.getNodeType()) && "WAITING".equalsIgnoreCase(step.getStatus())) {
                WorkflowNodeDto node = version.getDefinition().getNodes().stream()
                        .filter(n -> n.getId().equals(step.getNodeId()))
                        .findFirst()
                        .orElse(null);
                if (node != null && node.getData() != null) {
                    String bucketId = (String) node.getData().get("bucketId");
                    if (bucketId == null) {
                        bucketId = node.getId();
                    }
                    try {
                        String currentBucketId = bucketId;
                        boolean exists = bucketExecutionRepository.findByWorkflowInstanceId(executionLog.getInstanceId()).stream()
                                .anyMatch(b -> currentBucketId.equals(b.getBucketId()) && 
                                        ("PENDING".equalsIgnoreCase(b.getStatus()) || "IN_REVIEW".equalsIgnoreCase(b.getStatus())));
                        if (!exists) {
                            bucketExecutionService.createFromExecution(executionLog, bucketId);
                            log.info("Created BucketExecution for suspended bucket {} in instance {}", bucketId, executionLog.getInstanceId());
                        }
                    } catch (Exception ex) {
                        log.warn("Failed to create BucketExecution for bucket {} in execution {}: {}", bucketId, executionLog.getId(), ex.getMessage());
                    }
                }
            }
        }
    }
}
