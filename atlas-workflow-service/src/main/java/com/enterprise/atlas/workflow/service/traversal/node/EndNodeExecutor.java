package com.enterprise.atlas.workflow.service.traversal.node;

import com.enterprise.atlas.common.dto.StepRecordDto;
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

/** Executes an END node — records the terminal step and stops traversal. */
@Component
public class EndNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(EndNodeExecutor.class);

    @Autowired private TaskRecorder taskRecorder;

    @Override
    public NodeExecutionResult execute(WorkflowNodeDto node,
                                       TraversalExecutionState state,
                                       StepRecordDto step,
                                       List<StepRecordDto> trace) {
        var ti = taskRecorder.recordTaskStart(state.instance, node, state.context);
        step.setStatus("COMPLETED");
        step.setNotes("Workflow execution reached END node.");
        taskRecorder.recordTaskCompletion(ti, Map.of(), "COMPLETED");
        log.info("Reached END node ID: {}, Label: {}", node.getId(), node.getLabel());
        return NodeExecutionResult.COMPLETED;   // nextNode = null, not suspended
    }
}
