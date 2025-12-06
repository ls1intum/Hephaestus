package de.tum.in.www1.hephaestus.config;

import java.util.concurrent.Executors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class SpringAsyncConfig {

    /**
     * Virtual thread executor for I/O-bound monitoring tasks (GitHub API calls).
     * Virtual threads (Java 21+) are ideal for blocking I/O operations because:
     * - They are extremely lightweight (~1KB vs ~1MB for platform threads)
     * - Thousands can run concurrently without thread pool exhaustion
     * - The JVM automatically unmounts them during blocking operations
     * - No need to tune pool sizes - spawn as many as needed
     */
    @Bean(name = "monitoringExecutor")
    @ConditionalOnMissingBean(name = "monitoringExecutor")
    public AsyncTaskExecutor monitoringExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}
