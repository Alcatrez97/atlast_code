package com.vi.atlas.workflow.service;

import com.vi.atlas.common.dto.BucketExecutionDto;
import com.vi.atlas.common.dto.BucketWorkloadDto;
import com.vi.atlas.workflow.entity.Bucket;
import com.vi.atlas.workflow.entity.BucketExecution;
import com.vi.atlas.workflow.entity.ExecutionLog;
import com.vi.atlas.workflow.repository.BucketExecutionRepository;
import com.vi.atlas.workflow.repository.BucketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.vi.atlas.workflow.event.model.BucketCompletedEvent;
import com.vi.atlas.workflow.event.model.BucketReadySpringEvent;
import org.springframework.context.ApplicationEventPublisher;

@Service
@Transactional
public class BucketExecutionService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BucketExecutionService.class);

    @Autowired
    private BucketExecutionRepository bucketExecutionRepository;

    @Autowired
    private BucketRepository bucketRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /**
     * Auto-called by ExecutionService when a workflow routes to a BUCKET node.
     * Creates a new BucketExecution record in PENDING state.
     */
    public BucketExecutionDto createFromExecution(ExecutionLog executionLog, String bucketId) {
        Optional<Bucket> bucketOpt = bucketRepository.findByBucketId(bucketId);
        BucketExecution bex = new BucketExecution();
        bex.setId(UUID.randomUUID().toString());
        bex.setExecutionLogId(executionLog.getId());
        bex.setInstanceId(executionLog.getInstanceId());
        bex.setWorkflowKey(executionLog.getWorkflowKey());

        if (bucketOpt.isEmpty()) {
            // No registry entry â€“ create minimal record
            bex.setBucketId(bucketId);
            bex.setBucketName(bucketId); // fallback to key
            bex.setStatus("PENDING");
        } else {
            Bucket bucket = bucketOpt.get();
            bex.setBucketId(bucket.getBucketId());
            bex.setBucketName(bucket.getName());
            bex.setStatus("PENDING");
            bex.setPriority(bucket.getPriority());
            bex.setSlaHours(bucket.getSlaHours());
        }

        BucketExecution saved = bucketExecutionRepository.save(bex);

        try {
            eventPublisher.publishEvent(new BucketReadySpringEvent(this, saved));
        } catch (Exception ex) {
            log.warn("Failed to publish BucketReadySpringEvent: {}", ex.getMessage());
        }

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<BucketExecutionDto> getAll() {
        return bucketExecutionRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<BucketExecutionDto> getById(String id) {
        return bucketExecutionRepository.findById(id).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public List<BucketExecutionDto> getByBucket(String bucketId) {
        return bucketExecutionRepository.findByBucketIdOrderByCreatedAtDesc(bucketId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BucketExecutionDto> getByStatus(String status) {
        return bucketExecutionRepository.findByStatusOrderByCreatedAtAsc(status)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Transitions a PENDING bucket execution to IN_REVIEW.
     */
    public BucketExecutionDto startReview(String id) {
        BucketExecution bex = bucketExecutionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("BucketExecution not found: " + id));
        if ("RESOLVED".equalsIgnoreCase(bex.getStatus())) {
            throw new IllegalStateException("Cannot review an already resolved execution.");
        }
        bex.setStatus("IN_REVIEW");
        return toDto(bucketExecutionRepository.save(bex));
    }

    /**
     * Transitions a bucket execution to RESOLVED with resolver details.
     */
    public BucketExecutionDto resolve(String id, String resolvedBy, String notes) {
        BucketExecution bex = bucketExecutionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("BucketExecution not found: " + id));
        if ("RESOLVED".equalsIgnoreCase(bex.getStatus())) {
            throw new IllegalStateException("Execution is already resolved.");
        }
        bex.setStatus("RESOLVED");
        bex.setResolvedAt(LocalDateTime.now());
        bex.setResolvedBy(resolvedBy != null ? resolvedBy.trim() : "Anonymous");
        bex.setResolutionNotes(notes);
        
        BucketExecution saved = bucketExecutionRepository.save(bex);
        
        // Publish local event
        eventPublisher.publishEvent(new BucketCompletedEvent(this, saved));
        
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<BucketWorkloadDto> getAllWorkloadSummaries() {
        return getAllWorkloadSummaries(null);
    }

    @Transactional(readOnly = true)
    public List<BucketWorkloadDto> getAllWorkloadSummaries(String circleId) {
        List<Bucket> allBuckets = bucketRepository.findAll();
        List<BucketWorkloadDto> result = new ArrayList<>();

        List<Integer> circleList = null;
        if (circleId != null && !circleId.isBlank() && !"ALL".equalsIgnoreCase(circleId.trim())) {
            circleList = java.util.Arrays.stream(circleId.split(","))
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
        }

        for (Bucket bucket : allBuckets) {
            List<BucketExecution> items = bucketExecutionRepository
                    .findByBucketIdOrderByCreatedAtDesc(bucket.getBucketId());
            if (circleList != null && !circleList.isEmpty()) {
                final List<Integer> targetCircles = circleList;
                items = items.stream()
                        .filter(bex -> bex.getCircleId() != null && targetCircles.contains(bex.getCircleId()))
                        .collect(Collectors.toList());
            }
            result.add(buildWorkloadDto(bucket, items));
        }
        return result;
    }

    /**
     * Returns workload summary for a single bucket.
     */
    @Transactional(readOnly = true)
    public BucketWorkloadDto getWorkloadForBucket(String bucketId) {
        Bucket bucket = bucketRepository.findByBucketId(bucketId)
                .orElseThrow(() -> new IllegalArgumentException("Bucket not found: " + bucketId));
        List<BucketExecution> items = bucketExecutionRepository
                .findByBucketIdOrderByCreatedAtDesc(bucketId);
        return buildWorkloadDto(bucket, items);
    }

    // ---- Private helpers ----

    private BucketWorkloadDto buildWorkloadDto(Bucket bucket, List<BucketExecution> items) {
        BucketWorkloadDto dto = new BucketWorkloadDto();
        dto.setBucketId(bucket.getBucketId());
        dto.setBucketName(bucket.getName());
        dto.setPriority(bucket.getPriority());
        dto.setSlaHours(bucket.getSlaHours());
        dto.setOwnerGroup(bucket.getOwnerGroup());
        dto.setTotalRouted(items.size());

        long pending = 0, inReview = 0, resolved = 0, slaBreached = 0;
        long totalResolutionMinutes = 0;
        int resolvedCount = 0;

        for (BucketExecution bex : items) {
            switch (bex.getStatus().toUpperCase()) {
                case "PENDING" -> pending++;
                case "IN_REVIEW" -> inReview++;
                case "RESOLVED" -> {
                    resolved++;
                    if (bex.getResolvedAt() != null && bex.getCreatedAt() != null) {
                        totalResolutionMinutes += Duration.between(bex.getCreatedAt(), bex.getResolvedAt()).toMinutes();
                        resolvedCount++;
                    }
                }
            }
            if (bex.isSlaBreached()) slaBreached++;
        }

        dto.setPending(pending);
        dto.setInReview(inReview);
        dto.setResolved(resolved);
        dto.setSlaBreached(slaBreached);

        if (resolvedCount > 0) {
            dto.setAvgResolutionHours((double) totalResolutionMinutes / resolvedCount / 60.0);
        }
        return dto;
    }

    private BucketExecutionDto toDto(BucketExecution entity) {
        BucketExecutionDto dto = new BucketExecutionDto();
        dto.setId(entity.getId());
        dto.setExecutionLogId(entity.getExecutionLogId());
        dto.setInstanceId(entity.getInstanceId());
        dto.setWorkflowKey(entity.getWorkflowKey());
        dto.setBucketId(entity.getBucketId());
        dto.setBucketName(entity.getBucketName());
        dto.setStatus(entity.getStatus());
        dto.setPriority(entity.getPriority());
        dto.setSlaHours(entity.getSlaHours());
        dto.setSlaBreached(entity.isSlaBreached());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setResolvedAt(entity.getResolvedAt());
        dto.setResolvedBy(entity.getResolvedBy());
        dto.setResolutionNotes(entity.getResolutionNotes());
        return dto;
    }
}
