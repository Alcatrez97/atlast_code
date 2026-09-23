package com.vi.atlas.workflow.controller;

import com.vi.atlas.common.dto.ContextSchemaDto;
import com.vi.atlas.common.dto.DomainMappingDto;
import com.vi.atlas.workflow.service.context.ContextSchemaService;
import com.vi.atlas.workflow.service.domain.DomainConventionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/domain-conventions")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.OPTIONS})
@Tag(name = "Domain Conventions API", description = "Provides workflow key naming convention mappings and target table discovery")
public class DomainConventionController {

    @Autowired
    private DomainConventionService conventionService;

    @Autowired
    private ContextSchemaService schemaService;

    @GetMapping
    @Operation(summary = "List all supported domain naming conventions", description = "Retrieves enterprise domain prefixes, target tables, PKs, and status columns")
    public ResponseEntity<List<DomainMappingDto>> getAllConventions() {
        return ResponseEntity.ok(conventionService.getAllConventions());
    }

    @GetMapping("/resolve")
    @Operation(summary = "Resolve target domain mapping for a workflow key", description = "Inspects workflow key prefix and context schema override to determine the target table and status column")
    public ResponseEntity<DomainMappingDto> resolveDomain(@RequestParam String workflowKey) {
        Optional<ContextSchemaDto> schemaOpt = schemaService.getSchemaByKey(workflowKey);
        DomainMappingDto resolved = conventionService.resolveDomain(workflowKey, schemaOpt.orElse(null));
        return ResponseEntity.ok(resolved);
    }
}
