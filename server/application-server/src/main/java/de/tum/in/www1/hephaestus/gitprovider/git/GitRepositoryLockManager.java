package de.tum.in.www1.hephaestus.gitprovider.git;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
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
 * If horizontal scaling is needed later, this can be replaced with
 * distributed locking (pg_advisory_lock or Redis SETNX).
 */
@Service
public class GitRepositoryLockManager {

    private final ConcurrentHashMap<Long, ReentrantReadWriteLock> locks = new ConcurrentHashMap<>();

    /**
     * Get or create a lock for the given repository.
     * Locks are keyed by repository database ID for uniqueness.
     */
    public ReentrantReadWriteLock getLock(Long repositoryId) {
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
}
