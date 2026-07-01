package com.enterprise.atlas.workflow.controller;

import com.enterprise.atlas.common.dto.IntegrationRegistryDto;
import com.enterprise.atlas.workflow.entity.IntegrationRegistry;
import com.enterprise.atlas.workflow.repository.IntegrationRegistryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/integrations")
@CrossOrigin(origins = "*")
@Tag(name = "Integration Registry API", description = "Operations to manage external REST, Database, or Config source connector profiles for dynamic context resolution")
public class IntegrationRegistryController {

    @Autowired
    private IntegrationRegistryRepository repository;

    @GetMapping
    @Operation(summary = "Get all integrations", description = "Retrieves all registered external REST, DB, and CONFIG integrations")
    public List<IntegrationRegistryDto> getAll() {
        return repository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get integration by database ID", description = "Retrieves the integration details by database UUID")
    public ResponseEntity<IntegrationRegistryDto> getById(@PathVariable String id) {
        return repository.findById(id)
                .map(entity -> ResponseEntity.ok(toDto(entity)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/key/{key}")
    @Operation(summary = "Get integration by business key", description = "Retrieves the integration details by its business lookup key")
    public ResponseEntity<IntegrationRegistryDto> getByKey(@PathVariable String key) {
        return repository.findByIntegrationKey(key)
                .map(entity -> ResponseEntity.ok(toDto(entity)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create or update integration", description = "Saves a new integration registry details or updates an existing mapping config")
    public IntegrationRegistryDto save(@RequestBody IntegrationRegistryDto dto) {
        IntegrationRegistry entity;
        if (dto.getId() != null && !dto.getId().isBlank()) {
            entity = repository.findById(dto.getId())
                    .orElseGet(() -> {
                        IntegrationRegistry newEnt = new IntegrationRegistry();
                        newEnt.setId(dto.getId());
                        return newEnt;
                    });
        } else {
            entity = new IntegrationRegistry();
            entity.setId(UUID.randomUUID().toString());
        }

        entity.setIntegrationKey(dto.getIntegrationKey());
        entity.setName(dto.getName());
        entity.setProviderType(dto.getProviderType());
        entity.setEndpointUrl(dto.getEndpointUrl());
        entity.setMethod(dto.getMethod());
        entity.setHeadersJson(dto.getHeadersJson());
        entity.setRequestTemplate(dto.getRequestTemplate());
        entity.setTimeoutMs(dto.getTimeoutMs() != null ? dto.getTimeoutMs() : 5000);

        entity = repository.save(entity);
        return toDto(entity);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete integration", description = "Deletes an integration configuration from the registry by its UUID")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (repository.existsById(id)) {
            repository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    private IntegrationRegistryDto toDto(IntegrationRegistry entity) {
        IntegrationRegistryDto dto = new IntegrationRegistryDto();
        dto.setId(entity.getId());
        dto.setIntegrationKey(entity.getIntegrationKey());
        dto.setName(entity.getName());
        dto.setProviderType(entity.getProviderType());
        dto.setEndpointUrl(entity.getEndpointUrl());
        dto.setMethod(entity.getMethod());
        dto.setHeadersJson(entity.getHeadersJson());
        dto.setRequestTemplate(entity.getRequestTemplate());
        dto.setTimeoutMs(entity.getTimeoutMs());
        return dto;
    }
}
