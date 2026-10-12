package com.vi.atlas.workflow.repository;

import com.vi.atlas.workflow.entity.EntityRevert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EntityRevertRepository extends JpaRepository<EntityRevert, Long> {

    List<EntityRevert> findByCafId(Long cafId);

    List<EntityRevert> findByWorkflowInstanceId(String workflowInstanceId);

    @Query("SELECT er FROM EntityRevert er WHERE er.cafId = :cafId AND er.bucketId = :bucketId AND er.revertStatus = :revertStatus AND er.active = true")
    Optional<EntityRevert> findByCafIdAndBucketIdAndRevertStatusAndActiveTrue(
            @Param("cafId") Long cafId,
            @Param("bucketId") String bucketId,
            @Param("revertStatus") String revertStatus);

    @Query("SELECT er FROM EntityRevert er WHERE er.workflowInstanceId = :workflowInstanceId AND er.bucketId = :bucketId AND er.revertStatus = :revertStatus AND er.active = true")
    Optional<EntityRevert> findByWorkflowInstanceIdAndBucketIdAndRevertStatusAndActiveTrue(
            @Param("workflowInstanceId") String workflowInstanceId,
            @Param("bucketId") String bucketId,
            @Param("revertStatus") String revertStatus);

    @Query("SELECT er FROM EntityRevert er WHERE er.cafId = :cafId AND er.revertStatus = :revertStatus AND er.active = true")
    List<EntityRevert> findByCafIdAndRevertStatusAndActiveTrue(
            @Param("cafId") Long cafId,
            @Param("revertStatus") String revertStatus);

    Optional<EntityRevert> findTopByCafIdOrderByRevertSeqNumberDesc(Long cafId);
}
