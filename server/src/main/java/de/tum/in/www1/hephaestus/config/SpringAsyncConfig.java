package de.tum.in.www1.hephaestus.config;

import java.util.concurrent.Executors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@Profile("!test")
public class SpringAsyncConfig {

    /**
     * Defining this bean causes {@code TaskExecutionAutoConfiguration} to back off via
     * {@code @ConditionalOnMissingBean}, so {@code @Async} methods route here even when
     * {@code spring.threads.virtual.enabled=true} would otherwise install a
     * {@code SimpleAsyncTaskExecutor}. {@code waitForTasksToCompleteOnShutdown=true} is what
     * keeps async DB work from racing the {@code EntityManagerFactory} close; the bounded pool
     * is unrelated load-shedding.
     */
    @Bean(name = TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
    public AsyncTaskExecutor applicationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /** Virtual-thread executor for blocking I/O monitoring tasks. No graceful shutdown — use
     * {@link #applicationTaskExecutor} when DB access is involved. */
    @Bean(name = "monitoringExecutor")
    @ConditionalOnMissingBean(name = "monitoringExecutor")
    public AsyncTaskExecutor monitoringExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}
