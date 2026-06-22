package com.enterprise.atlas.workflow.controller;

import com.enterprise.atlas.common.dto.WorkflowDefinitionDto;
import com.enterprise.atlas.common.dto.WorkflowGraphDto;
import com.enterprise.atlas.common.dto.WorkflowVersionDto;
import com.enterprise.atlas.workflow.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workflows")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
@Tag(name = "Workflow Definition Management API", description = "Operations to design canvas graphs, manage draft versions, and transition publishing statuses")
public class WorkflowController {

    @Autowired
    private WorkflowService workflowService;

    @GetMapping
    @Operation(summary = "Get all workflows", description = "Retrieves all registered workflow definitions including version listings")
    public ResponseEntity<List<WorkflowDefinitionDto>> getAllWorkflows() {
        return ResponseEntity.ok(workflowService.getAllWorkflowDefinitions());
    }

    @PostMapping
    @Operation(summary = "Create workflow", description = "Registers a new top-level workflow definition template")
    public ResponseEntity<WorkflowDefinitionDto> createWorkflow(@RequestBody WorkflowDefinitionDto dto) {
        return new ResponseEntity<>(workflowService.createWorkflowDefinition(dto), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get workflow by ID", description = "Retrieves a specific workflow definition by its UUID")
    public ResponseEntity<WorkflowDefinitionDto> getWorkflow(@PathVariable String id) {
        return ResponseEntity.ok(workflowService.getWorkflowDefinition(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete workflow", description = "Deletes a workflow definition template and all associated version snapshots")
    public ResponseEntity<Void> deleteWorkflow(@PathVariable String id) {
        workflowService.deleteWorkflowDefinition(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/versions")
    @Operation(summary = "Create version draft", description = "Creates a new version draft for a workflow definition, optionally carrying a structured layout graph")
    public ResponseEntity<WorkflowVersionDto> createVersionDraft(
            @PathVariable String id,
            @RequestBody(required = false) WorkflowGraphDto definitionGraph) {
        return new ResponseEntity<>(workflowService.createDraftVersion(id, definitionGraph), HttpStatus.CREATED);
    }

    @GetMapping("/versions/{versionId}")
    @Operation(summary = "Get version details", description = "Retrieves workflow snapshot version details containing graph structure by version UUID")
    public ResponseEntity<WorkflowVersionDto> getVersion(@PathVariable String versionId) {
        return ResponseEntity.ok(workflowService.getWorkflowVersion(versionId));
    }

    @PutMapping("/versions/{versionId}")
    @Operation(summary = "Update version draft graph", description = "Saves/updates the canvas definition graph of a specific draft version")
    public ResponseEntity<WorkflowVersionDto> updateVersionDraft(
            @PathVariable String versionId,
            @RequestBody WorkflowGraphDto definitionGraph) {
        return ResponseEntity.ok(workflowService.updateDraftVersion(versionId, definitionGraph));
    }

    @PutMapping("/versions/{versionId}/status")
    @Operation(summary = "Transition version status", description = "Advances the version publishing workflow status (DRAFT, REVIEW, APPROVED, PUBLISHED)")
    public ResponseEntity<WorkflowVersionDto> transitionStatus(
            @PathVariable String versionId,
            @RequestBody Map<String, String> body) {
        String newStatus = body.get("status");
        if (newStatus == null || newStatus.trim().isEmpty()) {
            throw new IllegalArgumentException("Status parameter is required.");
        }
        return ResponseEntity.ok(workflowService.transitionVersionStatus(versionId, newStatus));
    }

    @GetMapping("/active/{key}")
    @Operation(summary = "Get active version by key", description = "Retrieves the currently published workflow version details for a workflow business key")
    public ResponseEntity<WorkflowVersionDto> getActiveVersion(@PathVariable String key) {
        return ResponseEntity.ok(workflowService.getPublishedVersionByKey(key));
    }

    @DeleteMapping("/versions/{versionId}")
    @Operation(summary = "Delete version draft", description = "Deletes a version snapshot draft by UUID")
    public ResponseEntity<Void> deleteVersion(@PathVariable String versionId) {
        workflowService.deleteWorkflowVersion(versionId);
        return ResponseEntity.noContent().build();
    }

}
