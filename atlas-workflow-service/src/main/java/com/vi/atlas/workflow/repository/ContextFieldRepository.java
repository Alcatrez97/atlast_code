package com.vi.atlas.workflow.repository;

import com.vi.atlas.workflow.entity.ContextField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContextFieldRepository extends JpaRepository<ContextField, String> {
}
