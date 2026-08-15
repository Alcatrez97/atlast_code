package com.vi.atlas.workflow.service.traversal;

import com.vi.atlas.common.dto.WorkflowEdgeDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Centralises every "which edge should we take?" decision.
 *
 * <p>There are three distinct edge-selection strategies used in traversal:
 * <ol>
 *   <li>{@link #chooseEdge} â€” for RULE nodes (true/false condition on each edge)</li>
 *   <li>{@link #matchDecisionEdge} â€” for DECISION nodes (value equality or SpEL on each edge)</li>
 *   <li>{@link #chooseBucketEdgeByOutcome} â€” for BUCKET resume (outcomeType field on each edge)</li>
 *   <li>{@link #isBucketEdgeTaken} â€” used by the activation-based traversal to check
 *       whether an individual edge from a completed BUCKET node was actually traversed</li>
 * </ol>
 *
 * <p>All logic is copied verbatim from {@code GraphTraversalEngine} â€” no algorithmic changes.
 */
@Component
public class EdgeSelector {

    @Autowired
    private SpelEvaluator spel;

    // -----------------------------------------------------------------------
    // RULE-node edge selection
    // -----------------------------------------------------------------------

    /**
     * Selects the outgoing edge for a RULE node.
     *
     * <p>Priority:
     * <ol>
     *   <li>Edge whose condition is literally {@code "true"} and rule evaluated to true</li>
     *   <li>Edge whose condition is literally {@code "false"} and rule evaluated to false</li>
     *   <li>Edge whose arbitrary SpEL condition evaluates to {@code true}</li>
     *   <li>Fallback: first edge with no condition (unconditional default)</li>
     *   <li>Last-resort: first edge in the list</li>
     * </ol>
     */
    public WorkflowEdgeDto chooseEdge(List<WorkflowEdgeDto> edges,
                                      StandardEvaluationContext spelCtx,
                                      boolean ruleResult) {
        for (WorkflowEdgeDto edge : edges) {
            String cond = extractString(edge.getData(), "condition");
            if (cond == null || cond.isBlank()) continue;

            String trimmed = cond.trim();
            if ("true".equalsIgnoreCase(trimmed)) {
                if (ruleResult) return edge;
                continue;
            }
            if ("false".equalsIgnoreCase(trimmed)) {
                if (!ruleResult) return edge;
                continue;
            }
            // Arbitrary SpEL condition on the edge
            Object result = spel.evaluate(cond, spelCtx);
            if (Boolean.TRUE.equals(result)) return edge;
        }
        // Fallback: first unconditional edge
        for (WorkflowEdgeDto edge : edges) {
            String cond = extractString(edge.getData(), "condition");
            if (cond == null || cond.isBlank()) return edge;
        }
        return edges.isEmpty() ? null : edges.get(0);
    }

    // -----------------------------------------------------------------------
    // DECISION-node edge selection
    // -----------------------------------------------------------------------

    /**
     * Selects the outgoing edge for a DECISION node by matching the runtime
     * field value against each edge's condition (string equality first, then SpEL).
     */
    public WorkflowEdgeDto matchDecisionEdge(List<WorkflowEdgeDto> edges,
                                              Object fieldValue,
                                              StandardEvaluationContext spelCtx) {
        String fieldStr = fieldValue != null ? String.valueOf(fieldValue) : "";
        for (WorkflowEdgeDto edge : edges) {
            String cond = extractString(edge.getData(), "condition");
            if (cond == null || cond.isBlank()) continue;
            if (cond.equalsIgnoreCase(fieldStr)) return edge;
            Object result = spel.evaluate(cond, spelCtx);
            if (Boolean.TRUE.equals(result)) return edge;
        }
        // Fallback: first unconditional edge
        for (WorkflowEdgeDto edge : edges) {
            String cond = extractString(edge.getData(), "condition");
            if (cond == null || cond.isBlank()) return edge;
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // BUCKET resume edge selection
    // -----------------------------------------------------------------------

    /**
     * Selects the outgoing edge for a BUCKET resume by matching the outcome string.
     *
     * <p>Tries {@code outcomeType} field first; falls back to SpEL condition
     * (backward compat) using {@code "Accept"} as the implicit true outcome.
     */
    public WorkflowEdgeDto chooseBucketEdgeByOutcome(List<WorkflowEdgeDto> outEdges,
                                                      String outcome,
                                                      StandardEvaluationContext spelCtx) {
        if (outcome != null) {
            for (WorkflowEdgeDto edge : outEdges) {
                if (edge.getData() != null) {
                    String outcomeType = (String) edge.getData().get("outcomeType");
                    if (outcomeType != null && outcomeType.equalsIgnoreCase(outcome)) {
                        return edge;
                    }
                }
            }
        }
        // Fallback to SpEL condition
        return chooseEdge(outEdges, spelCtx, "Accept".equalsIgnoreCase(outcome));
    }

    // -----------------------------------------------------------------------
    // Activation-based traversal: was a given edge actually taken?
    // -----------------------------------------------------------------------

    /**
     * Decides whether {@code edge} leading out of a completed BUCKET node
     * was the edge actually taken for the given {@code outcome}.
     *
     * <p>Logic (in priority order):
     * <ol>
     *   <li>Edge has a matching {@code outcomeType} â†’ yes</li>
     *   <li>Edge has no {@code outcomeType} but another edge does match â†’ no</li>
     *   <li>Edge has an arbitrary SpEL condition â†’ evaluate it</li>
     *   <li>Default: taken only if outcome is {@code "Accept"}</li>
     * </ol>
     */
    public boolean isBucketEdgeTaken(WorkflowEdgeDto edge,
                                      String outcome,
                                      List<WorkflowEdgeDto> allOutEdges,
                                      StandardEvaluationContext spelCtx) {
        if (outcome == null) return false;

        // 1. Explicit outcomeType match
        if (edge.getData() != null) {
            String outcomeType = (String) edge.getData().get("outcomeType");
            if (outcomeType != null && !outcomeType.isBlank()) {
                return outcomeType.equalsIgnoreCase(outcome);
            }
        }

        // 2. If another edge explicitly matches, this one doesn't
        boolean anotherEdgeMatches = false;
        for (WorkflowEdgeDto other : allOutEdges) {
            if (other.getData() != null) {
                String ot = (String) other.getData().get("outcomeType");
                if (ot != null && ot.equalsIgnoreCase(outcome)) {
                    anotherEdgeMatches = true;
                    break;
                }
            }
        }
        if (anotherEdgeMatches) return false;

        // 3. SpEL condition fallback
        String cond = extractString(edge.getData(), "condition");
        if (cond != null && !cond.trim().isEmpty()) {
            Object res = spel.evaluate(cond, spelCtx);
            return Boolean.TRUE.equals(res);
        }

        // 4. Ultimate default
        return "Accept".equalsIgnoreCase(outcome);
    }

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------

    private String extractString(Map<String, Object> data, String key) {
        if (data == null) return null;
        Object val = data.get(key);
        return val != null ? String.valueOf(val) : null;
    }
}
