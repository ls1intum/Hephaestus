package de.tum.in.www1.hephaestus.gitprovider.git;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Manages read-write locks for git repository operations.
 * <p>
 * Uses in-process locking with ReentrantReadWriteLock per repository.
 * This is sufficient because:
 * <ul>
 *   <li>Only application-server writes to repositories (fetch, clone)</li>
 *   <li>intelligence-service only reads via shared volume mount</li>
 *   <li>Git operations are atomic for concurrent reads</li>
 * </ul>
 * <p>
 * Lock map is bounded to {@value #MAX_LOCKS} entries. When the limit is
 * reached, idle locks (not currently held) are evicted before creating
 * new ones. In practice the number of concurrently-accessed repositories
 * is much smaller than the limit, so eviction rarely triggers.
 * <p>
 * If horizontal scaling is needed later, this can be replaced with
 * distributed locking (pg_advisory_lock or Redis SETNX).
 */
@Service
public class GitRepositoryLockManager {

    private static final Logger log = LoggerFactory.getLogger(GitRepositoryLockManager.class);

    /**
     * Maximum number of lock entries. Each entry is ~100 bytes
     * (Long key + ReentrantReadWriteLock), so 10k entries ≈ 1 MB.
     */
    static final int MAX_LOCKS = 10_000;

    private final ConcurrentHashMap<Long, ReentrantReadWriteLock> locks = new ConcurrentHashMap<>();

    /**
     * Get or create a lock for the given repository.
     * Locks are keyed by repository database ID for uniqueness.
     */
    public ReentrantReadWriteLock getLock(Long repositoryId) {
        return locks.computeIfAbsent(repositoryId, k -> {
            evictIdleLocksIfNeeded();
            return new ReentrantReadWriteLock();
        });
    }

    /**
     * Execute a write operation (fetch, clone) with exclusive lock.
     * Blocks until the lock is acquired.
     *
     * @param repositoryId the repository ID
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the result of the operation
     */
    public <T> T withWriteLock(Long repositoryId, Supplier<T> operation) {
        ReentrantReadWriteLock lock = getLock(repositoryId);
        lock.writeLock().lock();
        try {
            return operation.get();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Execute a write operation (fetch, clone) with exclusive lock.
     * Blocks until the lock is acquired.
     *
     * @param repositoryId the repository ID
     * @param operation the operation to execute
     */
    public void withWriteLock(Long repositoryId, Runnable operation) {
        ReentrantReadWriteLock lock = getLock(repositoryId);
        lock.writeLock().lock();
        try {
            operation.run();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Execute a read operation with shared lock.
     * Multiple readers can proceed concurrently.
     *
     * @param repositoryId the repository ID
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the result of the operation
     */
    public <T> T withReadLock(Long repositoryId, Supplier<T> operation) {
        ReentrantReadWriteLock lock = getLock(repositoryId);
        lock.readLock().lock();
        try {
            return operation.get();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Remove the lock for a repository (e.g., when repository is deleted).
     * Should only be called when no operations are in progress.
     */
    public void removeLock(Long repositoryId) {
        locks.remove(repositoryId);
    }

    /**
     * Returns the number of repositories with active locks.
     * Useful for monitoring/debugging.
     */
    public int getActiveLockCount() {
        return locks.size();
    }

    /**
     * Evict idle locks (not currently held by any thread) when the map
     * exceeds {@link #MAX_LOCKS}. Best-effort: races are harmless because
     * {@code computeIfAbsent} will simply recreate the lock on next access.
     */
    private void evictIdleLocksIfNeeded() {
        if (locks.size() < MAX_LOCKS) {
            return;
        }

        int before = locks.size();
        locks
            .entrySet()
            .removeIf(entry -> {
                ReentrantReadWriteLock lock = entry.getValue();
                // Only evict if nobody is reading or writing
                return !lock.isWriteLocked() && lock.getReadLockCount() == 0;
            });

        int evicted = before - locks.size();
        if (evicted > 0) {
            log.info("Evicted {} idle repository locks (was {} / max {})", evicted, before, MAX_LOCKS);
        }
    }
}
