package com.enterprise.atlas.workflow.controller;

import com.enterprise.atlas.common.dto.ExecutionLogDto;
import com.enterprise.atlas.common.dto.WorkflowInstanceDto;
import com.enterprise.atlas.common.dto.RevertStatusDto;
import com.enterprise.atlas.workflow.entity.WorkflowInstance;
import com.enterprise.atlas.workflow.entity.RevertStatus;
import com.enterprise.atlas.workflow.repository.WorkflowInstanceRepository;
import com.enterprise.atlas.workflow.repository.WorkflowVersionRepository;
import com.enterprise.atlas.workflow.repository.RevertStatusRepository;
import com.enterprise.atlas.workflow.service.ExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/instances")
@CrossOrigin(origins = "*", allowedHeaders = "*",
        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.DELETE, RequestMethod.OPTIONS})
@Tag(name = "Workflow Running Instances API", description = "Operations to monitor running stateful instances, audit variables state, and manually resume suspended state machines")
public class WorkflowInstanceController {

    @Autowired
    private WorkflowInstanceRepository repository;

    @Autowired
    private WorkflowVersionRepository versionRepository;

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private RevertStatusRepository revertStatusRepository;

    @GetMapping
    @Operation(summary = "Get all instances with pagination and filtering", description = "Retrieves a list of workflow stateful instances with page, size, workflowKey, status, and search filters")
    public ResponseEntity<Map<String, Object>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String workflowKey,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {

        org.springframework.data.domain.Pageable pageable = 
                org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));

        org.springframework.data.domain.Page<WorkflowInstance> instancePage = 
                repository.findAllWithFilters(workflowKey, status, search, pageable);

        List<WorkflowInstanceDto> dtos = instancePage.getContent().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("content", dtos);
        response.put("totalElements", instancePage.getTotalElements());
        response.put("totalPages", instancePage.getTotalPages());
        response.put("size", instancePage.getSize());
        response.put("number", instancePage.getNumber());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get instance by ID", description = "Retrieves workflow stateful instance details by UUID")
    public ResponseEntity<WorkflowInstanceDto> getById(@PathVariable String id) {
        return repository.findById(id)
                .map(entity -> ResponseEntity.ok(toDto(entity)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/workflow/{key}")
    @Operation(summary = "Get instances by workflow key", description = "Retrieves workflow stateful instances filtered by workflow lookup key pattern")
    public List<WorkflowInstanceDto> getByWorkflowKey(@PathVariable String key) {
        return repository.findByWorkflowKeyOrderByCreatedAtDesc(key).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @PostMapping("/{id}/resume")
    @Operation(summary = "Resume workflow instance", description = "Manually resumes a stateful workflow instance currently suspended in a WAITING status, injecting optional context parameters")
    public ResponseEntity<ExecutionLogDto> resumeInstance(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> additionalContext = body != null ? body : Map.of();
        ExecutionLogDto result = executionService.resume(id, additionalContext);
        HttpStatus status = "FAILED".equals(result.getStatus()) ? HttpStatus.UNPROCESSABLE_ENTITY : HttpStatus.OK;
        return new ResponseEntity<>(result, status);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete workflow instance", description = "Deletes a workflow running instance from the database by UUID")
    public ResponseEntity<Void> deleteInstance(@PathVariable String id) {
        if (repository.existsById(id)) {
            executionService.terminateInstance(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}/revert-status")
    @Operation(summary = "Get revert status history", description = "Retrieves the revert and bucket approval audit history for a running instance")
    public List<RevertStatusDto> getRevertStatusHistory(@PathVariable String id) {
        return revertStatusRepository.findByWorkflowInstanceIdOrderByCreatedAtDesc(id).stream()
                .map(this::toRevertDto)
                .collect(Collectors.toList());
    }

    private RevertStatusDto toRevertDto(RevertStatus entity) {
        RevertStatusDto dto = new RevertStatusDto();
        dto.setId(entity.getId());
        dto.setWorkflowInstanceId(entity.getWorkflowInstanceId());
        dto.setFormId(entity.getFormId());
        dto.setBucketId(entity.getBucketId());
        dto.setBucketName(entity.getBucketName());
        dto.setStatus(entity.getStatus());
        dto.setPreviousStepId(entity.getPreviousStepId());
        dto.setDependencyBucketIds(entity.getDependencyBucketIds());
        dto.setResolvedBy(entity.getResolvedBy());
        dto.setResolutionNotes(entity.getResolutionNotes());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setCompletedAt(entity.getCompletedAt());
        return dto;
    }

    private WorkflowInstanceDto toDto(WorkflowInstance entity) {
        WorkflowInstanceDto dto = new WorkflowInstanceDto();
        dto.setId(entity.getId());
        dto.setWorkflowKey(entity.getWorkflowKey());
        dto.setVersionId(entity.getVersionId());
        dto.setBusinessKey(entity.getBusinessKey());
        dto.setVersionNumber(entity.getVersionNumber());
        dto.setStatus(entity.getStatus());
        dto.setCurrentNodeId(entity.getCurrentNodeId());
        dto.setContext(entity.getSerializedContext() != null ? entity.getSerializedContext() : Map.of());
        dto.setRuntimeGraph(entity.getRuntimeGraph() != null ? entity.getRuntimeGraph() : Map.of());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        if (entity.getCurrentNodeId() != null) {
            versionRepository.findById(entity.getVersionId()).ifPresent(version -> {
                if (version.getDefinition() != null && version.getDefinition().getNodes() != null) {
                    version.getDefinition().getNodes().stream()
                            .filter(n -> n.getId().equals(entity.getCurrentNodeId()))
                            .findFirst()
                            .ifPresent(node -> dto.setCurrentNodeLabel(node.getLabel()));
                }
            });
        }
        return dto;
    }

    @Autowired
    private com.enterprise.atlas.workflow.repository.TaskInstanceRepository taskInstanceRepository;

    @Autowired
    private com.enterprise.atlas.workflow.repository.EventSubscriptionRepository eventSubscriptionRepository;

    @GetMapping("/{id}/tasks")
    @Operation(summary = "Get task instances", description = "Retrieves the historical TaskInstance logs for a stateful workflow instance")
    public List<Map<String, Object>> getTaskInstances(@PathVariable String id) {
        return taskInstanceRepository.findByWorkflowInstanceIdOrderByStartedAtAsc(id).stream()
                .map(ti -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("id", ti.getId());
                    map.put("taskType", ti.getTaskType());
                    map.put("label", ti.getLabel());
                    map.put("status", ti.getStatus());
                    map.put("inputData", ti.getInputData());
                    map.put("outputData", ti.getOutputData());
                    map.put("startedAt", ti.getStartedAt());
                    map.put("completedAt", ti.getCompletedAt());
                    return map;
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}/subscriptions")
    @Operation(summary = "Get event subscriptions", description = "Retrieves active/triggered EventSubscription records for a workflow instance")
    public List<Map<String, Object>> getEventSubscriptions(@PathVariable String id) {
        return eventSubscriptionRepository.findByWorkflowInstanceIdOrderByCreatedAtDesc(id).stream()
                .map(sub -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("id", sub.getId());
                    map.put("businessKey", sub.getBusinessKey());
                    map.put("eventType", sub.getEventType());
                    map.put("targetNodeId", sub.getTargetNodeId());
                    map.put("status", sub.getStatus());
                    map.put("filterAttributes", sub.getFilterAttributes());
                    map.put("createdAt", sub.getCreatedAt());
                    return map;
                })
                .collect(Collectors.toList());
    }
}
