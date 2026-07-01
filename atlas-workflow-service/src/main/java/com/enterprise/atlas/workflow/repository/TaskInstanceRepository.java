package com.enterprise.atlas.workflow.repository;

import com.enterprise.atlas.workflow.entity.TaskInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TaskInstanceRepository extends JpaRepository<TaskInstance, String> {
    List<TaskInstance> findByWorkflowInstanceIdOrderByStartedAtAsc(String workflowInstanceId);
}
