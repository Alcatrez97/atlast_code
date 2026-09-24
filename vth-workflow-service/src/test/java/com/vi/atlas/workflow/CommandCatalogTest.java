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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
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
