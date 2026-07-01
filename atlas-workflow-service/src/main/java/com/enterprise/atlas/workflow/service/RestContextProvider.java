package com.enterprise.atlas.workflow.service;

import com.enterprise.atlas.workflow.entity.ContextField;
import com.enterprise.atlas.workflow.entity.IntegrationRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RestContextProvider implements ContextProvider {

    private static final Logger log = LoggerFactory.getLogger(RestContextProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern PARAM_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    @Override
    public Object resolve(ContextField field, IntegrationRegistry integration, Map<String, Object> currentContext) {
        try {
            // Configure RestTemplate with timeouts
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            int timeout = integration.getTimeoutMs() != null ? integration.getTimeoutMs() : 5000;
            factory.setConnectTimeout(timeout);
            factory.setReadTimeout(timeout);
            RestTemplate restTemplate = new RestTemplate(factory);

            // Interpolate URL
            String url = interpolate(integration.getEndpointUrl(), currentContext);
            log.info("Resolving variable '{}' via REST call to: {}", field.getFieldKey(), url);

            // Set up headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (integration.getHeadersJson() != null && !integration.getHeadersJson().isBlank()) {
                String headersStr = interpolate(integration.getHeadersJson(), currentContext);
                try {
                    Map<String, String> headerMap = MAPPER.readValue(headersStr, Map.class);
                    for (Map.Entry<String, String> entry : headerMap.entrySet()) {
                        headers.set(entry.getKey(), entry.getValue());
                    }
                } catch (Exception ex) {
                    log.warn("Failed to parse headers JSON for integration '{}': {}", integration.getIntegrationKey(), ex.getMessage());
                }
            }

            // Interpolate body
            String body = null;
            if ("POST".equalsIgnoreCase(integration.getMethod()) && integration.getRequestTemplate() != null) {
                body = interpolate(integration.getRequestTemplate(), currentContext);
            }

            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            HttpMethod httpMethod = HttpMethod.valueOf(integration.getMethod().toUpperCase());

            ResponseEntity<String> response = restTemplate.exchange(url, httpMethod, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String responseBody = response.getBody();
                
                // Parse response body
                Object parsed = MAPPER.readValue(responseBody, Object.class);
                
                // Perform mapping extraction
                String mapping = field.getResponseMapping();
                if (mapping == null || mapping.isBlank()) {
                    return parsed; // Return raw parsed body (e.g., if response is just a string/number)
                }

                Object extracted = extractByPath(parsed, mapping);
                log.debug("Extracted value from path '{}' for field '{}': {}", mapping, field.getFieldKey(), extracted);
                return castToType(extracted, field.getFieldType());
            } else {
                throw new RuntimeException("HTTP call failed with status code: " + response.getStatusCode());
            }
        } catch (Exception ex) {
            log.error("REST resolution failed for field '{}': {}", field.getFieldKey(), ex.getMessage());
            throw new RuntimeException("REST integration resolution failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Interpolates placeholders of format {{variable_key}} using currentContext values.
     */
    private String interpolate(String template, Map<String, Object> context) {
        if (template == null) return null;
        Matcher matcher = PARAM_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            Object value = context.get(key);
            String replacement = value != null ? String.valueOf(value) : "";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Resolves dotted-paths from a parsed map structure (e.g. "customer.profile.age").
     */
    private Object extractByPath(Object root, String path) {
        if (root == null || path == null || path.isBlank()) return root;
        Object current = root;
        String[] parts = path.split("\\.");
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null;
            }
        }
        return current;
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
