package de.tum.in.www1.hephaestus.testconfig;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Test configuration that makes @Async event listeners run synchronously.
 *
 * <p><b>Why this exists:</b> Integration tests were experiencing intermittent deadlocks
 * during database cleanup (TRUNCATE). The root cause was @Async event listeners
 * (e.g., {@code ActivityEventListener}) spawning threads that continued writing
 * to the database after a test completed but before the next test's cleanup ran.
 *
 * <p><b>The deadlock scenario:</b>
 * <ol>
 *   <li>Test A completes and commits its transaction</li>
 *   <li>@TransactionalEventListener(AFTER_COMMIT) fires asynchronously via @Async</li>
 *   <li>Test B starts and calls TRUNCATE TABLE in @BeforeEach</li>
 *   <li>DEADLOCK: Async thread holds row lock, TRUNCATE needs table lock</li>
 * </ol>
 *
 * <p><b>The fix:</b> Implements {@link AsyncConfigurer} so Spring uses the synchronous
 * executor for ALL @Async method dispatch, not just for beans resolved by name.
 * The production {@link de.tum.in.www1.hephaestus.config.SpringAsyncConfig} is excluded
 * via {@code @Profile("!test")}, so this is the sole {@code AsyncConfigurer} in tests.
 *
 * <p><b>Benefits:</b>
 * <ul>
 *   <li>Deterministic test execution - no race conditions</li>
 *   <li>Faster tests - no thread pool overhead for event listeners</li>
 *   <li>Complete stack traces for debugging</li>
 *   <li>No flaky tests due to timing issues</li>
 * </ul>
 *
 * @see org.springframework.scheduling.annotation.Async
 * @see org.springframework.core.task.SyncTaskExecutor
 */
@Configuration
@Profile("test")
@EnableAsync
public class TestAsyncConfiguration implements AsyncConfigurer {

    private final SyncAsyncTaskExecutor syncExecutor = new SyncAsyncTaskExecutor();

    @Override
    public Executor getAsyncExecutor() {
        return syncExecutor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }

    /**
     * Primary synchronous executor that makes @Async methods run synchronously.
     */
    @Bean
    @Primary
    public AsyncTaskExecutor taskExecutor() {
        return syncExecutor;
    }

    /**
     * Synchronous executor for monitoring tasks in tests.
     */
    @Bean(name = "monitoringExecutor")
    public AsyncTaskExecutor monitoringExecutor() {
        return syncExecutor;
    }

    /**
     * A synchronous implementation of {@link AsyncTaskExecutor}.
     * Executes all tasks immediately in the calling thread.
     */
    private static class SyncAsyncTaskExecutor implements AsyncTaskExecutor {

        private final SyncTaskExecutor delegate = new SyncTaskExecutor();

        @Override
        public void execute(Runnable task) {
            delegate.execute(task);
        }

        @Override
        public Future<?> submit(Runnable task) {
            execute(task);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            try {
                T result = task.call();
                return CompletableFuture.completedFuture(result);
            } catch (Exception e) {
                CompletableFuture<T> failed = new CompletableFuture<>();
                failed.completeExceptionally(e);
                return failed;
            }
        }
    }
}
