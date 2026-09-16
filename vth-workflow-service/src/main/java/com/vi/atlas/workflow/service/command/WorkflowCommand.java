package com.vi.atlas.workflow.service.command;

import java.util.Map;

public interface WorkflowCommand {
    /**
     * Technical name of the command strategy (e.g. "REST", "MQ", "START_CHILD_WORKFLOW").
     */
    String getCommandType();

    /**
     * Returns true if this command performs remote network / I/O operations (HTTP REST, Kafka MQ, SMS),
     * signaling to the engine that execution should be decoupled from database transaction boundaries.
     */
    default boolean isExternalIo() {
        return false;
    }

    /**
     * Human-readable display label for designer UI.
     */
    default String getDisplayName() {
        String type = getCommandType();
        if (type == null) return "Command";
        return type.replace('_', ' ');
    }

    /**
     * Description of the command function.
     */
    default String getDescription() {
        return "";
    }

    /**
     * Classification category (e.g. "Integration", "Lifecycle", "Workflow", "Event", "Messaging", "Custom").
     */
    default String getCategory() {
        return "General";
    }

    /**
     * Descriptors for configurable parameters.
     */
    default java.util.List<com.vi.atlas.workflow.dto.CommandParameterDto> getParameters() {
        return java.util.Collections.emptyList();
    }

    /**
     * Executes the custom logic associated with this command type.
     *
     * @param input mapped data from global context and static node attributes.
     * @return the execution response payload, or an empty map/null if fire-and-forget.
     * @throws Exception if execution fails
     */
    Map<String, Object> execute(Map<String, Object> input) throws Exception;

    /**
     * Helper to map input parameters from a command's input map to a target map (e.g. child workflow input).
     */
    default void applyInputMapping(Map<String, Object> input, Map<String, Object> targetMap) {
        CommandUtils.applyInputMapping(input, targetMap);
    }

    /**
     * Helper to map payload expressions into a target map.
     */
    default void applyPayloadOrSpelMapping(Object mappingObj, Map<String, Object> input, Map<String, Object> targetMap) {
        CommandUtils.applyPayloadOrSpelMapping(mappingObj, input, targetMap);
    }

    /**
     * Helper to extract cleaned payload map excluding framework and metadata keys.
     */
    default Map<String, Object> extractCleanPayload(Map<String, Object> input, String... additionalExcludedKeys) {
        return CommandUtils.extractPayload(input, additionalExcludedKeys);
    }

    /**
     * Extracts first matching non-blank string value for given keys.
     */
    default String getStringParam(Map<String, Object> input, String... keys) {
        return CommandUtils.getStringParam(input, keys);
    }
}
