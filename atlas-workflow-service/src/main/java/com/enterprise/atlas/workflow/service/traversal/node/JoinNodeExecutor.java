package com.enterprise.atlas.workflow.service.traversal.node;

import com.enterprise.atlas.common.dto.StepRecordDto;
import com.enterprise.atlas.common.dto.WorkflowEdgeDto;
import com.enterprise.atlas.common.dto.WorkflowNodeDto;
import com.enterprise.atlas.workflow.service.traversal.NodeExecutionResult;
import com.enterprise.atlas.workflow.service.traversal.TaskRecorder;
import com.enterprise.atlas.workflow.service.traversal.TraversalExecutionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Executes a JOIN node.
 *
 * <p>Checks whether all incoming edges have been traversed (exist in the
 * active-edges runtime graph).  If they have, it continues to the next node;
 * otherwise it records a WAITING step and returns {@link NodeExecutionResult#COMPLETED}
 * (frontier is not advanced — the join node will be re-evaluated when more
 * branches complete).
 */
@Component
public class JoinNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(JoinNodeExecutor.class);

    @Autowired private TaskRecorder taskRecorder;

    @Override
    public NodeExecutionResult execute(WorkflowNodeDto node,
                                       TraversalExecutionState state,
                                       StepRecordDto step,
                                       List<StepRecordDto> trace) {
        var ti = taskRecorder.recordTaskStart(state.instance, node, state.context);

        List<WorkflowEdgeDto> incomingEdges = state.edgesByTarget.getOrDefault(node.getId(), List.of());

        log.info("JOIN NODE: Checking edges for node {}. incomingEdges={}, activeEdges={}",
                node.getId(),
                incomingEdges.stream().map(WorkflowEdgeDto::getId).collect(Collectors.toList()),
                state.activeEdges);

        boolean allIncomingTraversed = true;
        for (WorkflowEdgeDto edge : incomingEdges) {
            boolean hasEdge = state.activeEdges.stream()
                    .anyMatch(e -> edge.getId().equals(e.get("id")));
            log.info("JOIN NODE check: edge {} matched in activeEdges? {}", edge.getId(), hasEdge);
            if (!hasEdge) {
                allIncomingTraversed = false;
                break;
            }
        }

        if (allIncomingTraversed) {
            step.setStatus("COMPLETED");
            step.setNotes("Join convergence complete. All "
                    + incomingEdges.size() + " incoming branches resolved.");
            taskRecorder.recordTaskCompletion(ti, Map.of(), "COMPLETED");

            List<WorkflowEdgeDto> outEdges = state.edgesBySource.getOrDefault(node.getId(), List.of());
            if (!outEdges.isEmpty()) {
                return NodeExecutionResult.next(
                        state.nodeMap.get(outEdges.get(0).getTarget()), outEdges.get(0).getId());
            }
            return NodeExecutionResult.COMPLETED;

        } else {
            step.setStatus("WAITING");
            step.setNotes("Join convergence waiting. Not all incoming branches have arrived yet.");
            taskRecorder.recordTaskCompletion(ti, Map.of(), "WAITING");
            return NodeExecutionResult.COMPLETED;  // frontier not advanced; re-evaluated later
        }
    }
}
