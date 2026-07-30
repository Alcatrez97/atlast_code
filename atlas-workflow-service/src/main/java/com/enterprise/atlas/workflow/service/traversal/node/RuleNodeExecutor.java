package com.enterprise.atlas.workflow.service.traversal.node;

import com.enterprise.atlas.common.dto.StepRecordDto;
import com.enterprise.atlas.common.dto.WorkflowEdgeDto;
import com.enterprise.atlas.common.dto.WorkflowNodeDto;
import com.enterprise.atlas.workflow.entity.Rule;
import com.enterprise.atlas.workflow.repository.RuleRepository;
import com.enterprise.atlas.workflow.service.traversal.EdgeSelector;
import com.enterprise.atlas.workflow.service.traversal.NodeExecutionResult;
import com.enterprise.atlas.workflow.service.traversal.SpelEvaluator;
import com.enterprise.atlas.workflow.service.traversal.TaskRecorder;
import com.enterprise.atlas.workflow.service.traversal.TraversalExecutionState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Executes a RULE node.
 *
 * <p>Resolves the expression (inline or from the rule registry), evaluates it,
 * then selects the outgoing edge whose condition matches the boolean result.
 */
@Component
public class RuleNodeExecutor implements NodeExecutor {

    @Autowired private TaskRecorder  taskRecorder;
    @Autowired private SpelEvaluator spel;
    @Autowired private EdgeSelector  edgeSelector;
    @Autowired private RuleRepository ruleRepository;

    @Override
    public NodeExecutionResult execute(WorkflowNodeDto node,
                                       TraversalExecutionState state,
                                       StepRecordDto step,
                                       List<StepRecordDto> trace) {
        var ti = taskRecorder.recordTaskStart(state.instance, node, state.context);

        String expression = extractString(node.getData(), "expression");
        String ruleId     = extractString(node.getData(), "ruleId");

        // Resolve expression from rule registry if inline expression is missing
        if ((expression == null || expression.isBlank()) && ruleId != null && !ruleId.isBlank()) {
            Optional<Rule> ruleOpt = ruleRepository.findByRuleKey(ruleId);
            if (ruleOpt.isPresent() && ruleOpt.get().isActive()) {
                expression = ruleOpt.get().getExpression();
                step.setNotes("Resolved registry rule [" + ruleId + "]: " + ruleOpt.get().getName());
            } else {
                step.setStatus("FAILED");
                step.setNotes(ruleOpt.isEmpty()
                        ? "Rule reference '" + ruleId + "' not found in registry."
                        : "Rule reference '" + ruleId + "' is inactive in registry.");
                taskRecorder.recordTaskCompletion(ti, Map.of(), "FAILED");
                return NodeExecutionResult.failed();
            }
        }

        boolean ruleResult = false;
        if (expression != null && !expression.isBlank()) {
            try {
                Object resultObj = spel.evaluate(expression, state.spelCtx);
                ruleResult = Boolean.TRUE.equals(resultObj);
                step.setNotes("Evaluated expression: " + expression + " -> " + ruleResult);
            } catch (Exception e) {
                step.setStatus("FAILED");
                step.setNotes("SpEL Evaluation Error on expression '" + expression + "': " + e.getMessage());
                taskRecorder.recordTaskCompletion(ti, Map.of("error", e.getMessage()), "FAILED");
                return NodeExecutionResult.failed();
            }
        } else {
            step.setNotes("RULE node has no rule expression configured.");
        }

        step.setStatus("COMPLETED");
        step.setExpression(expression);
        step.setExpressionResult(ruleResult);
        taskRecorder.recordTaskCompletion(ti, Map.of("expressionResult", ruleResult), "COMPLETED");

        // Select outgoing edge based on rule result
        List<WorkflowEdgeDto> outEdges = state.edgesBySource.getOrDefault(node.getId(), List.of());
        if (!outEdges.isEmpty()) {
            WorkflowEdgeDto chosen = edgeSelector.chooseEdge(outEdges, state.spelCtx, ruleResult);
            if (chosen != null) {
                return NodeExecutionResult.next(state.nodeMap.get(chosen.getTarget()), chosen.getId());
            }
        }
        return NodeExecutionResult.COMPLETED;
    }

    private String extractString(Map<String, Object> data, String key) {
        if (data == null) return null;
        Object val = data.get(key);
        return val != null ? String.valueOf(val) : null;
    }
}
