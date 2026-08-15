package com.vi.atlas.workflow.controller;

import com.vi.atlas.common.dto.CustomerFormDto;
import com.vi.atlas.workflow.entity.CustomerForm;
import com.vi.atlas.workflow.entity.RevertStatus;
import com.vi.atlas.workflow.repository.CustomerFormRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/forms")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.PUT, RequestMethod.OPTIONS})
@Tag(name = "Customer Form Management API", description = "Operations to simulate external business document approvals and status transitions")
public class CustomerFormController {

    @Autowired
    private CustomerFormRepository repository;

    @Autowired
    private com.vi.atlas.workflow.service.BucketResolutionService bucketResolutionService;

    @Autowired
    private com.vi.atlas.workflow.repository.ExecutionRepository executionRepository;

    @Autowired
    private com.vi.atlas.workflow.repository.RevertStatusRepository revertStatusRepository;

    @GetMapping
    @Operation(summary = "Get all customer forms with pagination and filtering", description = "Retrieves all simulated customer form documents with page, size, status, search, and circleId filters")
    public ResponseEntity<Map<String, Object>> getAllForms(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String circleId) {

        List<Integer> circleIds = null;
        if (circleId != null && !circleId.isBlank() && !"ALL".equalsIgnoreCase(circleId.trim())) {
            circleIds = java.util.Arrays.stream(circleId.split(","))
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
            if (circleIds.isEmpty()) {
                circleIds = null;
            }
        }

        org.springframework.data.domain.Pageable pageable = 
                org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "updatedAt"));

        org.springframework.data.domain.Page<CustomerForm> formPage = 
                repository.findAllWithFilters(status, search, circleIds, pageable);

        List<CustomerFormDto> dtos = formPage.getContent().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("content", dtos);
        response.put("totalElements", formPage.getTotalElements());
        response.put("totalPages", formPage.getTotalPages());
        response.put("size", formPage.getSize());
        response.put("number", formPage.getNumber());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get form by ID", description = "Retrieves customer form document details by ID")
    public ResponseEntity<CustomerFormDto> getFormById(@PathVariable String id) {
        return repository.findById(id)
                .map(entity -> ResponseEntity.ok(toDto(entity)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Update form status", description = "Simulates an external approval status change, triggering the engine resumption flow")
    public ResponseEntity<CustomerFormDto> updateFormStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        
        String newStatus = body.get("status");
        if (newStatus == null || newStatus.trim().isEmpty()) {
            throw new IllegalArgumentException("Status is required.");
        }

        return repository.findById(id)
                .map(entity -> {
                    // Update CustomerForm first
                    entity.setFormStatus(newStatus);
                    CustomerForm saved = repository.save(entity);

                    // Find active WAITING executions for this form ID
                    List<com.vi.atlas.workflow.entity.ExecutionLog> logs = executionRepository.findByContextIdAndStatus(id, com.vi.atlas.workflow.entity.WorkflowInstanceStatus.WAITING);
                    if (!logs.isEmpty()) {
                        for (com.vi.atlas.workflow.entity.ExecutionLog logEntry : logs) {
                            String instanceId = logEntry.getInstanceId();
                            
                            // Find active PENDING RevertStatus to determine bucketId
                            List<RevertStatus> revertRecords = revertStatusRepository.findByWorkflowInstanceIdOrderByCreatedAtDesc(instanceId);
                            RevertStatus activePending = null;
                            for (RevertStatus rs : revertRecords) {
                                if ("PENDING".equalsIgnoreCase(rs.getStatus())) {
                                    activePending = rs;
                                    break;
                                }
                            }

                            if (activePending != null) {
                                String bucketId = activePending.getBucketId();
                                String outcome = body.get("outcome");
                                if (outcome == null || outcome.isBlank()) {
                                    outcome = newStatus;
                                    if (newStatus.startsWith(bucketId)) {
                                        outcome = newStatus.substring(bucketId.length());
                                    }
                                }
                                bucketResolutionService.resolveBucket(
                                        instanceId,
                                        bucketId,
                                        outcome,
                                        "ManualApprover",
                                        "Form status manually updated to " + newStatus + " via simulator UI/API"
                                );
                            }
                        }
                    }
                    
                    return ResponseEntity.ok(toDto(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private CustomerFormDto toDto(CustomerForm entity) {
        CustomerFormDto dto = new CustomerFormDto();
        dto.setId(entity.getId());
        dto.setCustomerName(entity.getCustomerName());
        dto.setFormStatus(entity.getFormStatus());
        dto.setCircleId(entity.getCircleId());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
