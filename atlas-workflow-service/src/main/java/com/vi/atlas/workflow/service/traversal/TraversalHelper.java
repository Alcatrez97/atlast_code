package com.vi.atlas.workflow.service.traversal;

import com.vi.atlas.common.dto.WorkflowEdgeDto;
import com.vi.atlas.common.dto.WorkflowNodeDto;
import com.vi.atlas.workflow.service.traversal.node.*;
import com.vi.atlas.workflow.service.traversal.TraversalContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Helper component that provides reusable traversal utilities extracted from {@link GraphTraversalEngine}.
 * This keeps the orchestrator thin and improves readability.
 */
@Component
public class TraversalHelper {

    private static final Logger log = LoggerFactory.getLogger(TraversalHelper.class);

    @Autowired private SpelEvaluator spelEvaluator;
    @Autowired private EdgeSelector edgeSelector;
    @Autowired private TaskRecorder taskRecorder;
    @Autowired private RuntimeGraphManager runtimeGraphManager;

    // -----------------------------------------------------------------------
    // Activation based helpers
    // -----------------------------------------------------------------------
    public static class ActivationDecision {
        public boolean canEvaluate;
        public boolean isBypassed;
    }

    public ActivationDecision evaluateActivation(WorkflowNodeDto node, String nodeId, String instanceId, StandardEvaluationContext spelCtx) {
        ActivationDecision d = new ActivationDecision();
        if ("START".equalsIgnoreCase(node.getType())) {
            d.canEvaluate = true;
            return d;
        }
        TraversalContext travCtx = GraphTraversalEngine.CURRENT_TRAVERSAL.get();
        List<WorkflowEdgeDto> incomingEdges = travCtx != null ? travCtx.edgesByTarget.getOrDefault(nodeId, List.of()) : List.of();
        if (incomingEdges.isEmpty()) {
            d.canEvaluate = true;
            return d;
        }
        boolean allPredecessorsResolved = true;
        List<String> activeIncoming = new ArrayList<>();
        List<String> skippedIncoming = new ArrayList<>();
        for (WorkflowEdgeDto edge : incomingEdges) {
            String srcId = edge.getSource();
            String srcStatus = taskRecorder.getTaskStatus(instanceId, srcId);
            if (srcStatus == null) {
                allPredecessorsResolved = false;
                break;
            }
            WorkflowNodeDto srcNode = travCtx.nodeMap.get(srcId);
            String srcType = srcNode != null && srcNode.getType() != null ? srcNode.getType().toUpperCase() : "";

            if ("COMPLETED".equalsIgnoreCase(srcStatus)) {
                if ("BUCKET".equals(srcType)) {
                    String outcome = resolveOutcome(srcId, travCtx);
                    List<WorkflowEdgeDto> allOut = travCtx.edgesBySource.getOrDefault(srcId, List.of());
                    if (edgeSelector.isBucketEdgeTaken(edge, outcome, allOut, spelCtx))
                        activeIncoming.add(edge.getId());
                    else
                        skippedIncoming.add(edge.getId());
                } else if ("RULE".equals(srcType)) {
                    Map<String, Object> output = taskRecorder.getTaskOutput(instanceId, srcId);
                    boolean ruleResult = false;
                    if (output != null && output.containsKey("expressionResult")) {
                        ruleResult = Boolean.TRUE.equals(output.get("expressionResult"));
                    }
                    List<WorkflowEdgeDto> allOut = travCtx.edgesBySource.getOrDefault(srcId, List.of());
                    WorkflowEdgeDto chosenEdge = edgeSelector.chooseEdge(allOut, spelCtx, ruleResult);
                    if (chosenEdge != null && edge.getId().equals(chosenEdge.getId())) {
                        activeIncoming.add(edge.getId());
                    } else {
                        skippedIncoming.add(edge.getId());
                    }
                } else if ("DECISION".equals(srcType)) {
                    Map<String, Object> output = taskRecorder.getTaskOutput(instanceId, srcId);
                    Object fieldValue = output != null ? output.get("fieldValue") : null;
                    List<WorkflowEdgeDto> allOut = travCtx.edgesBySource.getOrDefault(srcId, List.of());
                    WorkflowEdgeDto chosenEdge = edgeSelector.matchDecisionEdge(allOut, fieldValue, spelCtx);
                    if (chosenEdge != null && edge.getId().equals(chosenEdge.getId())) {
                        activeIncoming.add(edge.getId());
                    } else {
                        skippedIncoming.add(edge.getId());
                    }
                } else {
                    String cond = extractString(edge.getData(), "condition");
                    if (cond != null && !cond.trim().isEmpty()) {
                        Object res = spelEvaluator.evaluate(cond, spelCtx);
                        if (Boolean.TRUE.equals(res)) activeIncoming.add(edge.getId());
                        else skippedIncoming.add(edge.getId());
                    } else {
                        activeIncoming.add(edge.getId());
                    }
                }
            } else if ("SKIPPED".equalsIgnoreCase(srcStatus)) {
                if ("BUCKET".equals(srcType)) {
                    String disabled = srcNode.getData() != null ? (String) srcNode.getData().get("disabledBehavior") : null;
                    if (disabled == null || disabled.isBlank()) disabled = "SKIP_ACCEPT";
                    String synthetic = "SKIP_ACCEPT".equalsIgnoreCase(disabled) ? "Accept"
                            : "SKIP_REJECT".equalsIgnoreCase(disabled) ? "Reject" : null;
                    if (synthetic != null) {
                        List<WorkflowEdgeDto> allOut = travCtx.edgesBySource.getOrDefault(srcId, List.of());
                        if (edgeSelector.isBucketEdgeTaken(edge, synthetic, allOut, spelCtx))
                            activeIncoming.add(edge.getId());
                        else
                            skippedIncoming.add(edge.getId());
                    } else {
                        skippedIncoming.add(edge.getId());
                    }
                } else {
                    skippedIncoming.add(edge.getId());
                }
            } else if ("FAILED".equalsIgnoreCase(srcStatus)) {
                skippedIncoming.add(edge.getId());
            } else {
                allPredecessorsResolved = false;
                break;
            }
        }
        if (!allPredecessorsResolved) return d;
        d.canEvaluate = true;
        String joinType = extractString(node.getData(), "joinType");
        if (joinType == null || joinType.trim().isEmpty()) joinType = "AND";
        d.isBypassed = "OR".equalsIgnoreCase(joinType) ? activeIncoming.isEmpty() : !skippedIncoming.isEmpty();
        return d;
    }

    public boolean evaluateBusinessEligibility(WorkflowNodeDto node, StandardEvaluationContext spelCtx) {
        String rule = extractString(node.getData(), "businessEligibilityRule");
        if (rule == null || rule.isBlank()) rule = extractString(node.getData(), "activationCondition");
        if (rule == null || rule.isBlank()) return true;
        Object result = spelEvaluator.evaluate(rule, spelCtx);
        return Boolean.TRUE.equals(result);
    }

    public String resolveOutcome(String srcId, TraversalContext travCtx) {
        String outcome = travCtx.context != null ? (String) travCtx.context.get(srcId + "_outcome") : null;
        if (outcome == null && travCtx.context != null) outcome = (String) travCtx.context.get("lastOutcome");
        return outcome;
    }

    public boolean isActiveNode(String nodeId) {
        TraversalContext travCtx = GraphTraversalEngine.CURRENT_TRAVERSAL.get();
        return travCtx != null && travCtx.activeNodeIds != null && travCtx.activeNodeIds.contains(nodeId);
    }

    public void markIncomingEdgesActive(String nodeId, String instanceId, List<Map<String, Object>> activeEdges, TraversalExecutionState state) {
        TraversalContext travCtx = GraphTraversalEngine.CURRENT_TRAVERSAL.get();
        if (travCtx == null) return;
        for (WorkflowEdgeDto edge : travCtx.edgesByTarget.getOrDefault(nodeId, List.of())) {
            if (taskRecorder.getTaskStatus(instanceId, edge.getSource()) != null) {
                runtimeGraphManager.markEdgeActive(edge, activeEdges);
            }
        }
    }

    public void markIncomingEdgesActiveForActivated(String nodeId, List<Map<String, Object>> activeEdges, TraversalExecutionState state) {
        TraversalContext travCtx = GraphTraversalEngine.CURRENT_TRAVERSAL.get();
        if (travCtx == null) return;
        for (WorkflowEdgeDto edge : travCtx.edgesByTarget.getOrDefault(nodeId, List.of())) {
            if (isActiveNode(edge.getSource())) {
                runtimeGraphManager.markEdgeActive(edge, activeEdges);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------
    public String extractString(Map<String, Object> data, String key) {
        if (data == null) return null;
        Object val = data.get(key);
        return val != null ? String.valueOf(val) : null;
    }
}
