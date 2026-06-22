package com.enterprise.atlas.workflow.repository;

import com.enterprise.atlas.workflow.entity.IntegrationRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IntegrationRegistryRepository extends JpaRepository<IntegrationRegistry, String> {
    Optional<IntegrationRegistry> findByIntegrationKey(String integrationKey);
}
