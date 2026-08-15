package com.vi.atlas.workflow.service.traversal.node;

import com.vi.atlas.common.dto.StepRecordDto;
import com.vi.atlas.common.dto.WorkflowNodeDto;
import com.vi.atlas.workflow.service.traversal.NodeExecutionResult;
import com.vi.atlas.workflow.service.traversal.TraversalExecutionState;

import java.util.List;

/**
 * Strategy interface for executing a single workflow node during graph traversal.
 *
 * <p>Each implementation handles exactly one node type (START, END, RULE, â€¦).
 * The {@link NodeExecutorRegistry} maps node-type strings to the correct executor.
 *
 * <p>Implementations must:
 * <ul>
 *   <li>Append exactly one {@link StepRecordDto} to {@code trace} (or zero for
 *       continue-only paths that add their own step before returning)</li>
 *   <li>Call {@link com.vi.atlas.workflow.service.traversal.TaskRecorder#recordTaskStart}
 *       and {@link com.vi.atlas.workflow.service.traversal.TaskRecorder#recordTaskCompletion}
 *       for every node they process</li>
 *   <li>NOT commit transactions â€” Spring manages that at the service layer</li>
 * </ul>
 */
public interface NodeExecutor {

    /**
     * Executes the node and returns a result describing what the traversal loop
     * should do next.
     *
     * @param node  the graph node to execute
     * @param state all mutable traversal state (context, edges, active nodes, â€¦)
     * @param step  a partially-populated step record ({@code stepIndex}, {@code nodeId},
     *              {@code nodeType}, {@code label}, {@code enteredAt} are already set);
     *              the executor must set at least {@code status}
     * @param trace the running trace list; the executor should add {@code step} when done
     * @return a {@link NodeExecutionResult} describing the next action
     */
    NodeExecutionResult execute(WorkflowNodeDto node,
                                TraversalExecutionState state,
                                StepRecordDto step,
                                List<StepRecordDto> trace);
}
