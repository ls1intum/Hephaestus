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
 * Async execution configuration. All {@code @Async} methods go through the bounded
 * {@link #applicationTaskExecutor} so a burst of activity events cannot spawn unbounded threads.
 */
@Configuration
@EnableAsync
@Profile("!test")
public class SpringAsyncConfig implements AsyncConfigurer {

    private ThreadPoolTaskExecutor executor;

    /**
     * Intentionally a bounded {@link ThreadPoolTaskExecutor} even when
     * {@code spring.threads.virtual.enabled=true} flips the auto-configured one to
     * {@code SimpleAsyncTaskExecutor} (auto-config backs off via {@code @ConditionalOnMissingBean}).
     * Boundedness is load-shedding: a burst of activity events cannot race the
     * {@code EntityManagerFactory} close at shutdown.
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

    /** Virtual-thread executor for blocking I/O monitoring tasks. No graceful shutdown — use
     * {@link #applicationTaskExecutor} when DB access is involved. */
    @Bean(name = "monitoringExecutor")
    @ConditionalOnMissingBean(name = "monitoringExecutor")
    public AsyncTaskExecutor monitoringExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}
