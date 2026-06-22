package com.enterprise.atlas.workflow.controller;

import com.enterprise.atlas.common.dto.EventDefinitionDto;
import com.enterprise.atlas.workflow.entity.EventDefinition;
import com.enterprise.atlas.workflow.repository.EventDefinitionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/event-definitions")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
@Tag(name = "Event Definition Registry API", description = "Operations to define, register, update, and manage predefined events")
public class EventDefinitionController {

    @Autowired
    private EventDefinitionRepository eventDefinitionRepository;

    @GetMapping
    @Operation(summary = "Get all event definitions")
    public ResponseEntity<List<EventDefinitionDto>> getAll() {
        List<EventDefinitionDto> list = eventDefinitionRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get event definition by ID")
    public ResponseEntity<EventDefinitionDto> getById(@PathVariable String id) {
        return eventDefinitionRepository.findById(id)
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create event definition")
    public ResponseEntity<EventDefinitionDto> create(@RequestBody EventDefinitionDto dto) {
        if (dto.getEventKey() == null || dto.getEventKey().trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (eventDefinitionRepository.findByEventKey(dto.getEventKey().trim().toUpperCase()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        EventDefinition def = new EventDefinition();
        def.setId(UUID.randomUUID().toString());
        def.setEventKey(dto.getEventKey().trim().toUpperCase());
        def.setName(dto.getName() != null ? dto.getName().trim() : dto.getEventKey());
        def.setDescription(dto.getDescription());
        def.setKafkaTopic(dto.getKafkaTopic());
        def.setCorrelationKeyPath(dto.getCorrelationKeyPath());
        def.setPayloadSchema(dto.getPayloadSchema());
        def.setActive(dto.isActive());

        def = eventDefinitionRepository.save(def);
        return new ResponseEntity<>(toDto(def), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update event definition")
    public ResponseEntity<EventDefinitionDto> update(@PathVariable String id, @RequestBody EventDefinitionDto dto) {
        EventDefinition def = eventDefinitionRepository.findById(id)
                .orElse(null);
        if (def == null) {
            return ResponseEntity.notFound().build();
        }

        def.setEventKey(dto.getEventKey().trim().toUpperCase());
        def.setName(dto.getName() != null ? dto.getName().trim() : dto.getEventKey());
        def.setDescription(dto.getDescription());
        def.setKafkaTopic(dto.getKafkaTopic());
        def.setCorrelationKeyPath(dto.getCorrelationKeyPath());
        def.setPayloadSchema(dto.getPayloadSchema());
        def.setActive(dto.isActive());

        def = eventDefinitionRepository.save(def);
        return ResponseEntity.ok(toDto(def));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete event definition")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        eventDefinitionRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private EventDefinitionDto toDto(EventDefinition def) {
        EventDefinitionDto dto = new EventDefinitionDto();
        dto.setId(def.getId());
        dto.setEventKey(def.getEventKey());
        dto.setName(def.getName());
        dto.setDescription(def.getDescription());
        dto.setKafkaTopic(def.getKafkaTopic());
        dto.setCorrelationKeyPath(def.getCorrelationKeyPath());
        dto.setPayloadSchema(def.getPayloadSchema());
        dto.setActive(def.isActive());
        dto.setCreatedAt(def.getCreatedAt());
        dto.setUpdatedAt(def.getUpdatedAt());
        return dto;
    }
}
