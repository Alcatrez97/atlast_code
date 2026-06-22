package com.enterprise.atlas.workflow.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
public class HttpRestCommand implements WorkflowCommand {

    private static final Logger log = LoggerFactory.getLogger(HttpRestCommand.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String getCommandType() {
        return "REST";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> input) throws Exception {
        String url = (String) input.get("url");
        String method = (String) input.getOrDefault("method", "GET");
        
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("REST command requires a non-blank 'url' parameter.");
        }

        log.info("Executing HttpRestCommand: method={}, url={}, input={}", method, url, input);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");

        // Use custom headers if provided
        Object headersObj = input.get("headers");
        if (headersObj instanceof Map) {
            Map<?, ?> headers = (Map<?, ?>) headersObj;
            for (Map.Entry<?, ?> entry : headers.entrySet()) {
                builder.header(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }

        method = method.toUpperCase();
        if ("GET".equals(method)) {
            builder.GET();
        } else {
            // Include payload mapping body or just serialize input map (excluding static config keys)
            Map<String, Object> payload = new java.util.HashMap<>(input);
            payload.remove("url");
            payload.remove("method");
            payload.remove("headers");
            payload.remove("commandType");
            payload.remove("inputMapping");
            payload.remove("outputMapping");
            
            String bodyJson = MAPPER.writeValueAsString(payload);
            log.info("HttpRestCommand full request body payload: {}", bodyJson);
            builder.method(method, HttpRequest.BodyPublishers.ofString(bodyJson));
        }

        HttpResponse<String> response = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        
        log.info("HttpRestCommand response status: {}", response.statusCode());
        log.info("HttpRestCommand full response body payload: {}", response.body());

        if (response.statusCode() >= 400) {
            throw new RuntimeException("HTTP Command failed with status: " + response.statusCode() + " and body: " + response.body());
        }

        String body = response.body();
        if (body == null || body.isBlank()) {
            return Map.of();
        }

        // Deserialize response body to Map
        try {
            return MAPPER.readValue(body, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("HttpRestCommand failed to parse response body as JSON: {}", e.getMessage());
            return Map.of("rawResponse", body);
        }
    }
}
