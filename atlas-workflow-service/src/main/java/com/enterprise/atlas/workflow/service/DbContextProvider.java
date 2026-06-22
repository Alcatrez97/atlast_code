package com.enterprise.atlas.workflow.service;

import com.enterprise.atlas.workflow.entity.ContextField;
import com.enterprise.atlas.workflow.entity.IntegrationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DbContextProvider implements ContextProvider {

    private static final Logger log = LoggerFactory.getLogger(DbContextProvider.class);
    private static final Pattern PARAM_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    @Autowired
    private JdbcTemplate jdbcTemplate; // default spring datasource

    @Override
    public Object resolve(ContextField field, IntegrationRegistry integration, Map<String, Object> currentContext) {
        try {
            String sqlTemplate = integration.getRequestTemplate();
            if (sqlTemplate == null || sqlTemplate.isBlank()) {
                throw new IllegalArgumentException("SQL Request template is empty for database integration: " + integration.getIntegrationKey());
            }

            // Extract placeholders and compile query to parameterized form with '?'
            List<Object> args = new ArrayList<>();
            Matcher matcher = PARAM_PATTERN.matcher(sqlTemplate);
            StringBuilder sqlBuilder = new StringBuilder();
            while (matcher.find()) {
                String key = matcher.group(1).trim();
                Object val = currentContext.get(key);
                args.add(val);
                matcher.appendReplacement(sqlBuilder, "?");
            }
            matcher.appendTail(sqlBuilder);
            String compiledSql = sqlBuilder.toString();

            log.info("Executing database resolution query: {} with args: {}", compiledSql, args);

            // Execute query and retrieve single value
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(compiledSql, args.toArray());
            if (rows.isEmpty()) {
                log.warn("Database query returned 0 rows for key '{}'", field.getFieldKey());
                return null;
            }

            Map<String, Object> firstRow = rows.get(0);
            
            // Map column value
            String mapping = field.getResponseMapping();
            Object rawValue;
            if (mapping != null && !mapping.isBlank()) {
                // Find column matching name/alias
                rawValue = firstRow.get(mapping);
                if (rawValue == null) {
                    // Try case-insensitive matching
                    rawValue = firstRow.entrySet().stream()
                            .filter(e -> e.getKey().equalsIgnoreCase(mapping))
                            .map(Map.Entry::getValue)
                            .findFirst()
                            .orElse(null);
                }
            } else {
                // Take first column value from first row
                rawValue = firstRow.values().iterator().next();
            }

            return castToType(rawValue, field.getFieldType());
        } catch (Exception ex) {
            log.error("Database resolution failed for field '{}': {}", field.getFieldKey(), ex.getMessage());
            throw new RuntimeException("Database integration resolution failed: " + ex.getMessage(), ex);
        }
    }

    private Object castToType(Object value, String type) {
        if (value == null) return null;
        String str = String.valueOf(value).trim();
        switch (type.toUpperCase()) {
            case "NUMBER":
                try {
                    return Double.parseDouble(str);
                } catch (NumberFormatException e) {
                    return value;
                }
            case "BOOLEAN":
                return Boolean.parseBoolean(str);
            default:
                return str;
        }
    }
}
