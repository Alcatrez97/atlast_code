package com.vi.atlas.workflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.vi.atlas.workflow.repository")
@EntityScan(basePackages = "com.vi.atlas.workflow.entity")
@EnableScheduling
@EnableRetry
public class WorkflowServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkflowServiceApplication.class, args);
    }
}
