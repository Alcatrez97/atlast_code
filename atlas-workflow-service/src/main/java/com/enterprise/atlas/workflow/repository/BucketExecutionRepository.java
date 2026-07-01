package com.enterprise.atlas.workflow.repository;

import com.enterprise.atlas.workflow.entity.BucketExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BucketExecutionRepository extends JpaRepository<BucketExecution, String> {

    List<BucketExecution> findByBucketIdOrderByCreatedAtDesc(String bucketId);

    List<BucketExecution> findByStatusOrderByCreatedAtAsc(String status);

    List<BucketExecution> findAllByOrderByCreatedAtDesc();

    long countByBucketIdAndStatus(String bucketId, String status);

    long countByBucketId(String bucketId);

    @org.springframework.data.jpa.repository.Query("SELECT be FROM BucketExecution be WHERE be.workflowInstance.id = :instanceId AND be.bucketId = :bucketId AND be.status <> 'RESOLVED'")
    List<BucketExecution> findPendingByInstanceIdAndBucketId(
            @org.springframework.data.repository.query.Param("instanceId") String instanceId,
            @org.springframework.data.repository.query.Param("bucketId") String bucketId);

    List<BucketExecution> findByWorkflowInstanceId(String instanceId);
}
