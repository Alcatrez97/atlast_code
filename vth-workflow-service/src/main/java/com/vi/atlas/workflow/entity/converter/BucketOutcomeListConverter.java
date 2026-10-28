package com.vi.atlas.workflow.entity.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@Converter(autoApply = true)
public class BucketOutcomeListConverter implements AttributeConverter<List<com.vi.atlas.common.dto.BucketOutcomeDto>, String> {

    private static final Logger log = LoggerFactory.getLogger(BucketOutcomeListConverter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<com.vi.atlas.common.dto.BucketOutcomeDto> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            log.error("Error serializing List<BucketOutcomeDto> to JSON", e);
            throw new IllegalArgumentException("Error serializing bucket outcomes", e);
        }
    }

    @Override
    public List<com.vi.atlas.common.dto.BucketOutcomeDto> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(dbData, objectMapper.getTypeFactory().constructCollectionType(List.class, com.vi.atlas.common.dto.BucketOutcomeDto.class));
        } catch (JsonProcessingException e) {
            log.error("Error deserializing JSON to List<BucketOutcomeDto>", e);
            throw new IllegalArgumentException("Error deserializing bucket outcomes", e);
        }
    }
}
