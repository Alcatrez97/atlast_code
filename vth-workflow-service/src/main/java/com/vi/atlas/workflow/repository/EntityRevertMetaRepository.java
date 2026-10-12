package com.vi.atlas.workflow.repository;

import com.vi.atlas.workflow.entity.EntityRevertMeta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EntityRevertMetaRepository extends JpaRepository<EntityRevertMeta, Long> {

    List<EntityRevertMeta> findByEntityRevertId(Long entityRevertId);
}
