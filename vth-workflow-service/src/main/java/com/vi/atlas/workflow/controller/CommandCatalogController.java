package com.vi.atlas.workflow.controller;

import com.vi.atlas.workflow.dto.CommandMetadataDto;
import com.vi.atlas.workflow.service.command.CommandRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/commands")
@CrossOrigin(origins = "*")
@Tag(name = "Command Catalog API", description = "Endpoints to dynamically discover available workflow command strategies and parameter schemas")
public class CommandCatalogController {

    private final CommandRegistry commandRegistry;

    @Autowired
    public CommandCatalogController(CommandRegistry commandRegistry) {
        this.commandRegistry = commandRegistry;
    }

    @GetMapping
    @Operation(summary = "Get all registered commands", description = "Retrieves metadata and parameter descriptors for all commands registered in the engine")
    public List<CommandMetadataDto> getAllCommands() {
        return commandRegistry.getCommandCatalog();
    }
}
