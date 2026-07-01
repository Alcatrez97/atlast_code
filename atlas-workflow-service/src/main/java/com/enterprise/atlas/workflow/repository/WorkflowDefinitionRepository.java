package com.enterprise.atlas.workflow.repository;

import com.enterprise.atlas.workflow.entity.WorkflowDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, String> {
    Optional<WorkflowDefinition> findByKey(String key);
    boolean existsByKey(String key);
    java.util.List<WorkflowDefinition> findAllByActiveTrue();
}
