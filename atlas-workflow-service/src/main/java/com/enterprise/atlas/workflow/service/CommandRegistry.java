package com.enterprise.atlas.workflow.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CommandRegistry {

    private final Map<String, WorkflowCommand> registry = new ConcurrentHashMap<>();

    @Autowired
    public CommandRegistry(List<WorkflowCommand> commands) {
        for (WorkflowCommand command : commands) {
            registry.put(command.getCommandType().toUpperCase(), command);
        }
        
        // Register synonyms/aliases
        mapAlias("START_WORKFLOW", "START_CHILD_WORKFLOW");
        mapAlias("PUBLISH_EVENT", "EMIT_EVENT");
    }

    private void mapAlias(String alias, String targetType) {
        WorkflowCommand cmd = registry.get(targetType.toUpperCase());
        if (cmd != null) {
            registry.put(alias.toUpperCase(), cmd);
        }
    }

    /**
     * Resolves a command strategy by its technical type.
     */
    public Optional<WorkflowCommand> getCommand(String type) {
        if (type == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(registry.get(type.toUpperCase()));
    }
}
