package com.enterprise.atlas.workflow.service.traversal.node;

import com.enterprise.atlas.common.dto.StepRecordDto;
import com.enterprise.atlas.common.dto.WorkflowNodeDto;
import com.enterprise.atlas.workflow.service.traversal.BucketSuspensionManager;
import com.enterprise.atlas.workflow.service.traversal.EventSubscriptionManager;
import com.enterprise.atlas.workflow.service.traversal.NodeExecutionResult;
import com.enterprise.atlas.workflow.service.traversal.TaskRecorder;
import com.enterprise.atlas.workflow.service.traversal.TraversalExecutionState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Executes a BUCKET node — suspends the workflow and creates all required
 * side-effect records (CustomerForm status, RevertStatus, EventSubscription).
 */
@Component
public class BucketNodeExecutor implements NodeExecutor {

    @Autowired private TaskRecorder            taskRecorder;
    @Autowired private BucketSuspensionManager bucketSuspensionManager;
    @Autowired private EventSubscriptionManager eventSubscriptionManager;

    @Override
    public NodeExecutionResult execute(WorkflowNodeDto node,
                                       TraversalExecutionState state,
                                       StepRecordDto step,
                                       List<StepRecordDto> trace) {
        var ti = taskRecorder.recordTaskStart(state.instance, node, state.context);
        taskRecorder.recordTaskCompletion(ti, Map.of(), "WAITING");

        // Resolve the bucket ID
        String bucketId = null;
        if (node.getData() != null) {
            bucketId = (String) node.getData().get("bucketId");
        }
        if (bucketId == null) {
            bucketId = node.getId();
        }

        step.setStatus("WAITING");
        step.setNotes("Suspended execution. Waiting on business outcome bucket: " + step.getLabel());
        trace.add(step);

        // Side-effects: CustomerForm + RevertStatus + EventSubscription
        bucketSuspensionManager.createBucketRevertStatusAndFormPending(
                state.instanceId, state.contextId, bucketId, node, state.version);
        eventSubscriptionManager.createEventSubscription(
                state.instance, bucketId, node.getId(), Map.of());

        state.suspendedNodes.add(node);
        return NodeExecutionResult.suspended();
    }
}
