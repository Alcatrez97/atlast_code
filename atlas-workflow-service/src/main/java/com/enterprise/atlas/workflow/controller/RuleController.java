package com.enterprise.atlas.workflow.controller;

import com.enterprise.atlas.common.dto.RuleDto;
import com.enterprise.atlas.common.dto.ValidationResultDto;
import com.enterprise.atlas.workflow.service.RuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rules")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
@Tag(name = "Business Rules Registry API", description = "Operations to define, register, update, and validate business rules with SpEL expressions")
public class RuleController {

    @Autowired
    private RuleService ruleService;

    @GetMapping
    @Operation(summary = "Get all business rules", description = "Retrieves a list of all registered business rules")
    public ResponseEntity<List<RuleDto>> getAllRules() {
        return ResponseEntity.ok(ruleService.getAllRules());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get rule by database ID", description = "Retrieves the rule details using its database UUID")
    public ResponseEntity<RuleDto> getRule(@PathVariable String id) {
        return ruleService.getRuleById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/key/{ruleKey}")
    @Operation(summary = "Get rule by business key", description = "Retrieves the rule details using its unique lookup key pattern (e.g. CHECK_HIGH_VALUE)")
    public ResponseEntity<RuleDto> getRuleByKey(@PathVariable String ruleKey) {
        return ruleService.getRuleByKey(ruleKey)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create rule", description = "Registers a new business rule with SpEL expression logic")
    public ResponseEntity<RuleDto> createRule(@RequestBody RuleDto dto) {
        return new ResponseEntity<>(ruleService.createRule(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update rule", description = "Updates details, SpEL expression, or active flag of an existing business rule")
    public ResponseEntity<RuleDto> updateRule(@PathVariable String id, @RequestBody RuleDto dto) {
        return ResponseEntity.ok(ruleService.updateRule(id, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete rule", description = "Deletes a business rule from the registry by its UUID")
    public ResponseEntity<Void> deleteRule(@PathVariable String id) {
        ruleService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate SpEL expression", description = "Validates the syntax correctness of a Spring SpEL expression")
    public ResponseEntity<ValidationResultDto> validateExpression(@RequestBody Map<String, String> payload) {
        String expression = payload.get("expression");
        return ResponseEntity.ok(ruleService.validateExpression(expression));
    }

    @PostMapping("/evaluate")
    @Operation(summary = "Evaluate SpEL expression", description = "Evaluates a SpEL expression against a mock context payload")
    public ResponseEntity<Map<String, Object>> evaluateExpression(@RequestBody Map<String, Object> payload) {
        String expression = (String) payload.get("expression");
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) payload.get("context");

        Map<String, Object> response = new java.util.HashMap<>();
        try {
            Object result = ruleService.evaluateExpression(expression, context);
            response.put("success", true);
            response.put("result", result != null ? result.toString() : "null");
            response.put("type", result != null ? result.getClass().getSimpleName() : "Void/Null");
            response.put("value", result);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

}
