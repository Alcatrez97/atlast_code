package com.enterprise.atlas.workflow.repository;

import com.enterprise.atlas.workflow.entity.ExecutionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExecutionRepository extends JpaRepository<ExecutionLog, String> {

    List<ExecutionLog> findByWorkflowKeyOrderByStartedAtDesc(String workflowKey);

    Page<ExecutionLog> findAllByOrderByStartedAtDesc(Pageable pageable);

    List<ExecutionLog> findByContextIdAndStatus(String contextId, com.enterprise.atlas.workflow.entity.WorkflowInstanceStatus status);

    @org.springframework.data.jpa.repository.Query("SELECT el FROM ExecutionLog el WHERE el.workflowInstance.id = :instanceId AND el.status = :status")
    List<ExecutionLog> findByInstanceIdAndStatus(@org.springframework.data.repository.query.Param("instanceId") String instanceId, @org.springframework.data.repository.query.Param("status") com.enterprise.atlas.workflow.entity.WorkflowInstanceStatus status);

    @org.springframework.data.jpa.repository.Query("SELECT el FROM ExecutionLog el WHERE el.workflowInstance.id = :instanceId ORDER BY el.startedAt ASC")
    List<ExecutionLog> findByInstanceId(@org.springframework.data.repository.query.Param("instanceId") String instanceId);
}
