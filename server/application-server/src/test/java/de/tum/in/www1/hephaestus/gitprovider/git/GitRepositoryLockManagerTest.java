package de.tum.in.www1.hephaestus.gitprovider.git;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("GitRepositoryLockManager")
class GitRepositoryLockManagerTest extends BaseUnitTest {

    private GitRepositoryLockManager lockManager;

    @BeforeEach
    void setUp() {
        lockManager = new GitRepositoryLockManager();
    }

    @Nested
    @DisplayName("getLock")
    class GetLock {

        @Test
        @DisplayName("should return same lock instance for same repository ID")
        void shouldReturnSameLockForSameRepositoryId() {
            ReentrantReadWriteLock lock1 = lockManager.getLock(1L);
            ReentrantReadWriteLock lock2 = lockManager.getLock(1L);

            assertThat(lock1).isSameAs(lock2);
        }

        @Test
        @DisplayName("should return different lock instances for different repository IDs")
        void shouldReturnDifferentLocksForDifferentRepositoryIds() {
            ReentrantReadWriteLock lock1 = lockManager.getLock(1L);
            ReentrantReadWriteLock lock2 = lockManager.getLock(2L);

            assertThat(lock1).isNotSameAs(lock2);
        }

        @Test
        @DisplayName("should create lock on first access")
        void shouldCreateLockOnFirstAccess() {
            assertThat(lockManager.getActiveLockCount()).isZero();

            lockManager.getLock(1L);

            assertThat(lockManager.getActiveLockCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("withWriteLock (Supplier)")
    class WithWriteLockSupplier {

        @Test
        @DisplayName("should execute operation and return result")
        void shouldExecuteOperationAndReturnResult() {
            String result = lockManager.withWriteLock(1L, () -> "hello");

            assertThat(result).isEqualTo("hello");
        }

        @Test
        @DisplayName("should release lock after operation completes")
        void shouldReleaseLockAfterOperationCompletes() {
            lockManager.withWriteLock(1L, () -> "done");

            ReentrantReadWriteLock lock = lockManager.getLock(1L);
            assertThat(lock.isWriteLocked()).isFalse();
        }

        @Test
        @DisplayName("should release lock even when operation throws")
        void shouldReleaseLockWhenOperationThrows() {
            try {
                lockManager.withWriteLock(1L, () -> {
                    throw new RuntimeException("fail");
                });
            } catch (RuntimeException ignored) {
                // expected
            }

            ReentrantReadWriteLock lock = lockManager.getLock(1L);
            assertThat(lock.isWriteLocked()).isFalse();
        }
    }

    @Nested
    @DisplayName("withWriteLock (Runnable)")
    class WithWriteLockRunnable {

        @Test
        @DisplayName("should execute runnable operation")
        void shouldExecuteRunnableOperation() {
            AtomicBoolean executed = new AtomicBoolean(false);

            lockManager.withWriteLock(1L, () -> executed.set(true));

            assertThat(executed.get()).isTrue();
        }

        @Test
        @DisplayName("should release lock after runnable completes")
        void shouldReleaseLockAfterRunnableCompletes() {
            lockManager.withWriteLock(1L, () -> {});

            ReentrantReadWriteLock lock = lockManager.getLock(1L);
            assertThat(lock.isWriteLocked()).isFalse();
        }
    }

    @Nested
    @DisplayName("withReadLock")
    class WithReadLock {

        @Test
        @DisplayName("should execute operation and return result")
        void shouldExecuteOperationAndReturnResult() {
            String result = lockManager.withReadLock(1L, () -> "read");

            assertThat(result).isEqualTo("read");
        }

        @Test
        @DisplayName("should release lock after operation completes")
        void shouldReleaseLockAfterOperationCompletes() {
            lockManager.withReadLock(1L, () -> "done");

            ReentrantReadWriteLock lock = lockManager.getLock(1L);
            assertThat(lock.getReadLockCount()).isZero();
        }

        @Test
        @DisplayName("should allow concurrent readers")
        void shouldAllowConcurrentReaders() throws InterruptedException {
            AtomicInteger concurrentReaders = new AtomicInteger(0);
            AtomicInteger maxConcurrentReaders = new AtomicInteger(0);
            CountDownLatch allStarted = new CountDownLatch(2);
            CountDownLatch proceed = new CountDownLatch(1);

            Runnable readOp = () ->
                lockManager.withReadLock(1L, () -> {
                    int current = concurrentReaders.incrementAndGet();
                    maxConcurrentReaders.updateAndGet(max -> Math.max(max, current));
                    allStarted.countDown();
                    try {
                        proceed.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    concurrentReaders.decrementAndGet();
                    return null;
                });

            Thread t1 = new Thread(readOp);
            Thread t2 = new Thread(readOp);
            t1.start();
            t2.start();

            assertThat(allStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(maxConcurrentReaders.get()).isEqualTo(2);

            proceed.countDown();
            t1.join(5000);
            t2.join(5000);
        }
    }

    @Nested
    @DisplayName("removeLock")
    class RemoveLock {

        @Test
        @DisplayName("should remove existing lock")
        void shouldRemoveExistingLock() {
            lockManager.getLock(1L);
            assertThat(lockManager.getActiveLockCount()).isEqualTo(1);

            lockManager.removeLock(1L);

            assertThat(lockManager.getActiveLockCount()).isZero();
        }

        @Test
        @DisplayName("should not fail when removing non-existent lock")
        void shouldNotFailWhenRemovingNonExistentLock() {
            lockManager.removeLock(999L);

            assertThat(lockManager.getActiveLockCount()).isZero();
        }
    }

    @Nested
    @DisplayName("eviction")
    class Eviction {

        @Test
        @DisplayName("should evict idle locks when exceeding max capacity")
        void shouldEvictIdleLocksWhenExceedingMaxCapacity() {
            // Fill up to MAX_LOCKS
            for (long i = 0; i < GitRepositoryLockManager.MAX_LOCKS; i++) {
                lockManager.getLock(i);
            }
            assertThat(lockManager.getActiveLockCount()).isEqualTo(GitRepositoryLockManager.MAX_LOCKS);

            // Adding one more should trigger eviction of idle locks
            lockManager.getLock((long) GitRepositoryLockManager.MAX_LOCKS);

            // All previous locks were idle (not held), so they should be evicted
            // Only the newly created one remains
            assertThat(lockManager.getActiveLockCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should not evict locks that are currently held")
        void shouldNotEvictLocksCurrentlyHeld() {
            // Fill up to MAX_LOCKS
            for (long i = 0; i < GitRepositoryLockManager.MAX_LOCKS; i++) {
                lockManager.getLock(i);
            }

            // Hold a read lock on repository 0
            ReentrantReadWriteLock heldLock = lockManager.getLock(0L);
            heldLock.readLock().lock();
            try {
                // Adding one more should trigger eviction but keep the held lock
                lockManager.getLock((long) GitRepositoryLockManager.MAX_LOCKS);

                // The held lock should survive eviction
                assertThat(lockManager.getActiveLockCount()).isEqualTo(2);
            } finally {
                heldLock.readLock().unlock();
            }
        }
    }

    @Nested
    @DisplayName("getActiveLockCount")
    class GetActiveLockCount {

        @Test
        @DisplayName("should return zero initially")
        void shouldReturnZeroInitially() {
            assertThat(lockManager.getActiveLockCount()).isZero();
        }

        @Test
        @DisplayName("should track active locks")
        void shouldTrackActiveLocks() {
            lockManager.getLock(1L);
            lockManager.getLock(2L);
            lockManager.getLock(3L);

            assertThat(lockManager.getActiveLockCount()).isEqualTo(3);
        }
    }
}
