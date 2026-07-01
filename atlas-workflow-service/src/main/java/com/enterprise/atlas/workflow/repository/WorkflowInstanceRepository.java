package com.enterprise.atlas.workflow.repository;

import com.enterprise.atlas.workflow.entity.WorkflowInstance;
import com.enterprise.atlas.workflow.entity.WorkflowInstanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, String> {
    List<WorkflowInstance> findByWorkflowKeyOrderByCreatedAtDesc(String workflowKey);
    List<WorkflowInstance> findByStatusOrderByCreatedAtDesc(WorkflowInstanceStatus status);
    List<WorkflowInstance> findAllByOrderByCreatedAtDesc();
    long countByWorkflowVersionId(String versionId);

    @Query("SELECT wi FROM WorkflowInstance wi WHERE " +
           "(:workflowKey IS NULL OR :workflowKey = '' OR wi.workflowKey = :workflowKey) AND " +
           "(:status IS NULL OR :status = '' OR CAST(wi.status AS string) = :status) AND " +
           "(:search IS NULL OR :search = '' OR " +
           " LOWER(wi.id) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(wi.workflowKey) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(wi.currentNodeId) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(wi.businessKey) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(CAST(wi.serializedContext AS string)) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<WorkflowInstance> findAllWithFilters(
            @Param("workflowKey") String workflowKey,
            @Param("status") String status,
            @Param("search") String search,
            Pageable pageable);
}
