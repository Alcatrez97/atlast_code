package com.vi.atlas.workflow.repository;

import com.vi.atlas.workflow.entity.EntityRevertDtl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EntityRevertDtlRepository extends JpaRepository<EntityRevertDtl, Long> {

    List<EntityRevertDtl> findByEntityRevertId(Long entityRevertId);
}
