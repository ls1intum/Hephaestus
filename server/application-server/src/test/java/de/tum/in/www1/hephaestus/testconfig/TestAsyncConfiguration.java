package de.tum.in.www1.hephaestus.testconfig;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

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
 * <p><b>The fix:</b> By providing a synchronous primary task executor, @Async event
 * listeners execute synchronously within the caller's thread, eliminating race conditions.
 *
 * <p><b>Note:</b> The {@code monitoringExecutor} is NOT overridden because the
 * {@code GitHubDataSyncService} uses it for sync operations that have their own
 * transactions and should continue to run asynchronously to avoid transaction issues.
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
public class TestAsyncConfiguration {

    /**
     * Primary synchronous executor that makes @Async methods run synchronously.
     *
     * <p>This bean has {@code @Primary} so it is used by default for @Async methods
     * that don't specify a qualifier. The production {@code monitoringExecutor} is
     * NOT overridden, allowing sync operations to run asynchronously with proper
     * transaction isolation.
     *
     * <p>@Async event listeners (like ActivityEventListener) will execute synchronously
     * in the calling thread, ensuring all database operations complete before test cleanup.
     */
    @Bean
    @Primary
    public AsyncTaskExecutor taskExecutor() {
        return new SyncAsyncTaskExecutor();
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
