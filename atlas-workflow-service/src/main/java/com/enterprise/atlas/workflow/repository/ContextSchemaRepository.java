package com.enterprise.atlas.workflow.repository;

import com.enterprise.atlas.workflow.entity.ContextSchema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ContextSchemaRepository extends JpaRepository<ContextSchema, String> {
    Optional<ContextSchema> findByWorkflowKey(String workflowKey);
}
