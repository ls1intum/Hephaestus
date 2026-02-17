package de.tum.in.www1.hephaestus.gitprovider.user.github;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processor for GitHub users.
 * <p>
 * This service handles the conversion of GitHubUserDTO to User entities
 * and persists them. It provides a single processing path for all user-related
 * operations from webhooks and sync services.
 * <p>
 * <b>Design Principles:</b>
 * <ul>
 * <li>Single processing path for all data sources (sync and webhooks)</li>
 * <li>Idempotent operations via native SQL upsert (INSERT ON CONFLICT DO UPDATE)</li>
 * <li>Works exclusively with DTOs for complete field coverage</li>
 * <li>All operations use default REQUIRED propagation — joins the caller's
 *     transaction if one exists, or creates a new one when called from
 *     non-transactional contexts (e.g., webhook message handlers)</li>
 * </ul>
 *
 * <b>Concurrency Strategy:</b>
 * <p>
 * User upserts use three separate SQL statements within the caller's transaction:
 * <ol>
 *   <li>Try to acquire a transaction-scoped advisory lock on the login using
 *       {@code pg_try_advisory_xact_lock} (non-blocking to prevent deadlocks)</li>
 *   <li>Rename any other user holding the target login (to {@code RENAMED_<id>})</li>
 *   <li>Insert or update the user via {@code INSERT ... ON CONFLICT (id) DO UPDATE}</li>
 * </ol>
 * These are separate statements (not a CTE) because PostgreSQL data-modifying
 * CTEs all operate on the same snapshot — the INSERT would not see the UPDATE's
 * effect and would still hit the unique constraint. Separate statements each see
 * the results of the previous statement within the same transaction.
 * <p>
 * <b>Deadlock Prevention:</b> Uses {@code pg_try_advisory_xact_lock} instead of
 * the blocking {@code pg_advisory_xact_lock}. When two threads process overlapping
 * sets of users in different orders (e.g., NATS webhook thread A processes users
 * [alice, bob] while sync thread B processes [bob, alice]), blocking locks create
 * an ABBA deadlock cycle. Non-blocking try-locks with retry break the cycle because
 * a thread that can't acquire the lock sleeps briefly and retries, rather than
 * blocking in PostgreSQL's wait queue.
 */
@Service
@Transactional
public class GitHubUserProcessor {

    private static final Logger log = LoggerFactory.getLogger(GitHubUserProcessor.class);

    /** Maximum number of attempts to acquire the advisory lock before falling back. */
    private static final int MAX_LOCK_ATTEMPTS = 5;

    /** Base delay between lock acquisition attempts (with jitter). */
    private static final long LOCK_RETRY_BASE_MS = 50;

    private final UserRepository userRepository;
    private final EntityManager entityManager;

    public GitHubUserProcessor(UserRepository userRepository, EntityManager entityManager) {
        this.userRepository = userRepository;
        this.entityManager = entityManager;
    }

    /**
     * Find an existing user or create a new one from the DTO.
     * <p>
     * This method is idempotent — calling it multiple times with the same DTO
     * will return the same user entity. Uses default REQUIRED propagation:
     * joins the caller's transaction if one exists, or creates a new one
     * (e.g., when called from non-transactional message handlers).
     * <p>
     * Uses three separate SQL statements (not a CTE) within the caller's transaction:
     * <ol>
     *   <li>Try to acquire advisory lock on the login (non-blocking, with retry)</li>
     *   <li>Rename any other user that currently holds the target login</li>
     *   <li>INSERT ... ON CONFLICT (id) DO UPDATE the user</li>
     * </ol>
     * Separate statements are required because PostgreSQL data-modifying CTEs
     * all operate on the same snapshot — the INSERT would not see the UPDATE's
     * effect and would still violate the unique constraint on login.
     *
     * @param dto the GitHub user DTO
     * @return the User entity (managed in caller's persistence context),
     *     or null if dto is null or has no ID
     */
    @Nullable
    public User findOrCreate(GitHubUserDTO dto) {
        if (dto == null) {
            return null;
        }
        Long userId = dto.getDatabaseId();
        if (userId == null) {
            log.debug("Skipped user processing: reason=missingDatabaseId, userLogin={}", dto.login());
            return null;
        }

        String login = dto.login();
        String name = dto.name() != null ? dto.name() : login;
        String avatarUrl = dto.avatarUrl() != null ? dto.avatarUrl() : "";
        String htmlUrl = dto.htmlUrl() != null ? dto.htmlUrl() : "";
        User.Type userType = dto.getEffectiveType();

        // Flush any pending Hibernate changes before the native SQL to ensure
        // consistency between Hibernate's cache and the database.
        entityManager.flush();

        // Step 1: Try to acquire advisory lock (non-blocking to prevent deadlocks).
        // If another transaction holds the lock, retry with jitter backoff.
        // After MAX_LOCK_ATTEMPTS, fall through and attempt the upsert anyway —
        // the INSERT ON CONFLICT handles concurrent inserts gracefully.
        boolean lockAcquired = tryAcquireWithRetry(login);

        if (lockAcquired) {
            // Step 2: Rename any other user that holds this login (different id).
            // This is a separate statement so that step 3 sees the update.
            userRepository.freeLoginConflicts(login, userId);
        } else {
            log.debug(
                "Could not acquire advisory lock after {} attempts, proceeding with upsert: login={}",
                MAX_LOCK_ATTEMPTS,
                login
            );
        }

        // Step 3: Insert or update the user. When the lock was acquired, the login
        // conflict has been resolved by step 2. When the lock was not acquired,
        // the upsert may fail with a unique constraint violation if there is a login
        // conflict — this is acceptable because the caller (NATS/sync) will retry.
        userRepository.upsertUser(
            userId,
            login,
            name,
            avatarUrl,
            htmlUrl,
            userType.name(),
            dto.email(),
            dto.createdAt(),
            dto.updatedAt()
        );

        // Evict from L1 cache so the next find() returns fresh data from DB
        // instead of a stale cached version.
        User cached = entityManager.find(User.class, userId);
        if (cached != null) {
            entityManager.refresh(cached);
        }

        // Load the entity into the persistence context. We use find() instead of
        // returning a detached entity because callers set JPA associations
        // (e.g., comment.setAuthor(user)) which require managed entities.
        //
        // The find() call uses the L1 cache — since we just refreshed above,
        // it returns the up-to-date instance without an extra DB round-trip.
        User result = entityManager.find(User.class, userId);
        if (result == null) {
            // Should not happen after a successful upsert, but handle gracefully
            log.warn("User not found after upsert: userId={}, login={}", userId, login);
        }
        return result;
    }

    /**
     * Attempts to acquire the advisory lock with retry and jitter.
     * <p>
     * Uses {@code pg_try_advisory_xact_lock} which returns immediately (never blocks).
     * If the lock is held by another transaction, waits briefly with randomized jitter
     * before retrying. This prevents the ABBA deadlock pattern that occurs when
     * blocking locks are acquired in different orders by concurrent transactions.
     *
     * @param login the login to lock on
     * @return true if the lock was acquired, false after all attempts exhausted
     */
    private boolean tryAcquireWithRetry(String login) {
        for (int attempt = 0; attempt < MAX_LOCK_ATTEMPTS; attempt++) {
            if (userRepository.tryAcquireLoginLock(login)) {
                return true;
            }
            if (attempt < MAX_LOCK_ATTEMPTS - 1) {
                try {
                    // Jittered backoff: base * (attempt+1) + random [0, base)
                    long delay = LOCK_RETRY_BASE_MS * (attempt + 1) + (long) (Math.random() * LOCK_RETRY_BASE_MS);
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Ensure a user exists, creating if necessary.
     * <p>
     * Convenience method that delegates to {@link #findOrCreate(GitHubUserDTO)}.
     *
     * @param dto the GitHub user DTO
     * @return the User entity, or null if dto is null or has no ID
     */
    @Nullable
    public User ensureExists(GitHubUserDTO dto) {
        return findOrCreate(dto);
    }
}
