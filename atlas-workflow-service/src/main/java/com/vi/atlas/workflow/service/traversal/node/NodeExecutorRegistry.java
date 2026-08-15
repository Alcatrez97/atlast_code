package com.vi.atlas.workflow.service.traversal.node;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry that maps workflow node-type strings to their {@link NodeExecutor}
 * implementations.
 *
 * <p>The traversal loop calls {@link #get(String)} to obtain the correct executor
 * instead of the original {@code switch} block, making it trivially easy to add
 * new node types by implementing {@link NodeExecutor} and registering it here.
 */
@Component
public class NodeExecutorRegistry {

    private final Map<String, NodeExecutor> executors;

    @Autowired
    public NodeExecutorRegistry(
            StartNodeExecutor        start,
            EndNodeExecutor          end,
            RuleNodeExecutor         rule,
            DecisionNodeExecutor     decision,
            BucketNodeExecutor       bucket,
            WaitEventNodeExecutor    waitEvent,
            SubWorkflowNodeExecutor  subWorkflow,
            CommandNodeExecutor      command,
            TimerNodeExecutor        timer,
            ParallelNodeExecutor     parallel,
            JoinNodeExecutor         join,
            DefaultNodeExecutor      defaultExec) {

        executors = Map.ofEntries(
                Map.entry("START",        start),
                Map.entry("END",          end),
                Map.entry("RULE",         rule),
                Map.entry("DECISION",     decision),
                Map.entry("BUCKET",       bucket),
                Map.entry("WAIT_EVENT",   waitEvent),
                Map.entry("SUB_WORKFLOW", subWorkflow),
                Map.entry("COMMAND",      command),
                Map.entry("TIMER",        timer),
                Map.entry("PARALLEL",     parallel),
                Map.entry("JOIN",         join),
                Map.entry("DEFAULT",      defaultExec)
        );
    }

    /**
     * Returns the executor for the given node type (case-insensitive).
     * Falls back to {@link DefaultNodeExecutor} for unknown types.
     *
     * @param nodeType the node type string, e.g. {@code "RULE"}, {@code "BUCKET"}
     * @return the corresponding executor, never {@code null}
     */
    public NodeExecutor get(String nodeType) {
        NodeExecutor exec = executors.get(nodeType != null ? nodeType.toUpperCase() : "DEFAULT");
        return exec != null ? exec : executors.get("DEFAULT");
    }
}
