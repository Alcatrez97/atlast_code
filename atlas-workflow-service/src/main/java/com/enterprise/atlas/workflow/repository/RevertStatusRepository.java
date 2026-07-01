package com.enterprise.atlas.workflow.repository;

import com.enterprise.atlas.workflow.entity.RevertStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import com.enterprise.atlas.workflow.entity.RevertStepStatus;

@Repository
public interface RevertStatusRepository extends JpaRepository<RevertStatus, String> {

    @org.springframework.data.jpa.repository.Query("SELECT rs FROM RevertStatus rs WHERE rs.workflowInstance.id = :workflowInstanceId ORDER BY rs.createdAt DESC")
    List<RevertStatus> findByWorkflowInstanceIdOrderByCreatedAtDesc(@org.springframework.data.repository.query.Param("workflowInstanceId") String workflowInstanceId);

    @org.springframework.data.jpa.repository.Query("SELECT rs FROM RevertStatus rs WHERE rs.workflowInstance.id = :workflowInstanceId AND rs.bucketId = :bucketId AND rs.status = :status")
    Optional<RevertStatus> findByWorkflowInstanceIdAndBucketIdAndStatus(
            @org.springframework.data.repository.query.Param("workflowInstanceId") String workflowInstanceId,
            @org.springframework.data.repository.query.Param("bucketId") String bucketId,
            @org.springframework.data.repository.query.Param("status") RevertStepStatus status);

    @org.springframework.data.jpa.repository.Query("SELECT rs FROM RevertStatus rs WHERE rs.customerForm.id = :formId AND rs.bucketId = :bucketId AND rs.status = :status")
    Optional<RevertStatus> findByFormIdAndBucketIdAndStatus(
            @org.springframework.data.repository.query.Param("formId") String formId,
            @org.springframework.data.repository.query.Param("bucketId") String bucketId,
            @org.springframework.data.repository.query.Param("status") RevertStepStatus status);

    @org.springframework.data.jpa.repository.Query("SELECT rs FROM RevertStatus rs WHERE rs.customerForm.id = :formId ORDER BY rs.createdAt DESC")
    List<RevertStatus> findByFormIdOrderByCreatedAtDesc(@org.springframework.data.repository.query.Param("formId") String formId);
}
