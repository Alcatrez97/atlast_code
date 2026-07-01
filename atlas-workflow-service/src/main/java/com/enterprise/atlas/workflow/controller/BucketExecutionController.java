package com.enterprise.atlas.workflow.controller;

import com.enterprise.atlas.common.dto.BucketExecutionDto;
import com.enterprise.atlas.common.dto.BucketWorkloadDto;
import com.enterprise.atlas.workflow.service.BucketExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bucket-executions")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
@Tag(name = "Bucket Task Executions API", description = "Operations to manage task lifecycles routed to outcome buckets (e.g. manual reviews, SLA analytics, resolutions)")
public class BucketExecutionController {

    @Autowired
    private BucketExecutionService bucketExecutionService;

    @GetMapping
    @Operation(summary = "Get all bucket tasks", description = "Retrieves tasks optionally filtered by status (PENDING, IN_REVIEW, RESOLVED)")
    public ResponseEntity<List<BucketExecutionDto>> getAll(
            @RequestParam(required = false) String status) {
        if (status != null && !status.isBlank()) {
            return ResponseEntity.ok(bucketExecutionService.getByStatus(status));
        }
        return ResponseEntity.ok(bucketExecutionService.getAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get task by database ID", description = "Retrieves details of a bucket execution task by database UUID")
    public ResponseEntity<BucketExecutionDto> getById(@PathVariable String id) {
        return bucketExecutionService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/bucket/{bucketId}")
    @Operation(summary = "Get tasks by bucket ID", description = "Retrieves all tasks routed to a specific outcome bucket key")
    public ResponseEntity<List<BucketExecutionDto>> getByBucket(@PathVariable String bucketId) {
        return ResponseEntity.ok(bucketExecutionService.getByBucket(bucketId));
    }

    @PostMapping("/{id}/review")
    @Operation(summary = "Start task review", description = "Transitions a bucket task status to IN_REVIEW")
    public ResponseEntity<BucketExecutionDto> startReview(@PathVariable String id) {
        return ResponseEntity.ok(bucketExecutionService.startReview(id));
    }

    @Autowired
    private com.enterprise.atlas.workflow.service.BucketResolutionService bucketResolutionService;

    @PostMapping("/{id}/resolve")
    @Operation(summary = "Resolve task", description = "Resolves a pending/in-review task and triggers the async workflow event resume flow")
    public ResponseEntity<BucketExecutionDto> resolve(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        String resolvedBy = body.getOrDefault("resolvedBy", "Anonymous");
        String notes = body.getOrDefault("notes", "");
        
        BucketExecutionDto bex = bucketExecutionService.getById(id)
                .orElseThrow(() -> new IllegalArgumentException("BucketExecution not found: " + id));

        String outcome = body.getOrDefault("outcome", "Accept");
        
        bucketResolutionService.resolveBucket(bex.getInstanceId(), bex.getBucketId(), outcome, resolvedBy, notes);
        
        BucketExecutionDto updated = bucketExecutionService.getById(id).orElse(bex);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/workload")
    @Operation(summary = "Get all workload metrics", description = "Retrieves operational workload summary KPIs across all buckets")
    public ResponseEntity<List<BucketWorkloadDto>> getAllWorkload() {
        return ResponseEntity.ok(bucketExecutionService.getAllWorkloadSummaries());
    }

    @GetMapping("/workload/{bucketId}")
    @Operation(summary = "Get workload metrics for bucket", description = "Retrieves operational workload metrics for a specific bucket ID")
    public ResponseEntity<BucketWorkloadDto> getWorkloadForBucket(@PathVariable String bucketId) {
        return ResponseEntity.ok(bucketExecutionService.getWorkloadForBucket(bucketId));
    }

}
