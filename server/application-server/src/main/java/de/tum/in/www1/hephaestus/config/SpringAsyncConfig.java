package de.tum.in.www1.hephaestus.config;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Async execution configuration.
 * <p>
 * Implements {@link AsyncConfigurer} to ensure ALL @Async methods use the bounded
 * thread pool, preventing the default SimpleAsyncTaskExecutor from creating
 * unbounded threads (which caused 5000+ thread creation in production).
 */
@Configuration
@EnableAsync
@Profile("!test")
public class SpringAsyncConfig implements AsyncConfigurer {

    private ThreadPoolTaskExecutor executor;

    /**
     * Default async executor for @Async methods (e.g., activity event listeners).
     *
     * <p>Intentionally a bounded ThreadPoolTaskExecutor even when
     * {@code spring.threads.virtual.enabled=true} flips the auto-configured one to
     * {@code SimpleAsyncTaskExecutor}; auto-config backs off via {@code @ConditionalOnMissingBean}.
     * Boundedness here is load-shedding: a runaway burst of activity events cannot create
     * thousands of in-flight tasks racing the EntityManagerFactory close.
     *
     * <p>{@code setWaitForTasksToCompleteOnShutdown(true)} keeps EMF open until in-flight tasks
     * finish — prevents "EntityManagerFactory is closed" races at shutdown.
     */
    @Bean(name = TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
    public AsyncTaskExecutor applicationTaskExecutor() {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * Configures the default executor for all @Async annotated methods.
     * <p>
     * Without this, Spring falls back to SimpleAsyncTaskExecutor which creates
     * a new thread for every task - causing unbounded thread growth.
     */
    @Override
    public Executor getAsyncExecutor() {
        if (executor == null) {
            applicationTaskExecutor();
        }
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
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
