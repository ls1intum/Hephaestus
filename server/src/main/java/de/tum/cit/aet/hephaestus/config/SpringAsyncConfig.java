package de.tum.cit.aet.hephaestus.config;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * {@code @Async} advice is ordered outside the transaction advisor (so an {@code @Async @Transactional}
 * method opens its transaction on the pool thread, not the caller thread) and inside
 * {@code WorkspaceAgnosticAspect} at {@code HIGHEST_PRECEDENCE}. Both otherwise default to
 * {@code LOWEST_PRECEDENCE}, hence the explicit {@code HIGHEST_PRECEDENCE + 1}; many
 * {@code @Async @Transactional} listeners depend on it.
 *
 * <p>{@link #getAsyncExecutor()} pins the default executor to the bounded
 * {@link #applicationTaskExecutor}; with a second {@link AsyncTaskExecutor} bean present, type
 * resolution is ambiguous and Spring would otherwise fall back to an unbounded
 * {@code SimpleAsyncTaskExecutor}.
 */
@Configuration
@EnableAsync(order = Ordered.HIGHEST_PRECEDENCE + 1)
@Profile("!test")
public class SpringAsyncConfig implements AsyncConfigurer {

    @Bean(name = TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
    public ThreadPoolTaskExecutor applicationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        // Stays below the datasource pool (spring.datasource.hikari.maximum-pool-size = 30) so an
        // async burst can't starve web requests of connections.
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("async-");
        // Let in-flight async DB work finish before the EntityManagerFactory closes on shutdown.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    /** Long-running provider reconciliations must not queue behind or starve ordinary async work. */
    @Bean(name = "syncJobExecutor")
    public AsyncTaskExecutor syncJobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("sync-job-");
        // SyncJobService (a lifecycle phase above) records cooperative cancellation first; runners
        // observe it and unwind on their own. shutdownNow is the backstop that bounds context close
        // rather than leaking this non-daemon pool — it must never be the primary mechanism, since an
        // interrupt is ignored by a pgjdbc socket read here and destroys one on a virtual thread.
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return applicationTaskExecutor();
    }

    /** Virtual-thread executor for blocking-I/O monitoring tasks. No graceful shutdown — use
     * {@link #applicationTaskExecutor} for DB work. */
    @Bean(name = "monitoringExecutor")
    @ConditionalOnMissingBean(name = "monitoringExecutor")
    public AsyncTaskExecutor monitoringExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}
