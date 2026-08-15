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

/** Executes a TIMER node â€” records it and continues traversal (no actual delay at engine level). */
@Component
public class TimerNodeExecutor implements NodeExecutor {

    @Autowired private TaskRecorder taskRecorder;

    @Override
    public NodeExecutionResult execute(WorkflowNodeDto node,
                                       TraversalExecutionState state,
                                       StepRecordDto step,
                                       List<StepRecordDto> trace) {
        var ti = taskRecorder.recordTaskStart(state.instance, node, state.context);

        String delayStr = extractString(node.getData(), "delayMs");
        step.setStatus("COMPLETED");
        step.setNotes("Timer node recorded (delay: "
                + (delayStr != null ? delayStr + "ms" : "unspecified") + "). Execution continues.");

        taskRecorder.recordTaskCompletion(ti, Map.of(), "COMPLETED");

        List<WorkflowEdgeDto> outEdges = state.edgesBySource.getOrDefault(node.getId(), List.of());
        if (!outEdges.isEmpty()) {
            return NodeExecutionResult.next(
                    state.nodeMap.get(outEdges.get(0).getTarget()), outEdges.get(0).getId());
        }
        return NodeExecutionResult.COMPLETED;
    }

    private String extractString(Map<String, Object> data, String key) {
        if (data == null) return null;
        Object val = data.get(key);
        return val != null ? String.valueOf(val) : null;
    }
}
