package com.enterprise.atlas.workflow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Form Engine - CPOS Workflow Orchestration & Decision API")
                        .version("1.0.0")
                        .description("API Documentation for Project Atlas Workflow Engine. " +
                                "Manages rules, outcome buckets, context catalogs, integrations, " +
                                "workflow definitions, visual traversals, trace debugger, and stateful async resumptions.")
                        .contact(new Contact()
                                .name("CPOS Team")
                                .email("cpos@vi.com")));
    }
}
