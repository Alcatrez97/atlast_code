package com.vi.atlas.workflow.service.traversal;

import com.vi.atlas.common.dto.WorkflowEdgeDto;
import com.vi.atlas.common.dto.WorkflowNodeDto;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mutable context shared across the entire traversal of one workflow execution.
 * Stored in a {@code ThreadLocal} inside {@code GraphTraversalEngine} so that
 * helper beans (e.g. {@code TaskRecorder}) can read/write it without needing
 * it passed as a parameter to every call.
 *
 * <p>All fields are package-private; external code must not depend on this class.
 */
public class TraversalContext {

    /** ID of the workflow instance currently being traversed. */
    public String instanceId;

    /** Nodes that are "ready to be visited next" in the sequential traversal. */
    public List<WorkflowNodeDto> activeFrontiers;

    /** Quick O(1) node lookup by node ID. */
    public Map<String, WorkflowNodeDto> nodeMap;

    /** Outgoing edges indexed by source node ID. */
    public Map<String, List<WorkflowEdgeDto>> edgesBySource;

    /** Incoming edges indexed by target node ID. */
    public Map<String, List<WorkflowEdgeDto>> edgesByTarget;

    /** Edges that have been traversed so far (runtime-graph data). */
    public List<Map<String, Object>> activeEdges;

    /** The live execution context (key â†’ value) for SpEL evaluation. */
    public Map<String, Object> context;

    /** Nodes that caused the traversal to pause (BUCKET, WAIT_EVENT, â€¦). */
    public List<WorkflowNodeDto> suspendedNodes;

    /**
     * In-memory task status cache: nodeId â†’ "COMPLETED" | "SKIPPED" | "WAITING" | â€¦
     * Avoids repeated DB round-trips during a single traversal pass.
     */
    public Map<String, String> localTaskStatuses;

    /**
     * In-memory task output cache: nodeId â†’ Map<String, Object> output data.
     */
    public Map<String, Map<String, Object>> localTaskOutputs = new java.util.HashMap<>();

    /** Set of node IDs already added to the runtime-graph {@code activeNodes} list. */
    public Set<String> activeNodeIds;

    /**
     * In-memory task instances accumulated during this traversal pass.
     * Flushed in a single batch to the database at the traversal boundary.
     */
    public Map<String, com.vi.atlas.workflow.entity.TaskInstance> inMemoryTasks = new java.util.LinkedHashMap<>();
}
