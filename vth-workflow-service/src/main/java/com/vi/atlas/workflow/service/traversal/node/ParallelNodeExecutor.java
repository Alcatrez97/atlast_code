package com.vi.atlas.workflow.service.traversal.node;

import com.vi.atlas.common.dto.StepRecordDto;
import com.vi.atlas.common.dto.WorkflowEdgeDto;
import com.vi.atlas.common.dto.WorkflowNodeDto;
import com.vi.atlas.workflow.service.traversal.GraphTraversalEngine;
import com.vi.atlas.workflow.service.traversal.NodeExecutionResult;
import com.vi.atlas.workflow.service.traversal.RuntimeGraphManager;
import com.vi.atlas.workflow.service.traversal.TaskRecorder;
import com.vi.atlas.workflow.service.traversal.TraversalContext;
import com.vi.atlas.workflow.service.traversal.TraversalExecutionState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Executes a PARALLEL node â€” fans out to all outgoing edges simultaneously
 * by adding all target nodes to the active frontiers queue.
 */
@Component
public class ParallelNodeExecutor implements NodeExecutor {

    @Autowired private TaskRecorder        taskRecorder;
    @Autowired private RuntimeGraphManager runtimeGraphManager;

    @Override
    public NodeExecutionResult execute(WorkflowNodeDto node,
                                       TraversalExecutionState state,
                                       StepRecordDto step,
                                       List<StepRecordDto> trace) {
        var ti = taskRecorder.recordTaskStart(state.instance, node, state.context);

        List<WorkflowEdgeDto> outEdges = state.edgesBySource.getOrDefault(node.getId(), List.of());
        step.setStatus("COMPLETED");
        step.setNotes("Parallel split â€“ fanned out to " + outEdges.size() + " branches.");

        // Add all branch targets to the frontier (accessed via TraversalContext)
        TraversalContext travCtx = GraphTraversalEngine.CURRENT_TRAVERSAL.get();
        for (WorkflowEdgeDto edge : outEdges) {
            WorkflowNodeDto targetNode = state.nodeMap.get(edge.getTarget());
            if (targetNode != null) {
                if (travCtx != null) {
                    travCtx.activeFrontiers.add(targetNode);
                }
                runtimeGraphManager.markEdgeActive(edge, state.activeEdges);
            }
        }
        if (state.instance != null) state.instance.setRuntimeGraph(state.runtimeGraph);

        taskRecorder.recordTaskCompletion(ti, Map.of(), "COMPLETED");
        return NodeExecutionResult.parallelFanOut();   // nextNode = null (already added to frontier)
    }
}
