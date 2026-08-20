package com.vi.atlas.workflow.repository;

import com.vi.atlas.workflow.entity.ExecutionLogDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExecutionLogDetailRepository extends JpaRepository<ExecutionLogDetail, String> {
}
