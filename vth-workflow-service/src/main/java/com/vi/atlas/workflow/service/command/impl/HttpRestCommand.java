package com.vi.atlas.workflow.service.command.impl;

import com.vi.atlas.workflow.service.command.WorkflowCommand;
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
    public boolean isExternalIo() {
        return true;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> input) throws Exception {
        String url = getStringParam(input, "url");
        String method = (String) input.getOrDefault("method", "GET");
        
        if (url == null) {
            throw new IllegalArgumentException("REST command requires a non-blank 'url' parameter.");
        }

        log.info("Executing HttpRestCommand: method={}, url={}, input={}", method, url, input);

        long timeoutSec = 10;
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
            Map<String, Object> payload = extractCleanPayload(input, "url", "method", "headers");
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
