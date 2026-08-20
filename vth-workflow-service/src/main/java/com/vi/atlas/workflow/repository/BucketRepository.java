package com.vi.atlas.workflow.repository;

import com.vi.atlas.workflow.entity.Bucket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BucketRepository extends JpaRepository<Bucket, String> {
    Optional<Bucket> findByBucketId(String bucketId);
    List<Bucket> findByCategory(String category);
    List<Bucket> findByOwnerGroup(String ownerGroup);
}
