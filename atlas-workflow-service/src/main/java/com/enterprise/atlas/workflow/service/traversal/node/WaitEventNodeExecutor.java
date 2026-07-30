package com.enterprise.atlas.workflow.service.traversal.node;

import com.enterprise.atlas.common.dto.StepRecordDto;
import com.enterprise.atlas.common.dto.WorkflowNodeDto;
import com.enterprise.atlas.workflow.service.traversal.EventSubscriptionManager;
import com.enterprise.atlas.workflow.service.traversal.NodeExecutionResult;
import com.enterprise.atlas.workflow.service.traversal.TaskRecorder;
import com.enterprise.atlas.workflow.service.traversal.TraversalExecutionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Executes a WAIT_EVENT node — suspends the workflow and registers an
 * {@link com.enterprise.atlas.workflow.entity.EventSubscription} so it can be
 * resumed when the expected event arrives.
 */
@Component
public class WaitEventNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(WaitEventNodeExecutor.class);

    @Autowired private TaskRecorder             taskRecorder;
    @Autowired private EventSubscriptionManager eventSubscriptionManager;

    @Override
    public NodeExecutionResult execute(WorkflowNodeDto node,
                                       TraversalExecutionState state,
                                       StepRecordDto step,
                                       List<StepRecordDto> trace) {
        var ti = taskRecorder.recordTaskStart(state.instance, node, state.context);
        taskRecorder.recordTaskCompletion(ti, Map.of(), "WAITING");

        String eventType = extractString(node.getData(), "eventType");
        if (eventType == null || eventType.isBlank()) {
            eventType = "GENERIC_EVENT";
        }

        step.setStatus("WAITING");
        step.setNotes("Suspended execution. Waiting on event: " + eventType);
        trace.add(step);

        eventSubscriptionManager.createEventSubscription(
                state.instance, eventType, node.getId(), Map.of());

        log.info("Suspended at WAIT_EVENT node ID: {}, Label: {}. Subscribed to eventType: {}",
                node.getId(), node.getLabel(), eventType);

        state.suspendedNodes.add(node);
        return NodeExecutionResult.suspended();
    }

    private String extractString(Map<String, Object> data, String key) {
        if (data == null) return null;
        Object val = data.get(key);
        return val != null ? String.valueOf(val) : null;
    }
}
