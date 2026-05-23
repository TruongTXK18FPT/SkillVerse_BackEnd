package com.exe.skillverse_backend.shared.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for asynchronous task execution
 * Used for email sending operations to prevent server overload
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    @Value("${app.roadmap.enrichment.concurrency:2}")
    private int roadmapEnrichmentConcurrency;

    /**
     * Thread pool executor for email sending operations
     * Prevents server overload by limiting concurrent email operations
     * 
     * Configuration:
     * - Core pool size: 5 threads
     * - Max pool size: 10 threads
     * - Queue capacity: 100 tasks
     * - Thread name prefix: "EmailAsync-"
     */
    @Bean(name = "emailTaskExecutor")
    public Executor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Core pool size - minimum number of threads
        executor.setCorePoolSize(5);

        // Maximum pool size - max number of threads
        executor.setMaxPoolSize(10);

        // Queue capacity - max number of tasks waiting in queue
        executor.setQueueCapacity(100);

        // Thread name prefix for debugging
        executor.setThreadNamePrefix("EmailAsync-");

        // Graceful shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();

        log.info("Email Task Executor initialized with core={}, max={}, queue={}",
                executor.getCorePoolSize(),
                executor.getMaxPoolSize(),
                executor.getQueueCapacity());

        return executor;
    }

    @Bean(name = "fcmTaskExecutor")
    public Executor fcmTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("FcmAsync-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        log.info("FCM Task Executor initialized with core={}, max={}, queue={}",
                executor.getCorePoolSize(),
                executor.getMaxPoolSize(),
                executor.getQueueCapacity());

        return executor;
    }

    /**
     * Thread pool executor for AI grading operations.
     * Small pool size (2 core, 5 max) because AI calls are I/O-bound but
     * consume memory for prompt building. Queue capacity of 50 handles spikes.
     */
    @Bean(name = "gradingTaskExecutor")
    public Executor gradingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("GradingAsync-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        executor.initialize();

        log.info("Grading Task Executor initialized with core={}, max={}, queue={}",
                executor.getCorePoolSize(),
                executor.getMaxPoolSize(),
                executor.getQueueCapacity());

        return executor;
    }

    /**
     * Thread pool executor for concurrent AI roadmap node enrichment.
     * Hard-limited pool size to prevent OpenAI/Mistral rate limits (HTTP 429).
     */
    @Bean(name = "roadmapEnrichmentTaskExecutor")
    public Executor roadmapEnrichmentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(roadmapEnrichmentConcurrency);
        executor.setMaxPoolSize(roadmapEnrichmentConcurrency);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("RoadmapEnrichment-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();

        log.info("Roadmap Enrichment Task Executor initialized with core={}, max={}, queue={}",
                executor.getCorePoolSize(),
                executor.getMaxPoolSize(),
                executor.getQueueCapacity());

        return executor;
    }
}
