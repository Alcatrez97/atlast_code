package com.vi.atlas.workflow.service.command.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vi.atlas.workflow.dto.CommandParameterDto;
import com.vi.atlas.workflow.entity.IntegrationRegistry;
import com.vi.atlas.workflow.repository.IntegrationRegistryRepository;
import com.vi.atlas.workflow.service.command.WorkflowCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class HttpRestCommand implements WorkflowCommand {

    private static final Logger log = LoggerFactory.getLogger(HttpRestCommand.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}|\\$\\{([^}]+)\\}|\\{([^}]+)\\}");

    @Autowired(required = false)
    private IntegrationRegistryRepository integrationRegistryRepository;

    @Override
    public String getCommandType() {
        return "REST";
    }

    @Override
    public boolean isExternalIo() {
        return true;
    }

    @Override
    public String getDisplayName() {
        return "Call External REST API";
    }

    @Override
    public String getDescription() {
        return "Invokes external HTTP REST endpoints with configurable methods, headers, payload, and response mapping.";
    }

    @Override
    public String getCategory() {
        return "Integration";
    }

    @Override
    public List<CommandParameterDto> getParameters() {
        List<CommandParameterDto> params = new ArrayList<>();

        CommandParameterDto integrationParam = new CommandParameterDto(
                "integrationKey",
                "Integration Profile",
                "select",
                false,
                "",
                "Select a pre-configured connector profile from Integration Registry, or leave blank for a custom endpoint"
        );
        integrationParam.setDataSource("INTEGRATIONS");
        params.add(integrationParam);

        params.add(new CommandParameterDto(
                "url",
                "Endpoint URL",
                "text",
                true,
                "",
                "HTTP URL endpoint. Supports placeholders like ${context.panNumber} or {circleId}"
        ));

        CommandParameterDto methodParam = new CommandParameterDto(
                "method",
                "HTTP Method",
                "select",
                true,
                "GET",
                "HTTP request method"
        );
        methodParam.setOptions(List.of(
                new CommandParameterDto.Option("GET", "GET"),
                new CommandParameterDto.Option("POST", "POST"),
                new CommandParameterDto.Option("PUT", "PUT"),
                new CommandParameterDto.Option("DELETE", "DELETE"),
                new CommandParameterDto.Option("PATCH", "PATCH")
        ));
        params.add(methodParam);

        params.add(new CommandParameterDto(
                "headers",
                "Request Headers (JSON)",
                "json",
                false,
                "{}",
                "JSON map of HTTP request headers (e.g. {\"Authorization\": \"Bearer ...\"})"
        ));

        params.add(new CommandParameterDto(
                "timeoutSeconds",
                "Timeout (Seconds)",
                "number",
                false,
                10,
                "Maximum duration before request times out"
        ));

        params.add(new CommandParameterDto(
                "inputMapping",
                "Request Body Mapping (JSON)",
                "json",
                false,
                "{}",
                "JSON mapping context variables or SpEL expressions into outbound request payload"
        ));

        params.add(new CommandParameterDto(
                "outputMapping",
                "Response Output Mapping (JSON)",
                "json",
                false,
                "{}",
                "JSON mapping response fields into workflow context keys (e.g. {\"status\": \"context.kycStatus\"})"
        ));

        return params;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> input) throws Exception {
        String integrationKey = getStringParam(input, "integrationKey");
        String url = getStringParam(input, "url", "endpointUrl");
        String method = getStringParam(input, "method");
        long timeoutSec = 10;
        Map<String, String> resolvedHeaders = new HashMap<>();

        String regRequestTemplate = null;

        // If integrationKey provided, populate defaults from IntegrationRegistry
        if (integrationKey != null && !integrationKey.isBlank() && integrationRegistryRepository != null) {
            Optional<IntegrationRegistry> regOpt = integrationRegistryRepository.findByIntegrationKey(integrationKey);
            if (regOpt.isPresent()) {
                IntegrationRegistry reg = regOpt.get();
                if ((url == null || url.isBlank()) && reg.getEndpointUrl() != null) {
                    url = reg.getEndpointUrl();
                }
                if ((method == null || method.isBlank()) && reg.getMethod() != null) {
                    method = reg.getMethod();
                }
                if (reg.getRequestTemplate() != null && !reg.getRequestTemplate().isBlank()) {
                    regRequestTemplate = reg.getRequestTemplate();
                }
                if (reg.getTimeoutMs() != null && reg.getTimeoutMs() > 0) {
                    timeoutSec = Math.max(1, reg.getTimeoutMs() / 1000);
                }
                if (reg.getHeadersJson() != null && !reg.getHeadersJson().isBlank()) {
                    try {
                        Map<String, Object> regHeaders = MAPPER.readValue(reg.getHeadersJson(), new TypeReference<Map<String, Object>>() {});
                        for (Map.Entry<String, Object> e : regHeaders.entrySet()) {
                            resolvedHeaders.put(e.getKey(), String.valueOf(e.getValue()));
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse registry headersJson for {}: {}", integrationKey, e.getMessage());
                    }
                }
            } else {
                log.warn("Integration profile '{}' not found in registry. Using inline configuration.", integrationKey);
            }
        }

        if (method == null || method.isBlank()) {
            method = "GET";
        }
        method = method.toUpperCase();

        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("REST command requires a non-blank 'url' or a valid 'integrationKey'.");
        }

        // Variable substitution in URL from context or input
        url = substitutePlaceholders(url, input);

        if (input.containsKey("timeoutSeconds")) {
            timeoutSec = Long.parseLong(String.valueOf(input.get("timeoutSeconds")));
        } else if (input.containsKey("timeout")) {
            timeoutSec = Long.parseLong(String.valueOf(input.get("timeout")));
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSec))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");

        // Merge input custom headers
        Object headersObj = input.get("headers");
        if (headersObj instanceof Map<?, ?> hMap) {
            for (Map.Entry<?, ?> entry : hMap.entrySet()) {
                resolvedHeaders.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        } else if (headersObj instanceof String hStr && !hStr.isBlank()) {
            try {
                Map<String, Object> parsed = MAPPER.readValue(hStr, new TypeReference<Map<String, Object>>() {});
                for (Map.Entry<String, Object> entry : parsed.entrySet()) {
                    resolvedHeaders.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            } catch (Exception e) {
                log.warn("Failed to parse headers string: {}", hStr);
            }
        }

        for (Map.Entry<String, String> entry : resolvedHeaders.entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }

        log.info("Executing HttpRestCommand: method={}, url={}, timeout={}s", method, url, timeoutSec);

        if ("GET".equals(method)) {
            builder.GET();
        } else if ("DELETE".equals(method)) {
            builder.DELETE();
        } else {
            String bodyJson;
            String template = getStringParam(input, "requestTemplate");
            if (template == null || template.isBlank()) {
                template = regRequestTemplate;
            }
            if (template != null && !template.isBlank()) {
                bodyJson = substitutePlaceholders(template, input);
            } else {
                Map<String, Object> payload = extractCleanPayload(input, "url", "method", "headers", "integrationKey", "timeoutSeconds", "timeout", "requestTemplate");
                bodyJson = MAPPER.writeValueAsString(payload);
            }
            log.info("HttpRestCommand request payload: {}", bodyJson);
            builder.method(method, HttpRequest.BodyPublishers.ofString(bodyJson));
        }

        HttpResponse<String> response = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());

        log.info("HttpRestCommand response status: {}", response.statusCode());

        if (response.statusCode() >= 400) {
            throw new RuntimeException("HTTP Command failed with status: " + response.statusCode() + " and body: " + response.body());
        }

        String body = response.body();
        if (body == null || body.isBlank()) {
            return Map.of("statusCode", response.statusCode());
        }

        // Deserialize response body to Map
        try {
            Map<String, Object> respMap = MAPPER.readValue(body, new TypeReference<Map<String, Object>>() {});
            Map<String, Object> result = new HashMap<>(respMap);
            result.putIfAbsent("statusCode", response.statusCode());
            return result;
        } catch (Exception e) {
            log.debug("HttpRestCommand response body is not JSON object: {}", e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("rawResponse", body);
            result.put("statusCode", response.statusCode());
            return result;
        }
    }

    private String substitutePlaceholders(String url, Map<String, Object> input) {
        if (url == null || !url.contains("{")) return url;

        @SuppressWarnings("unchecked")
        Map<String, Object> ctx = input.get("_context") instanceof Map ? (Map<String, Object>) input.get("_context") : Map.of();

        Matcher m = VAR_PATTERN.matcher(url);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1) != null ? m.group(1) : m.group(2);
            key = key.trim();
            if (key.startsWith("context.")) {
                key = key.substring(8);
            }
            Object val = ctx.get(key);
            if (val == null) val = input.get(key);
            String replacement = val != null ? Matcher.quoteReplacement(String.valueOf(val)) : "";
            m.appendReplacement(sb, replacement);
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
