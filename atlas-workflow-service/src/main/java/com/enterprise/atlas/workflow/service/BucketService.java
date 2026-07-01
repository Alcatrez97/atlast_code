package com.enterprise.atlas.workflow.service;

import com.enterprise.atlas.common.dto.BucketDto;
import com.enterprise.atlas.workflow.entity.Bucket;
import com.enterprise.atlas.workflow.entity.WorkflowDefinition;
import com.enterprise.atlas.workflow.entity.WorkflowVersion;
import com.enterprise.atlas.workflow.repository.BucketRepository;
import com.enterprise.atlas.workflow.repository.WorkflowDefinitionRepository;
import com.enterprise.atlas.workflow.repository.WorkflowVersionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class BucketService {

    @Autowired
    private BucketRepository bucketRepository;

    @Autowired
    private WorkflowDefinitionRepository definitionRepository;

    @Autowired
    private WorkflowVersionRepository versionRepository;

    @Transactional(readOnly = true)
    public List<BucketDto> getAllBuckets() {
        return bucketRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<BucketDto> getBucketById(String id) {
        return bucketRepository.findById(id).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<BucketDto> getBucketByBusinessKey(String bucketId) {
        return bucketRepository.findByBucketId(bucketId).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public List<BucketDto> getBucketsByCategory(String category) {
        return bucketRepository.findByCategory(category).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BucketDto> getBucketsByOwnerGroup(String ownerGroup) {
        return bucketRepository.findByOwnerGroup(ownerGroup).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public BucketDto createBucket(BucketDto dto) {
        if (bucketRepository.findByBucketId(dto.getBucketId()).isPresent()) {
            throw new IllegalArgumentException("Bucket with ID '" + dto.getBucketId() + "' already exists.");
        }

        Bucket bucket = new Bucket();
        bucket.setId(UUID.randomUUID().toString());
        bucket.setBucketId(dto.getBucketId());
        bucket.setName(dto.getName());
        bucket.setDescription(dto.getDescription());
        bucket.setCategory(dto.getCategory());
        bucket.setPriority(dto.getPriority() != null ? dto.getPriority() : "MEDIUM");
        bucket.setSlaHours(dto.getSlaHours());
        bucket.setOwnerGroup(dto.getOwnerGroup());
        bucket.setAutoActions(dto.getAutoActions());
        bucket.setActive(true);

        bucket = bucketRepository.save(bucket);
        return toDto(bucket);
    }

    public BucketDto updateBucket(String id, BucketDto dto) {
        Bucket bucket = bucketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Bucket not found with ID: " + id));

        // Check if updating bucketId business key leads to collision
        if (!bucket.getBucketId().equals(dto.getBucketId())) {
            Optional<Bucket> collision = bucketRepository.findByBucketId(dto.getBucketId());
            if (collision.isPresent()) {
                throw new IllegalArgumentException("Another bucket with ID '" + dto.getBucketId() + "' already exists.");
            }
        }

        bucket.setBucketId(dto.getBucketId());
        bucket.setName(dto.getName());
        bucket.setDescription(dto.getDescription());
        bucket.setCategory(dto.getCategory());
        bucket.setPriority(dto.getPriority());
        bucket.setSlaHours(dto.getSlaHours());
        bucket.setOwnerGroup(dto.getOwnerGroup());
        bucket.setAutoActions(dto.getAutoActions());
        bucket.setActive(dto.isActive());
        bucket.setUpdatedAt(LocalDateTime.now());

        bucket = bucketRepository.save(bucket);
        return toDto(bucket);
    }

    public void deleteBucket(String id) {
        Bucket bucket = bucketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Bucket not found with ID: " + id));
        bucket.setActive(false);
        bucketRepository.save(bucket);
    }

    @Transactional(readOnly = true)
    public List<BucketDto> resolveBucketsForWorkflow(String workflowKey) {
        WorkflowDefinition definition = definitionRepository.findByKey(workflowKey)
                .orElse(null);
        if (definition == null || definition.getActiveVersion() == null || definition.getActiveVersion() <= 0) {
            return Collections.emptyList();
        }

        Optional<WorkflowVersion> versionOpt = versionRepository.findByWorkflowDefinitionIdAndVersion(
                definition.getId(), definition.getActiveVersion()
        );
        if (!versionOpt.isPresent()) {
            return Collections.emptyList();
        }

        WorkflowVersion version = versionOpt.get();
        if (version.getDefinition() == null || version.getDefinition().getNodes() == null) {
            return Collections.emptyList();
        }

        Set<String> bucketIds = version.getDefinition().getNodes().stream()
                .filter(n -> "BUCKET".equalsIgnoreCase(n.getType()))
                .map(n -> n.getData() != null ? (String) n.getData().get("bucketId") : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (bucketIds.isEmpty()) {
            return Collections.emptyList();
        }

        return bucketRepository.findAll().stream()
                .filter(b -> bucketIds.contains(b.getBucketId()))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private BucketDto toDto(Bucket bucket) {
        if (bucket == null) return null;
        BucketDto dto = new BucketDto();
        dto.setId(bucket.getId());
        dto.setBucketId(bucket.getBucketId());
        dto.setName(bucket.getName());
        dto.setDescription(bucket.getDescription());
        dto.setCategory(bucket.getCategory());
        dto.setPriority(bucket.getPriority());
        dto.setSlaHours(bucket.getSlaHours());
        dto.setOwnerGroup(bucket.getOwnerGroup());
        dto.setAutoActions(bucket.getAutoActions());
        dto.setActive(bucket.isActive());
        dto.setCreatedAt(bucket.getCreatedAt());
        dto.setUpdatedAt(bucket.getUpdatedAt());
        return dto;
    }
}
