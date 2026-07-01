package com.enterprise.atlas.workflow.controller;

import com.enterprise.atlas.common.dto.ContextSchemaDto;
import com.enterprise.atlas.common.dto.ValidationResultDto;
import com.enterprise.atlas.workflow.service.ContextSchemaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/context-schemas")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
@Tag(name = "Workflow Context Schema API", description = "Operations to define, edit, and validate execution context data structures for workflows")
public class ContextSchemaController {

    @Autowired
    private ContextSchemaService schemaService;

    @GetMapping
    @Operation(summary = "Get all context schemas", description = "Retrieves all registered context schema definitions mapping variables to workflows")
    public ResponseEntity<List<ContextSchemaDto>> getAllSchemas() {
        return ResponseEntity.ok(schemaService.getAllSchemas());
    }

    @GetMapping("/{workflowKey}")
    @Operation(summary = "Get schema by workflow key", description = "Retrieves the context schema associated with a specific workflow key")
    public ResponseEntity<ContextSchemaDto> getSchemaByKey(@PathVariable String workflowKey) {
        return schemaService.getSchemaByKey(workflowKey)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create or update schema", description = "Creates a new context schema contract or updates an existing layout definition")
    public ResponseEntity<ContextSchemaDto> createOrUpdateSchema(@RequestBody ContextSchemaDto dto) {
        return ResponseEntity.ok(schemaService.createOrUpdateSchema(dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete schema", description = "Deletes a context schema by its database UUID")
    public ResponseEntity<Void> deleteSchema(@PathVariable String id) {
        schemaService.deleteSchema(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/validate/{workflowKey}")
    @Operation(summary = "Validate context variables", description = "Validates an inbound runtime context map payload against the schema registered for a workflow")
    public ResponseEntity<ValidationResultDto> validateContext(
            @PathVariable String workflowKey,
            @RequestBody Map<String, Object> context) {
        return ResponseEntity.ok(schemaService.validateContext(workflowKey, context));
    }

}
