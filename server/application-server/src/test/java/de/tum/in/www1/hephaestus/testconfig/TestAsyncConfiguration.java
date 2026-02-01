package de.tum.in.www1.hephaestus.testconfig;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
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
 * <p><b>The fix:</b> By providing synchronous executors and implementing AsyncConfigurer,
 * @Async event listeners execute synchronously within the caller's thread, eliminating
 * race conditions. The monitoringExecutor is replaced with a no-op to skip sync
 * operations entirely (which would otherwise cause transaction rollback issues).
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
@EnableAsync
@Profile("test")
public class TestAsyncConfiguration implements AsyncConfigurer {

    private final SyncAsyncTaskExecutor syncExecutor = new SyncAsyncTaskExecutor();

    /**
     * Configures the default executor for all @Async annotated methods.
     * This returns our synchronous executor to ensure all async methods
     * run in the calling thread during tests.
     */
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
     *
     * <p>This bean uses the same name as Spring Boot's auto-configured task executor
     * to ensure it takes precedence. The {@code @Primary} annotation ensures it is
     * used by default for @Async methods that don't specify a qualifier.
     *
     * <p>@Async event listeners (like ActivityEventListener) will execute synchronously
     * in the calling thread, ensuring all database operations complete before test cleanup.
     */
    @Bean(name = TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
    @Primary
    public AsyncTaskExecutor applicationTaskExecutor() {
        return new SyncAsyncTaskExecutor();
    }

    /**
     * No-op monitoring executor that skips all submitted sync tasks.
     *
     * <p>The production {@code monitoringExecutor} uses virtual threads for efficient
     * I/O-bound operations. In tests, we face two problems with sync operations:
     * <ol>
     *   <li><b>Async deadlocks:</b> If async, sync operations from one test can
     *       conflict with the next test's database cleanup (TRUNCATE)</li>
     *   <li><b>Sync transaction issues:</b> If sync, operations that fail (e.g.,
     *       due to missing GitHub credentials) mark the transaction for rollback,
     *       causing UnexpectedRollbackException</li>
     * </ol>
     *
     * <p>The solution is to skip sync operations entirely in tests. Tests that need
     * to verify sync behavior should mock the sync service directly.
     */
    @Bean(name = "monitoringExecutor")
    public AsyncTaskExecutor monitoringExecutor() {
        return new NoOpAsyncTaskExecutor();
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

    /**
     * An executor that ignores all submitted tasks.
     * Used for monitoring tasks that shouldn't run during tests.
     */
    private static class NoOpAsyncTaskExecutor implements AsyncTaskExecutor {

        @Override
        public void execute(Runnable task) {
            // Skip execution
        }

        @Override
        public Future<?> submit(Runnable task) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
