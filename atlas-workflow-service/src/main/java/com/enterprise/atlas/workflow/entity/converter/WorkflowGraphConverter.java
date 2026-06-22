package com.enterprise.atlas.workflow.entity.converter;

import com.enterprise.atlas.common.dto.WorkflowGraphDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter(autoApply = true)
public class WorkflowGraphConverter implements AttributeConverter<WorkflowGraphDto, String> {

    private static final Logger log = LoggerFactory.getLogger(WorkflowGraphConverter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(WorkflowGraphDto attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            log.error("Error serializing WorkflowGraphDto to JSON", e);
            throw new IllegalArgumentException("Error serializing graph definition", e);
        }
    }

    @Override
    public WorkflowGraphDto convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return new WorkflowGraphDto();
        }
        try {
            return objectMapper.readValue(dbData, WorkflowGraphDto.class);
        } catch (JsonProcessingException e) {
            log.error("Error deserializing JSON to WorkflowGraphDto", e);
            throw new IllegalArgumentException("Error deserializing graph definition", e);
        }
    }
}
