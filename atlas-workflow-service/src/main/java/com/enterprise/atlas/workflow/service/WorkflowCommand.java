package com.enterprise.atlas.workflow.service;

import java.util.Map;

public interface WorkflowCommand {
    /**
     * Technical name of the command strategy (e.g. "REST", "MQ", "START_CHILD_WORKFLOW").
     */
    String getCommandType();

    /**
     * Executes the custom logic associated with this command type.
     *
     * @param input mapped data from global context and static node attributes.
     * @return the execution response payload, or an empty map/null if fire-and-forget.
     * @throws Exception if execution fails
     */
    Map<String, Object> execute(Map<String, Object> input) throws Exception;
}
