package com.vi.atlas.workflow.service.traversal;

import com.vi.atlas.common.dto.WorkflowNodeDto;

/**
 * Value-object returned by every {@link node.NodeExecutor} after processing
 * a single graph node.
 *
 * <p>The traversal loop in {@code GraphTraversalEngine} reads these fields to
 * decide what to do after each node is processed:
 * <ul>
 *   <li>{@code nextNode} â€” next node to enqueue in the frontier (null = nothing follows)</li>
 *   <li>{@code edgeTakenId} â€” ID of the edge used to reach {@code nextNode} (for runtime graph)</li>
 *   <li>{@code suspended} â€” if {@code true} the engine must pause the traversal loop</li>
 *   <li>{@code continueLoop} â€” if {@code true} use {@code continue} instead of the normal
 *       post-node logic (used by PARALLEL, WAIT_EVENT, suspended COMMAND)</li>
 * </ul>
 */
public class NodeExecutionResult {

    public static final NodeExecutionResult COMPLETED = new NodeExecutionResult(null, null, false, false);

    private final WorkflowNodeDto nextNode;
    private final String edgeTakenId;
    private final boolean suspended;
    private final boolean continueLoop;

    public NodeExecutionResult(WorkflowNodeDto nextNode, String edgeTakenId, boolean suspended, boolean continueLoop) {
        this.nextNode = nextNode;
        this.edgeTakenId = edgeTakenId;
        this.suspended = suspended;
        this.continueLoop = continueLoop;
    }

    // --- factory helpers ---

    public static NodeExecutionResult next(WorkflowNodeDto node, String edgeId) {
        return new NodeExecutionResult(node, edgeId, false, false);
    }

    public static NodeExecutionResult suspended() {
        return new NodeExecutionResult(null, null, true, true);
    }

    public static NodeExecutionResult failed() {
        return new NodeExecutionResult(null, null, true, false);
    }

    public static NodeExecutionResult parallelFanOut() {
        return new NodeExecutionResult(null, null, false, false);
    }

    // --- accessors ---

    public WorkflowNodeDto getNextNode()  { return nextNode; }
    public String getEdgeTakenId()        { return edgeTakenId; }
    public boolean isSuspended()          { return suspended; }
    public boolean isContinueLoop()       { return continueLoop; }
}
