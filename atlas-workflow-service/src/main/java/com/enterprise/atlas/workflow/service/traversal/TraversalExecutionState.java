package com.enterprise.atlas.workflow.service.traversal;

import com.enterprise.atlas.common.dto.WorkflowEdgeDto;
import com.enterprise.atlas.common.dto.WorkflowNodeDto;
import com.enterprise.atlas.workflow.entity.WorkflowInstance;
import com.enterprise.atlas.workflow.entity.WorkflowVersion;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.List;
import java.util.Map;

/**
 * Carries all mutable per-traversal data that {@link node.NodeExecutor} implementations
 * need so they receive a single, typed object instead of a long parameter list.
 *
 * <p>One instance of this class is created per call to {@code GraphTraversalEngine#traverse()}
 * and passed down through the traversal loop and node executors.
 *
 * <p>Fields are public for convenience within the {@code traversal} sub-package.
 * External callers must not depend on this class.
 */
public class TraversalExecutionState {

    // ---- Immutable per-traversal inputs ----

    public final WorkflowVersion version;
    public final String instanceId;
    public final String contextId;
    public final WorkflowInstance instance;

    // ---- Live execution context (mutable by node executors) ----

    public final Map<String, Object> context;
    public final StandardEvaluationContext spelCtx;

    // ---- Graph index ----

    public final Map<String, WorkflowNodeDto> nodeMap;
    public final Map<String, List<WorkflowEdgeDto>> edgesBySource;
    public final Map<String, List<WorkflowEdgeDto>> edgesByTarget;

    // ---- Runtime graph state (mutated by RuntimeGraphManager) ----

    public final List<Map<String, Object>> activeNodes;
    public final List<Map<String, Object>> activeEdges;
    public final Map<String, Object> runtimeGraph;

    // ---- Suspension state ----

    public final List<WorkflowNodeDto> suspendedNodes;

    // ---- Step counter (boxed so executors can increment it) ----

    public final int[] stepIdx;   // single-element array used as a mutable int

    public TraversalExecutionState(
            WorkflowVersion version,
            String instanceId,
            String contextId,
            WorkflowInstance instance,
            Map<String, Object> context,
            StandardEvaluationContext spelCtx,
            Map<String, WorkflowNodeDto> nodeMap,
            Map<String, List<WorkflowEdgeDto>> edgesBySource,
            Map<String, List<WorkflowEdgeDto>> edgesByTarget,
            List<Map<String, Object>> activeNodes,
            List<Map<String, Object>> activeEdges,
            Map<String, Object> runtimeGraph,
            List<WorkflowNodeDto> suspendedNodes,
            int[] stepIdx) {
        this.version        = version;
        this.instanceId     = instanceId;
        this.contextId      = contextId;
        this.instance       = instance;
        this.context        = context;
        this.spelCtx        = spelCtx;
        this.nodeMap        = nodeMap;
        this.edgesBySource  = edgesBySource;
        this.edgesByTarget  = edgesByTarget;
        this.activeNodes    = activeNodes;
        this.activeEdges    = activeEdges;
        this.runtimeGraph   = runtimeGraph;
        this.suspendedNodes = suspendedNodes;
        this.stepIdx        = stepIdx;
    }

    /** Convenience: increment the step counter and return the old value. */
    public int nextStepIdx() {
        return stepIdx[0]++;
    }

    /** Current step index value (without incrementing). */
    public int currentStepIdx() {
        return stepIdx[0];
    }
}
