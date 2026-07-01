package com.enterprise.atlas.workflow.controller;

import com.enterprise.atlas.common.dto.BucketDto;
import com.enterprise.atlas.workflow.service.BucketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/buckets")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
@Tag(name = "Outcome Buckets API", description = "Operations to define, register, update, and resolve outcome buckets (queues)")
public class BucketController {

    @Autowired
    private BucketService bucketService;

    @GetMapping
    @Operation(summary = "Get all outcome buckets", description = "Retrieves a list of all registered business outcome buckets")
    public ResponseEntity<List<BucketDto>> getAllBuckets() {
        return ResponseEntity.ok(bucketService.getAllBuckets());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get bucket by database ID", description = "Retrieves the bucket details using its unique database UUID")
    public ResponseEntity<BucketDto> getBucket(@PathVariable String id) {
        return bucketService.getBucketById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/key/{bucketId}")
    @Operation(summary = "Get bucket by business ID", description = "Retrieves the bucket details using its unique business key pattern (e.g. BCK_FRAUD)")
    public ResponseEntity<BucketDto> getBucketByBusinessKey(@PathVariable String bucketId) {
        return bucketService.getBucketByBusinessKey(bucketId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create bucket", description = "Registers a new outcome bucket with priority and SLA configuration")
    public ResponseEntity<BucketDto> createBucket(@RequestBody BucketDto dto) {
        return new ResponseEntity<>(bucketService.createBucket(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update bucket", description = "Updates details, SLA, or priority of an existing outcome bucket")
    public ResponseEntity<BucketDto> updateBucket(@PathVariable String id, @RequestBody BucketDto dto) {
        return ResponseEntity.ok(bucketService.updateBucket(id, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete bucket", description = "Deletes an outcome bucket from the registry")
    public ResponseEntity<Void> deleteBucket(@PathVariable String id) {
        bucketService.deleteBucket(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/workflow/{key}")
    @Operation(summary = "Resolve buckets for workflow", description = "Resolves all outcome buckets defined within a specific workflow definition graph")
    public ResponseEntity<List<BucketDto>> resolveBucketsForWorkflow(@PathVariable String key) {
        return ResponseEntity.ok(bucketService.resolveBucketsForWorkflow(key));
    }

}
