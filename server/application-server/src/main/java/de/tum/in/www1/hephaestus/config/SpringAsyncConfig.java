package de.tum.in.www1.hephaestus.config;

import java.util.concurrent.Executors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class SpringAsyncConfig {

    /**
     * Default async executor for @Async methods (e.g., activity event listeners).
     * <p>
     * Uses ThreadPoolTaskExecutor with graceful shutdown to prevent race conditions
     * where async tasks attempt to access EntityManagerFactory after it's closed.
     * <p>
     * <b>Graceful shutdown behavior:</b>
     * <ul>
     *   <li>On shutdown, executor stops accepting new tasks</li>
     *   <li>Waits up to 30 seconds for running tasks to complete</li>
     *   <li>EntityManagerFactory stays open until all async tasks finish</li>
     * </ul>
     * <p>
     * Pool sizing rationale:
     * <ul>
     *   <li>Core 10: Handles steady stream of activity events</li>
     *   <li>Max 50: Bursts during sync operations with many domain events</li>
     *   <li>Queue 500: Buffer for spikes, prevents rejection under load</li>
     * </ul>
     */
    @Bean(name = TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
    public AsyncTaskExecutor applicationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("async-");
        // CRITICAL: Wait for in-flight tasks to complete before destroying beans
        // This prevents "EntityManagerFactory is closed" errors during shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * Virtual thread executor for I/O-bound monitoring tasks (GitHub API calls).
     * Virtual threads (Java 21+) are ideal for blocking I/O operations because:
     * - They are extremely lightweight (~1KB vs ~1MB for platform threads)
     * - Thousands can run concurrently without thread pool exhaustion
     * - The JVM automatically unmounts them during blocking operations
     * - No need to tune pool sizes - spawn as many as needed
     * <p>
     * Note: This executor does NOT support graceful shutdown. Tasks may be
     * interrupted during application shutdown. Use applicationTaskExecutor
     * for tasks that require database access.
     */
    @Bean(name = "monitoringExecutor")
    @ConditionalOnMissingBean(name = "monitoringExecutor")
    public AsyncTaskExecutor monitoringExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}
