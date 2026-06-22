package com.enterprise.atlas.workflow.service;

import com.enterprise.atlas.common.dto.ContextFieldDto;
import com.enterprise.atlas.common.dto.ContextSchemaDto;
import com.enterprise.atlas.common.dto.ValidationResultDto;
import com.enterprise.atlas.workflow.entity.ContextField;
import com.enterprise.atlas.workflow.entity.ContextSchema;
import com.enterprise.atlas.workflow.repository.ContextFieldRepository;
import com.enterprise.atlas.workflow.repository.ContextSchemaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class ContextSchemaService {

    @Autowired
    private ContextSchemaRepository schemaRepository;

    @Autowired
    private ContextFieldRepository fieldRepository;

    @Transactional(readOnly = true)
    public List<ContextSchemaDto> getAllSchemas() {
        return schemaRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<ContextSchemaDto> getSchemaByKey(String workflowKey) {
        return schemaRepository.findByWorkflowKey(workflowKey).map(this::toDto);
    }

    public ContextSchemaDto createOrUpdateSchema(ContextSchemaDto dto) {
        Optional<ContextSchema> existingOpt = schemaRepository.findByWorkflowKey(dto.getWorkflowKey());
        ContextSchema schema;
        
        if (existingOpt.isPresent()) {
            schema = existingOpt.get();
            schema.setName(dto.getName());
            schema.setDescription(dto.getDescription());
            schema.setUpdatedAt(LocalDateTime.now());
            // Clear existing fields to replace them
            schema.getFields().clear();
        } else {
            schema = new ContextSchema();
            schema.setId(UUID.randomUUID().toString());
            schema.setWorkflowKey(dto.getWorkflowKey());
            schema.setName(dto.getName());
            schema.setDescription(dto.getDescription());
        }

        // Save schema first to get transactional context
        schema = schemaRepository.save(schema);

        // Map and save new fields
        List<ContextField> fields = new ArrayList<>();
        if (dto.getFields() != null) {
            int order = 0;
            for (ContextFieldDto fieldDto : dto.getFields()) {
                ContextField field = new ContextField();
                field.setId(UUID.randomUUID().toString());
                field.setSchema(schema);
                field.setFieldKey(fieldDto.getFieldKey());
                field.setDisplayName(fieldDto.getDisplayName());
                field.setFieldType(fieldDto.getFieldType());
                field.setRequired(fieldDto.isRequired());
                field.setDefaultValue(fieldDto.getDefaultValue());
                field.setDescription(fieldDto.getDescription());
                field.setFieldOrder(order++);
                
                // Sprint 7 extensions
                field.setIntegrationId(fieldDto.getIntegrationId());
                field.setResponseMapping(fieldDto.getResponseMapping());
                field.setCacheable(fieldDto.isCacheable());
                field.setTtlSeconds(fieldDto.getTtlSeconds());
                field.setCost(fieldDto.getCost() != null ? fieldDto.getCost() : "LOW");
                field.setExpression(fieldDto.getExpression());
                
                fields.add(field);
            }
        }
        schema.getFields().addAll(fields);
        schema = schemaRepository.save(schema);

        return toDto(schema);
    }

    public void deleteSchema(String id) {
        schemaRepository.deleteById(id);
    }

    public ValidationResultDto validateContext(String workflowKey, Map<String, Object> context) {
        ValidationResultDto result = new ValidationResultDto();
        result.setValid(true);

        Optional<ContextSchema> schemaOpt = schemaRepository.findByWorkflowKey(workflowKey);
        if (!schemaOpt.isPresent()) {
            result.addWarning("No context schema defined for workflow key: " + workflowKey);
            return result;
        }

        ContextSchema schema = schemaOpt.get();
        Set<String> schemaFieldKeys = new HashSet<>();

        for (ContextField field : schema.getFields()) {
            String key = field.getFieldKey();
            schemaFieldKeys.add(key);
            Object value = context.get(key);

            if (value == null) {
                // Check default value
                if (field.getDefaultValue() != null && !field.getDefaultValue().trim().isEmpty()) {
                    try {
                        Object typedDefault = parseValue(field.getDefaultValue(), field.getFieldType());
                        context.put(key, typedDefault);
                        result.addWarning("Field '" + key + "' was missing; populated with default value: " + field.getDefaultValue());
                    } catch (Exception e) {
                        result.addError("Field '" + key + "' was missing and default value '" + field.getDefaultValue() + "' failed to parse as type " + field.getFieldType());
                    }
                } else if (field.isRequired()) {
                    result.addError("Required field '" + key + "' is missing.");
                }
            } else {
                // Coerce/cast value if it is a string representation of the expected type
                Object coercedValue = coerceValue(value, field.getFieldType());
                if (coercedValue != value) {
                    context.put(key, coercedValue);
                    value = coercedValue;
                }
                // Validate type of present value
                validateFieldType(key, value, field.getFieldType(), result);
            }
        }

        // Check for unexpected fields in the payload
        for (String key : context.keySet()) {
            if (!schemaFieldKeys.contains(key)) {
                result.addWarning("Payload field '" + key + "' is not defined in the schema.");
            }
        }

        return result;
    }

    private Object parseValue(String rawValue, String type) {
        if (rawValue == null) return null;
        switch (type.toUpperCase()) {
            case "NUMBER":
                return Double.parseDouble(rawValue);
            case "BOOLEAN":
                return Boolean.parseBoolean(rawValue);
            case "DATE":
                return rawValue; // Keep as string or date parser can go here
            default:
                return rawValue;
        }
    }

    private Object coerceValue(Object value, String type) {
        if (value == null) return null;
        if ("NUMBER".equalsIgnoreCase(type)) {
            if (value instanceof String) {
                try {
                    return Double.parseDouble((String) value);
                } catch (NumberFormatException e) {
                    return value;
                }
            }
        } else if ("BOOLEAN".equalsIgnoreCase(type)) {
            if (value instanceof String) {
                String sVal = ((String) value).toLowerCase().trim();
                if ("true".equals(sVal)) return Boolean.TRUE;
                if ("false".equals(sVal)) return Boolean.FALSE;
            }
        }
        return value;
    }

    private void validateFieldType(String key, Object value, String type, ValidationResultDto result) {
        switch (type.toUpperCase()) {
            case "NUMBER":
                if (!(value instanceof Number)) {
                    if (value instanceof String) {
                        try {
                            Double.parseDouble((String) value);
                        } catch (NumberFormatException e) {
                            result.addError("Field '" + key + "' expected type NUMBER, but string '" + value + "' cannot be parsed as a number.");
                        }
                    } else {
                        result.addError("Field '" + key + "' expected type NUMBER, but got " + value.getClass().getSimpleName());
                    }
                }
                break;
            case "BOOLEAN":
                if (!(value instanceof Boolean)) {
                    if (value instanceof String) {
                        String sVal = ((String) value).toLowerCase();
                        if (!"true".equals(sVal) && !"false".equals(sVal)) {
                            result.addError("Field '" + key + "' expected type BOOLEAN, but string '" + value + "' is not a valid boolean.");
                        }
                    } else {
                        result.addError("Field '" + key + "' expected type BOOLEAN, but got " + value.getClass().getSimpleName());
                    }
                }
                break;
            case "DATE":
                if (!(value instanceof java.util.Date) && !(value instanceof java.time.temporal.Temporal)) {
                    if (value instanceof String) {
                        try {
                            // Validate standard ISO formats
                            java.time.Instant.parse((String) value);
                        } catch (DateTimeParseException e1) {
                            try {
                                java.time.LocalDate.parse((String) value);
                            } catch (DateTimeParseException e2) {
                                try {
                                    java.time.LocalDateTime.parse((String) value);
                                } catch (DateTimeParseException e3) {
                                    result.addError("Field '" + key + "' expected type DATE (ISO format), but string '" + value + "' cannot be parsed.");
                                }
                            }
                        }
                    } else {
                        result.addError("Field '" + key + "' expected type DATE, but got " + value.getClass().getSimpleName());
                    }
                }
                break;
            default:
                // STRING or other type matches anything
                break;
        }
    }

    private ContextSchemaDto toDto(ContextSchema schema) {
        if (schema == null) return null;
        ContextSchemaDto dto = new ContextSchemaDto();
        dto.setId(schema.getId());
        dto.setWorkflowKey(schema.getWorkflowKey());
        dto.setName(schema.getName());
        dto.setDescription(schema.getDescription());
        dto.setCreatedAt(schema.getCreatedAt());
        dto.setUpdatedAt(schema.getUpdatedAt());
        
        if (schema.getFields() != null) {
            dto.setFields(schema.getFields().stream()
                    .map(this::toDto)
                    .collect(Collectors.toList()));
        }
        return dto;
    }

    private ContextFieldDto toDto(ContextField field) {
        if (field == null) return null;
        ContextFieldDto dto = new ContextFieldDto();
        dto.setId(field.getId());
        dto.setSchemaId(field.getSchema().getId());
        dto.setFieldKey(field.getFieldKey());
        dto.setDisplayName(field.getDisplayName());
        dto.setFieldType(field.getFieldType());
        dto.setRequired(field.isRequired());
        dto.setDefaultValue(field.getDefaultValue());
        dto.setDescription(field.getDescription());
        dto.setFieldOrder(field.getFieldOrder());
        
        // Sprint 7 extensions
        dto.setIntegrationId(field.getIntegrationId());
        dto.setResponseMapping(field.getResponseMapping());
        dto.setCacheable(field.isCacheable());
        dto.setTtlSeconds(field.getTtlSeconds());
        dto.setCost(field.getCost());
        dto.setExpression(field.getExpression());
        
        return dto;
    }
}
