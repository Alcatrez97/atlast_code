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
import java.util.Map;

/**
 * Fallback executor for any node type not explicitly handled.
 * Records a SKIPPED step and advances to the first outgoing edge.
 */
@Component
public class DefaultNodeExecutor implements NodeExecutor {

    @Autowired private TaskRecorder taskRecorder;

    @Override
    public NodeExecutionResult execute(WorkflowNodeDto node,
                                       TraversalExecutionState state,
                                       StepRecordDto step,
                                       List<StepRecordDto> trace) {
        var ti = taskRecorder.recordTaskStart(state.instance, node, state.context);
        step.setStatus("SKIPPED");
        step.setNotes("Unknown node type '" + node.getType() + "' â€“ skipping.");

        taskRecorder.recordTaskCompletion(ti, Map.of(), "COMPLETED");

        List<WorkflowEdgeDto> outEdges = state.edgesBySource.getOrDefault(node.getId(), List.of());
        if (!outEdges.isEmpty()) {
            return NodeExecutionResult.next(
                    state.nodeMap.get(outEdges.get(0).getTarget()), outEdges.get(0).getId());
        }
        return NodeExecutionResult.COMPLETED;
    }
}
