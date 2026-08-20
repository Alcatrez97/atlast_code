package com.vi.atlas.workflow.service.traversal.node;

import com.vi.atlas.common.dto.StepRecordDto;
import com.vi.atlas.common.dto.WorkflowEdgeDto;
import com.vi.atlas.common.dto.WorkflowNodeDto;
import com.vi.atlas.workflow.service.traversal.NodeExecutionResult;
import com.vi.atlas.workflow.service.traversal.TaskRecorder;
import com.vi.atlas.workflow.service.traversal.TraversalExecutionState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/** Executes a START node â€” records the step and advances to the first outgoing edge. */
@Component
public class StartNodeExecutor implements NodeExecutor {

    @Autowired private TaskRecorder taskRecorder;

    @Override
    public NodeExecutionResult execute(WorkflowNodeDto node,
                                       TraversalExecutionState state,
                                       StepRecordDto step,
                                       List<StepRecordDto> trace) {
        var ti = taskRecorder.recordTaskStart(state.instance, node, state.context);
        step.setStatus("ENTERED");
        step.setNotes("Workflow execution started.");

        List<WorkflowEdgeDto> outEdges = state.edgesBySource.getOrDefault(node.getId(), List.of());
        WorkflowNodeDto nextNode = null;
        String edgeTakenId = null;
        if (!outEdges.isEmpty()) {
            var taken = outEdges.get(0);
            edgeTakenId = taken.getId();
            nextNode = state.nodeMap.get(taken.getTarget());
        }

        taskRecorder.recordTaskCompletion(ti, java.util.Map.of(), "COMPLETED");
        return nextNode != null
                ? NodeExecutionResult.next(nextNode, edgeTakenId)
                : NodeExecutionResult.COMPLETED;
    }
}
