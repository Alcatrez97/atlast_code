package com.vi.atlas.workflow.service.command;

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
        mapAlias("CALL_EXTERNAL_SYSTEM", "REST");
        mapAlias("HTTP", "REST");
        mapAlias("KAFKA", "MQ");
        mapAlias("MQ_PUBLISH", "MQ");
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

    /**
     * Returns full metadata catalog for all registered commands (excluding duplicate aliases).
     */
    public List<com.vi.atlas.workflow.dto.CommandMetadataDto> getCommandCatalog() {
        return registry.values().stream()
                .distinct()
                .map(cmd -> {
                    com.vi.atlas.workflow.dto.CommandMetadataDto dto = new com.vi.atlas.workflow.dto.CommandMetadataDto(
                            cmd.getCommandType(),
                            cmd.getDisplayName(),
                            cmd.getDescription(),
                            cmd.getCategory(),
                            cmd.isExternalIo()
                    );
                    dto.setParameters(cmd.getParameters());
                    return dto;
                })
                .sorted(java.util.Comparator.comparing(com.vi.atlas.workflow.dto.CommandMetadataDto::getCategory)
                        .thenComparing(com.vi.atlas.workflow.dto.CommandMetadataDto::getDisplayName))
                .collect(java.util.stream.Collectors.toList());
    }
}
