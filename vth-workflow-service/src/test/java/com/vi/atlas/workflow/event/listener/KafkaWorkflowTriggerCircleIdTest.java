package com.vi.atlas.workflow.event.listener;

import com.vi.atlas.common.dto.CafSubmittedEvent;
import com.vi.atlas.common.dto.ExecutionRequestDto;
import com.vi.atlas.workflow.service.BucketResolutionService;
import com.vi.atlas.workflow.service.ExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

public class KafkaWorkflowTriggerCircleIdTest {

    @Mock
    private ExecutionService executionService;

    @Mock
    private BucketResolutionService bucketResolutionService;

    @Mock
    private com.vi.atlas.workflow.repository.WorkflowInstanceRepository workflowInstanceRepository;

    @InjectMocks
    private KafkaEventListener kafkaEventListener;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testTriggerWithFlatJsonPayloadContainingCircleIdAndCafId() {
        String jsonPayload = """
            {
                "workflowKey": "order_processing",
                "cafId": "123456",
                "circleId": 12
            }
            """;

        kafkaEventListener.handleWorkflowTrigger(jsonPayload);

        ArgumentCaptor<ExecutionRequestDto> captor = ArgumentCaptor.forClass(ExecutionRequestDto.class);
        verify(executionService).execute(eq("order_processing"), captor.capture());

        ExecutionRequestDto request = captor.getValue();
        assertEquals("123456", request.getContextId());
        assertEquals("123456", request.getBusinessKey());
        assertEquals(12, request.getCircleId());
        assertNotNull(request.getContext());
        assertEquals(12, request.getContext().get("circleId"));
        assertEquals("123456", request.getContext().get("cafId"));
    }

    @Test
    public void testTriggerWithNestedContextJsonPayload() {
        String jsonPayload = """
            {
                "workflowKey": "order_processing",
                "context": {
                    "caf_id": "789012",
                    "circle_id": "18",
                    "planCode": "RED_599"
                }
            }
            """;

        kafkaEventListener.handleWorkflowTrigger(jsonPayload);

        ArgumentCaptor<ExecutionRequestDto> captor = ArgumentCaptor.forClass(ExecutionRequestDto.class);
        verify(executionService).execute(eq("order_processing"), captor.capture());

        ExecutionRequestDto request = captor.getValue();
        assertEquals("789012", request.getContextId());
        assertEquals("789012", request.getBusinessKey());
        assertEquals("18", request.getCircleId().toString());
        assertNotNull(request.getContext());
        assertEquals("18", request.getContext().get("circleId").toString());
        assertEquals("RED_599", request.getContext().get("planCode"));
    }

    @Test
    public void testTriggerWithCafSubmittedEventObject() {
        CafSubmittedEvent event = new CafSubmittedEvent();
        event.setWorkflowKey("order_processing");
        event.setCafId("555444");
        event.setCircleId(25);
        event.setContext(Map.of("customerSegment", "HNI"));

        kafkaEventListener.handleWorkflowTrigger(event);

        ArgumentCaptor<ExecutionRequestDto> captor = ArgumentCaptor.forClass(ExecutionRequestDto.class);
        verify(executionService).execute(eq("order_processing"), captor.capture());

        ExecutionRequestDto request = captor.getValue();
        assertEquals("555444", request.getContextId());
        assertEquals("555444", request.getBusinessKey());
        assertEquals(25, request.getCircleId());
        assertNotNull(request.getContext());
        assertEquals(25, request.getContext().get("circleId"));
        assertEquals("HNI", request.getContext().get("customerSegment"));
    }
}
