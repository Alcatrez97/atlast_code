package com.vi.atlas.workflow.service.traversal;

import com.vi.atlas.common.dto.WorkflowDefinitionDto;
import com.vi.atlas.common.dto.WorkflowNodeDto;
import com.vi.atlas.workflow.entity.Bucket;
import com.vi.atlas.workflow.entity.EntityRevert;
import com.vi.atlas.workflow.entity.WorkflowDefinition;
import com.vi.atlas.workflow.entity.WorkflowVersion;
import com.vi.atlas.workflow.repository.BucketRepository;
import com.vi.atlas.workflow.repository.EntityRevertRepository;
import com.vi.atlas.workflow.repository.RevertStatusRepository;
import com.vi.atlas.workflow.service.domain.EntityStatusSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BucketSuspensionManagerCircleIdTest {

    @Mock
    private BucketRepository bucketRepository;

    @Mock
    private RevertStatusRepository revertStatusRepository;

    @Mock
    private EntityRevertRepository entityRevertRepository;

    @Mock
    private EntityStatusSyncService entityStatusSyncService;

    @InjectMocks
    private BucketSuspensionManager bucketSuspensionManager;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testEntityRevertCreatedWithSuppliedCircleId() {
        String instanceId = "inst-101";
        String formId = "987654";
        String bucketId = "A2";

        WorkflowNodeDto node = new WorkflowNodeDto();
        node.setId("bucket-node-1");
        node.setLabel("A2 Verification");
        node.setType("BUCKET");
        node.setData(Map.of("bucketId", bucketId));

        WorkflowDefinition def = new WorkflowDefinition();
        def.setKey("order_processing");
        WorkflowVersion version = new WorkflowVersion();
        version.setWorkflowDefinition(def);

        Bucket bucket = new Bucket();
        bucket.setBucketId(bucketId);
        bucket.setName("A2 Review");
        bucket.setEntTypeId("WORKFLOW_ENGINE");
        bucket.setPendingRevertStatus("PENDING");
        when(bucketRepository.findByBucketId(bucketId)).thenReturn(Optional.of(bucket));

        when(entityRevertRepository.findByWorkflowInstanceIdAndBucketIdAndRevertStatusAndActiveTrue(
                instanceId, bucketId, "PENDING")).thenReturn(Optional.empty());

        Map<String, Object> context = new HashMap<>();
        context.put("cafId", "987654");
        context.put("circleId", 22);

        bucketSuspensionManager.createBucketRevertStatusAndFormPending(
                instanceId, formId, bucketId, node, version, context);

        ArgumentCaptor<EntityRevert> captor = ArgumentCaptor.forClass(EntityRevert.class);
        verify(entityRevertRepository).save(captor.capture());

        EntityRevert saved = captor.getValue();
        assertNotNull(saved);
        assertEquals(987654L, saved.getCafId());
        assertEquals(22, saved.getCircleId(), "circleId must match the one supplied in context!");
        assertEquals("BUCKET", saved.getRevertCategory());
        assertEquals("A2", saved.getBucketId());
    }

    @Test
    public void testEntityRevertWithAlternativeCircleIdCasing() {
        String instanceId = "inst-102";
        String formId = "987655";
        String bucketId = "AWCS";

        WorkflowNodeDto node = new WorkflowNodeDto();
        node.setId("bucket-node-2");
        node.setType("BUCKET");
        node.setData(Map.of("bucketId", bucketId));

        WorkflowDefinition def = new WorkflowDefinition();
        def.setKey("order_processing");
        WorkflowVersion version = new WorkflowVersion();
        version.setWorkflowDefinition(def);

        Bucket bucket = new Bucket();
        bucket.setBucketId(bucketId);
        when(bucketRepository.findByBucketId(bucketId)).thenReturn(Optional.of(bucket));

        when(entityRevertRepository.findByWorkflowInstanceIdAndBucketIdAndRevertStatusAndActiveTrue(
                instanceId, bucketId, "PENDING")).thenReturn(Optional.empty());

        Map<String, Object> context = new HashMap<>();
        context.put("caf_id", "987655");
        context.put("circle_id", "45");

        bucketSuspensionManager.createBucketRevertStatusAndFormPending(
                instanceId, formId, bucketId, node, version, context);

        ArgumentCaptor<EntityRevert> captor = ArgumentCaptor.forClass(EntityRevert.class);
        verify(entityRevertRepository).save(captor.capture());

        EntityRevert saved = captor.getValue();
        assertNotNull(saved);
        assertEquals(987655L, saved.getCafId());
        assertEquals(45, saved.getCircleId(), "circle_id casing must be correctly resolved!");
    }
}
