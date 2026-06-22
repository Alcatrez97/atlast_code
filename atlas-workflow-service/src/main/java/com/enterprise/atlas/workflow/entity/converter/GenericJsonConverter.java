package com.enterprise.atlas.workflow.entity.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reusable JSON↔String converters for generic Map and List structures stored as CLOBs.
 */
public class GenericJsonConverter {

    private static final Logger log = LoggerFactory.getLogger(GenericJsonConverter.class);
    private static final ObjectMapper MAPPER;

    static {
        MAPPER = new ObjectMapper();
        MAPPER.registerModule(new JavaTimeModule());
    }

    /** Converts Map<String,Object> ↔ JSON CLOB */
    @Converter(autoApply = false)
    public static class MapConverter implements AttributeConverter<Map<String, Object>, String> {

        @Override
        public String convertToDatabaseColumn(Map<String, Object> attribute) {
            if (attribute == null) return null;
            try {
                return MAPPER.writeValueAsString(attribute);
            } catch (JsonProcessingException e) {
                log.error("Error serializing Map to JSON", e);
                return "{}";
            }
        }

        @Override
        public Map<String, Object> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.trim().isEmpty()) return new HashMap<>();
            try {
                return MAPPER.readValue(dbData, new TypeReference<Map<String, Object>>() {});
            } catch (JsonProcessingException e) {
                log.error("Error deserializing JSON to Map", e);
                return new HashMap<>();
            }
        }
    }

    /** Converts List<Map<String,Object>> ↔ JSON CLOB */
    @Converter(autoApply = false)
    public static class ListConverter implements AttributeConverter<List<Map<String, Object>>, String> {

        @Override
        public String convertToDatabaseColumn(List<Map<String, Object>> attribute) {
            if (attribute == null) return null;
            try {
                return MAPPER.writeValueAsString(attribute);
            } catch (JsonProcessingException e) {
                log.error("Error serializing List to JSON", e);
                return "[]";
            }
        }

        @Override
        public List<Map<String, Object>> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.trim().isEmpty()) return List.of();
            try {
                return MAPPER.readValue(dbData, new TypeReference<List<Map<String, Object>>>() {});
            } catch (JsonProcessingException e) {
                log.error("Error deserializing JSON to List", e);
                return List.of();
            }
        }
    }
}
