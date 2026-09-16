package com.vi.atlas.workflow.repository;

import com.vi.atlas.workflow.entity.StagedPayload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StagedPayloadRepository extends JpaRepository<StagedPayload, String> {

    Optional<StagedPayload> findFirstByBusinessKeyAndPayloadTypeOrderByCreatedAtDesc(String businessKey, String payloadType);

    List<StagedPayload> findAllByBusinessKey(String businessKey);

    List<StagedPayload> findAllByBusinessKeyAndStatus(String businessKey, String status);
}
