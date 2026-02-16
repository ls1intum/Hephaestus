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
     * <p>
     * Eviction runs <em>before</em> {@code computeIfAbsent} to avoid mutating the
     * ConcurrentHashMap from inside its own mapping function (which can cause
     * deadlock on bin-level locks in some JDK versions).
     */
    public ReentrantReadWriteLock getLock(Long repositoryId) {
        // Check existing first (fast path without eviction)
        ReentrantReadWriteLock existing = locks.get(repositoryId);
        if (existing != null) {
            return existing;
        }

        // Evict outside computeIfAbsent to avoid ConcurrentHashMap reentrancy
        evictIdleLocksIfNeeded();
        return locks.computeIfAbsent(repositoryId, k -> new ReentrantReadWriteLock());
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
     * Only removes the lock if it is not currently held by any thread.
     *
     * @return true if the lock was removed, false if it was still held or didn't exist
     */
    public boolean removeLock(Long repositoryId) {
        return (
            locks.computeIfPresent(repositoryId, (k, lock) -> {
                if (!lock.isWriteLocked() && lock.getReadLockCount() == 0) {
                    return null; // removes entry
                }
                log.warn("Cannot remove lock for repository {}: still held", repositoryId);
                return lock; // keep it
            }) ==
            null
        );
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
     * <p>
     * KNOWN LIMITATION: eviction can remove a lock entry between another
     * thread's {@code getLock()} fast-path miss and its
     * {@code computeIfAbsent}, creating two distinct lock objects for the
     * same repository. This is theoretical — it requires &gt;10,000 repos
     * AND eviction running at the exact nanosecond another thread acquires
     * the same lock. At that scale, consider switching to distributed
     * locking (pg_advisory_lock or Redis).
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
