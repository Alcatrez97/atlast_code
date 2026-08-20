package com.vi.atlas.workflow.service.traversal.node;

import com.vi.atlas.common.dto.StepRecordDto;
import com.vi.atlas.common.dto.WorkflowEdgeDto;
import com.vi.atlas.common.dto.WorkflowNodeDto;
import com.vi.atlas.workflow.service.traversal.EdgeSelector;
import com.vi.atlas.workflow.service.traversal.NodeExecutionResult;
import com.vi.atlas.workflow.service.traversal.SpelEvaluator;
import com.vi.atlas.workflow.service.traversal.TaskRecorder;
import com.vi.atlas.workflow.service.traversal.TraversalExecutionState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Executes a DECISION node.
 *
 * <p>Reads a context field (or evaluates a SpEL expression) and routes to the
 * outgoing edge whose condition matches the resolved value.
 */
@Component
public class DecisionNodeExecutor implements NodeExecutor {

    @Autowired private TaskRecorder  taskRecorder;
    @Autowired private SpelEvaluator spel;
    @Autowired private EdgeSelector  edgeSelector;

    @Override
    public NodeExecutionResult execute(WorkflowNodeDto node,
                                       TraversalExecutionState state,
                                       StepRecordDto step,
                                       List<StepRecordDto> trace) {
        var ti = taskRecorder.recordTaskStart(state.instance, node, state.context);

        String fieldKey      = extractString(node.getData(), "fieldKey");
        Object fieldValue    = null;
        String expressionMeta = null;

        if (fieldKey != null && !fieldKey.isBlank()) {
            // SpEL expression path vs simple context key
            if (fieldKey.contains(".") || fieldKey.contains("[") || fieldKey.contains("'")) {
                try {
                    fieldValue = spel.evaluate(fieldKey, state.spelCtx);
                } catch (Exception e) {
                    fieldValue = state.context.get(fieldKey);
                }
                expressionMeta = fieldKey;
            } else {
                fieldValue     = state.context.get(fieldKey);
                expressionMeta = "context['" + fieldKey + "']";
            }
            step.setNotes("Decision on context field: " + fieldKey + " = '" + fieldValue + "'");
        }

        step.setStatus("ROUTED");
        step.setExpression(expressionMeta);
        step.setExpressionResult(fieldValue);
        taskRecorder.recordTaskCompletion(
                ti, Map.of("fieldValue", fieldValue != null ? fieldValue : ""), "COMPLETED");

        List<WorkflowEdgeDto> outEdges = state.edgesBySource.getOrDefault(node.getId(), List.of());
        if (!outEdges.isEmpty()) {
            WorkflowEdgeDto chosen = edgeSelector.matchDecisionEdge(outEdges, fieldValue, state.spelCtx);
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
