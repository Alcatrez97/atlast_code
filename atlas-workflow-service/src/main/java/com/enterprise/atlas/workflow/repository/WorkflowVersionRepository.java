package com.enterprise.atlas.workflow.repository;

import com.enterprise.atlas.workflow.entity.WorkflowVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersion, String> {
    
    List<WorkflowVersion> findByWorkflowDefinitionIdOrderByVersionAsc(String workflowDefinitionId);
    
    Optional<WorkflowVersion> findByWorkflowDefinitionIdAndVersion(String workflowDefinitionId, Integer version);
    
    @Query("SELECT wv FROM WorkflowVersion wv WHERE wv.workflowDefinition.key = :key AND wv.version = :version")
    Optional<WorkflowVersion> findByWorkflowDefinitionKeyAndVersion(@Param("key") String key, @Param("version") Integer version);

    @Query("SELECT COALESCE(MAX(wv.version), 0) FROM WorkflowVersion wv WHERE wv.workflowDefinition.id = :definitionId")
    Integer findMaxVersionByDefinitionId(@Param("definitionId") String definitionId);
}
