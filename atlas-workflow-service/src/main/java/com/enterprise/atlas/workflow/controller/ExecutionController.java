package com.enterprise.atlas.workflow.controller;

import com.enterprise.atlas.common.dto.ExecutionLogDto;
import com.enterprise.atlas.common.dto.ExecutionRequestDto;
import com.enterprise.atlas.workflow.service.ExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*", allowedHeaders = "*",
        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.DELETE, RequestMethod.OPTIONS})
@Tag(name = "Workflow Executions Engine API", description = "Operations to execute workflows, retrieve execution trace logs, and manage audit traces")
public class ExecutionController {

    @Autowired
    private ExecutionService executionService;

    /**
     * POST /api/execute/{key}
     * Triggers execution of the active published version for the given workflow key.
     * Body: { "contextId": "optional-id", "context": { ...payload... } }
     */
    @PostMapping("/execute/{key}")
    @Operation(summary = "Execute workflow", description = "Triggers execution of the active published version for the given workflow key with context variables")
    public ResponseEntity<ExecutionLogDto> executeWorkflow(
            @PathVariable String key,
            @RequestBody(required = false) ExecutionRequestDto request) {
        if (request == null) request = new ExecutionRequestDto();
        ExecutionLogDto result = executionService.execute(key, request);
        HttpStatus status = "FAILED".equals(result.getStatus()) ? HttpStatus.UNPROCESSABLE_ENTITY : HttpStatus.CREATED;
        return new ResponseEntity<>(result, status);
    }

    /**
     * GET /api/executions?page=0&size=20
     * Returns a paginated list of all execution logs, newest first.
     */
    @GetMapping("/executions")
    @Operation(summary = "Get all execution logs", description = "Returns a paginated list of all execution logs, newest first")
    public ResponseEntity<List<ExecutionLogDto>> getAllExecutions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(executionService.getAllExecutions(page, size));
    }

    /**
     * GET /api/executions/{execId}
     * Returns the full execution log including trace.
     */
    @GetMapping("/executions/{execId}")
    @Operation(summary = "Get execution log details", description = "Returns the full execution log including trace steps for a specific execution identifier UUID")
    public ResponseEntity<ExecutionLogDto> getExecution(@PathVariable String execId) {
        return ResponseEntity.ok(executionService.getExecution(execId));
    }

    /**
     * GET /api/executions/workflow/{key}
     * Returns execution logs for a specific workflow key.
     */
    @GetMapping("/executions/workflow/{key}")
    @Operation(summary = "Get execution logs by key", description = "Returns all execution logs for a specific workflow key pattern")
    public ResponseEntity<List<ExecutionLogDto>> getExecutionsByKey(@PathVariable String key) {
        return ResponseEntity.ok(executionService.getExecutionsByKey(key));
    }

    /**
     * DELETE /api/executions/{execId}
     */
    @DeleteMapping("/executions/{execId}")
    @Operation(summary = "Delete execution log", description = "Deletes an execution log from the database by UUID")
    public ResponseEntity<Void> deleteExecution(@PathVariable String execId) {
        executionService.deleteExecution(execId);
        return ResponseEntity.noContent().build();
    }

}
