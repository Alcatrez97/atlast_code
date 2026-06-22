package com.enterprise.atlas.workflow.repository;

import com.enterprise.atlas.workflow.entity.EventDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EventDefinitionRepository extends JpaRepository<EventDefinition, String> {
    Optional<EventDefinition> findByEventKey(String eventKey);
}
