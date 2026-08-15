package com.vi.atlas.workflow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Java 21 Project Loom configuration.
 *
 * <p>Provides a Virtual Thread per task executor for high-concurrency, non-blocking
 * parallel branch execution, asynchronous command dispatching, and child workflow triggers.
 */
@Configuration
public class VirtualThreadConfig {

    private static final Logger log = LoggerFactory.getLogger(VirtualThreadConfig.class);

    @Bean(name = "workflowVirtualTaskExecutor")
    public ExecutorService workflowVirtualTaskExecutor() {
        log.info("Initializing Java 21 Virtual Thread Executor (Executors.newVirtualThreadPerTaskExecutor) for Workflow Engine.");
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
