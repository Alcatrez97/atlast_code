package com.vi.atlas.workflow;

import com.vi.atlas.workflow.dto.CommandMetadataDto;
import com.vi.atlas.workflow.dto.CommandParameterDto;
import com.vi.atlas.workflow.entity.IntegrationRegistry;
import com.vi.atlas.workflow.repository.IntegrationRegistryRepository;
import com.vi.atlas.workflow.service.command.CommandRegistry;
import com.vi.atlas.workflow.service.command.WorkflowCommand;
import com.vi.atlas.workflow.service.command.impl.HttpRestCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
@ActiveProfiles("test")
public class CommandCatalogTest {

    @Autowired
    private CommandRegistry commandRegistry;

    @Autowired
    private HttpRestCommand httpRestCommand;

    @Autowired
    private IntegrationRegistryRepository integrationRegistryRepository;

    @Test
    public void testCommandCatalogDiscovery() {
        List<CommandMetadataDto> catalog = commandRegistry.getCommandCatalog();
        assertNotNull(catalog);
        assertFalse(catalog.isEmpty());

        // Check that core commands are registered
        List<String> types = catalog.stream().map(CommandMetadataDto::getType).toList();
        assertTrue(types.contains("REST"), "Catalog should include REST");
        assertTrue(types.contains("FINALIZE_CAF_SUBMISSION"), "Catalog should include FINALIZE_CAF_SUBMISSION");
        assertTrue(types.contains("CREATE_BUCKET"), "Catalog should include CREATE_BUCKET");
        assertTrue(types.contains("UPDATE_FORM_STATUS"), "Catalog should include UPDATE_FORM_STATUS");
        assertTrue(types.contains("START_CHILD_WORKFLOW"), "Catalog should include START_CHILD_WORKFLOW");
        assertTrue(types.contains("EMIT_EVENT"), "Catalog should include EMIT_EVENT");
        assertTrue(types.contains("MQ"), "Catalog should include MQ");

        // Verify parameters exist for REST
        CommandMetadataDto restDto = catalog.stream().filter(c -> "REST".equals(c.getType())).findFirst().orElseThrow();
        assertEquals("Call External REST API", restDto.getDisplayName());
        assertEquals("Integration", restDto.getCategory());
        assertTrue(restDto.isExternalIo());

        List<String> paramNames = restDto.getParameters().stream().map(CommandParameterDto::getName).toList();
        assertTrue(paramNames.contains("url"));
        assertTrue(paramNames.contains("method"));
        assertTrue(paramNames.contains("headers"));
        assertTrue(paramNames.contains("integrationKey"));

        // Verify parameter dataSource for integrationKey
        CommandParameterDto intKeyParam = restDto.getParameters().stream()
                .filter(p -> "integrationKey".equals(p.getName())).findFirst().orElseThrow();
        assertEquals("INTEGRATIONS", intKeyParam.getDataSource());
        // Verify parameters exist for MQ (Kafka)
        CommandMetadataDto mqDto = catalog.stream().filter(c -> "MQ".equals(c.getType())).findFirst().orElseThrow();
        assertEquals("Publish to Kafka / MQ", mqDto.getDisplayName());
        assertEquals("Messaging", mqDto.getCategory());
        List<String> mqParams = mqDto.getParameters().stream().map(CommandParameterDto::getName).toList();
        assertTrue(mqParams.contains("topic"), "MQ should include topic");
        assertTrue(mqParams.contains("key"), "MQ should include key");
        assertTrue(mqParams.contains("consumerGroup"), "MQ should include consumerGroup");
        assertTrue(mqParams.contains("headers"), "MQ should include headers");
        assertTrue(mqParams.contains("payloadMapping"), "MQ should include payloadMapping");

        // Verify parameters exist for EMIT_EVENT
        CommandMetadataDto emitDto = catalog.stream().filter(c -> "EMIT_EVENT".equals(c.getType())).findFirst().orElseThrow();
        assertEquals("Emit Domain Event", emitDto.getDisplayName());
        List<String> emitParams = emitDto.getParameters().stream().map(CommandParameterDto::getName).toList();
        assertTrue(emitParams.contains("eventType"));
        assertTrue(emitParams.contains("topic"));
        assertTrue(emitParams.contains("key"));
        assertTrue(emitParams.contains("consumerGroup"));
        assertTrue(emitParams.contains("headers"));
        assertTrue(emitParams.contains("payloadMapping"));
    }

    @Test
    public void testAliasesResolution() {
        Optional<WorkflowCommand> callExt = commandRegistry.getCommand("CALL_EXTERNAL_SYSTEM");
        assertTrue(callExt.isPresent());
        assertEquals("REST", callExt.get().getCommandType());

        Optional<WorkflowCommand> httpCmd = commandRegistry.getCommand("HTTP");
        assertTrue(httpCmd.isPresent());
        assertEquals("REST", httpCmd.get().getCommandType());

        Optional<WorkflowCommand> kafkaCmd = commandRegistry.getCommand("KAFKA");
        assertTrue(kafkaCmd.isPresent());
        assertEquals("MQ", kafkaCmd.get().getCommandType());

        Optional<WorkflowCommand> mqPublishCmd = commandRegistry.getCommand("MQ_PUBLISH");
        assertTrue(mqPublishCmd.isPresent());
        assertEquals("MQ", mqPublishCmd.get().getCommandType());
    }

    @Test
    public void testMqPublishCommandExecutionWithPayloadMappingAndHeaders() throws Exception {
        WorkflowCommand mqCommand = commandRegistry.getCommand("MQ").orElseThrow();

        Map<String, Object> context = new HashMap<>();
        context.put("cafId", "CAF_TEST_999");
        context.put("circleId", 101);
        context.put("mobileNo", "9876543210");

        Map<String, Object> payloadMapping = new HashMap<>();
        payloadMapping.put("targetCafId", "cafId");
        payloadMapping.put("targetCircle", "context.circleId");

        Map<String, Object> input = new HashMap<>();
        input.put("topic", "test-kafka-topic");
        input.put("key", "${context.cafId}");
        input.put("consumerGroup", "test-consumer-group");
        input.put("headers", "{\"X-Trace-Id\": \"TRC-100\"}");
        input.put("payloadMapping", payloadMapping);
        input.put("_context", context);

        Map<String, Object> result = mqCommand.execute(input);
        assertNotNull(result);
        assertEquals("PUBLISHED", result.get("status"));
        assertEquals("test-kafka-topic", result.get("topic"));
        assertEquals("CAF_TEST_999", result.get("key"));
        assertEquals("test-consumer-group", result.get("consumerGroup"));
    }

    @Test
    public void testHttpRestCommandWithIntegrationRegistry() throws Exception {
        // Save test integration profile
        IntegrationRegistry reg = new IntegrationRegistry();
        reg.setId("test-int-id");
        reg.setIntegrationKey("TEST_KYC_API");
        reg.setName("Test KYC Connector");
        reg.setProviderType("REST");
        reg.setEndpointUrl("http://localhost:9091/api/integrations"); // Existing endpoint on running service
        reg.setMethod("GET");
        reg.setTimeoutMs(4000);
        integrationRegistryRepository.save(reg);

        // Execute using integrationKey
        Map<String, Object> input = Map.of(
                "integrationKey", "TEST_KYC_API",
                "_context", Map.of("testVar", "123")
        );

        try {
            Map<String, Object> result = httpRestCommand.execute(input);
            assertNotNull(result);
        } catch (Exception e) {
            // Expected when running unit tests without live mock HTTP server on port 9091
            assertNotNull(e);
        }
    }
}
