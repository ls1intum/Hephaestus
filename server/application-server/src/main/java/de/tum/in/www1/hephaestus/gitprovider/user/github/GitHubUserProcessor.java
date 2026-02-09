package de.tum.in.www1.hephaestus.gitprovider.user.github;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DeadlockLoserDataAccessException;
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
 * <li>Idempotent operations via find-or-create pattern</li>
 * <li>Works exclusively with DTOs for complete field coverage</li>
 * </ul>
 */
@Service
public class GitHubUserProcessor {

    private static final Logger log = LoggerFactory.getLogger(GitHubUserProcessor.class);

    private final UserRepository userRepository;
    private final EntityManager entityManager;

    public GitHubUserProcessor(UserRepository userRepository, EntityManager entityManager) {
        this.userRepository = userRepository;
        this.entityManager = entityManager;
    }

    /**
     * Find an existing user or create a new one from the DTO.
     * <p>
     * This method is idempotent and thread-safe - calling it multiple times with the same DTO
     * will return the same user entity.
     * <p>
     * Uses a "find-first-then-insert" pattern to avoid deadlocks:
     * 1. Try to find the user by ID (fast path, no locks for most cases)
     * 2. If not found, try INSERT ON CONFLICT DO NOTHING (avoids UPDATE lock contention)
     * 3. Fetch and update any additional fields
     * <p>
     * This approach handles concurrent inserts gracefully without deadlocks because:
     * - ON CONFLICT DO NOTHING doesn't try to UPDATE, avoiding lock escalation
     * - Multiple transactions inserting the same user will all succeed (one inserts, others skip)
     * - Updates happen on fetched entities without competing for insert locks
     *
     * @param dto the GitHub user DTO
     * @return the User entity, or null if dto is null or has no ID
     */
    @Nullable
    @Transactional
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

        // Fast path: check if user already exists (most common case, no write locks)
        User existingUser = userRepository.findById(userId).orElse(null);

        if (existingUser == null) {
            // User doesn't exist - try to insert with ON CONFLICT DO NOTHING
            // This avoids deadlocks because DO NOTHING doesn't acquire update locks
            try {
                userRepository.insertIgnore(userId, login, name, avatarUrl, htmlUrl, userType.name());
            } catch (DataIntegrityViolationException e) {
                // Login conflict: another user has this login (common when users rename)
                // This happens when:
                // 1. User A had login "alice", renamed to "bob"
                // 2. User B (new) took login "alice"
                // 3. We're processing User B but User A still has "alice" in our DB
                //
                // Also happens with GitHub Apps vs Users sharing a login (e.g., "Copilot")
                //
                // Solution: Free up the login by renaming the old user
                if (isLoginConflict(e)) {
                    handleLoginConflict(userId, login, name, avatarUrl, htmlUrl, userType.name());
                } else {
                    // Some other constraint violation - rethrow
                    throw e;
                }
            } catch (CannotAcquireLockException | DeadlockLoserDataAccessException e) {
                // Rare case: concurrent insert caused a lock issue
                // Just log and continue - the user should exist now from the other transaction
                log.debug(
                    "User insert encountered lock contention, will fetch existing: userId={}, login={}",
                    userId,
                    login
                );
            }
        }

        // Fetch the entity and update any fields that may have changed
        return userRepository
            .findById(userId)
            .map(user -> updateUserFields(user, login, name, avatarUrl, htmlUrl, userType, dto))
            .orElse(null);
    }

    /**
     * Update user fields from the DTO, handling login conflicts gracefully.
     * <p>
     * If the login has changed, this method first frees up the login from any other
     * user who currently holds it (via native SQL to avoid L1 cache issues). If the
     * save still fails due to a login constraint violation (race condition between
     * freeLogin and save), it retries once after freeing the login again and
     * refreshing the entity from the database.
     * <p>
     * This handles cases like:
     * <ul>
     *   <li>GitHub Apps and Users sharing a login (e.g., "Copilot" AI user vs
     *       copilot-pull-request-reviewer bot)</li>
     *   <li>Users renaming accounts and another user taking the old name</li>
     *   <li>Concurrent webhook processing for entities with the same login</li>
     * </ul>
     */
    private User updateUserFields(
        User user,
        String login,
        String name,
        String avatarUrl,
        String htmlUrl,
        User.Type userType,
        GitHubUserDTO dto
    ) {
        boolean changed = applyFieldUpdates(user, login, name, avatarUrl, htmlUrl, userType, dto);
        if (!changed) {
            return user;
        }

        try {
            return userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            if (!isLoginConflict(e)) {
                throw e;
            }
            // Login conflict during save: another user holds this login.
            // This can happen when:
            // 1. freeLogin ran but a concurrent transaction inserted a new user with
            //    the same login between freeLogin and save
            // 2. The conflicting entity was in the Hibernate L1 cache and its stale
            //    state was flushed, reverting the native SQL rename
            //
            // Fix: free the login again, clear the persistence context to evict stale
            // entities, re-fetch our user, re-apply fields, and retry the save.
            log.info("Login conflict during user update, retrying: userId={}, login={}", user.getId(), login);
            freeLoginIfOwnedByOtherUser(user.getId(), login);
            entityManager.clear();

            User refreshedUser = userRepository.findById(user.getId()).orElse(null);
            if (refreshedUser == null) {
                log.warn("User disappeared after login conflict retry: userId={}", user.getId());
                return null;
            }
            applyFieldUpdates(refreshedUser, login, name, avatarUrl, htmlUrl, userType, dto);
            return userRepository.save(refreshedUser);
        }
    }

    /**
     * Apply field updates to a user entity, returning whether any field changed.
     * <p>
     * If the login is changing, preemptively frees the login from any other user
     * via {@link #freeLoginIfOwnedByOtherUser} before setting it on this entity.
     */
    private boolean applyFieldUpdates(
        User user,
        String login,
        String name,
        String avatarUrl,
        String htmlUrl,
        User.Type userType,
        GitHubUserDTO dto
    ) {
        boolean changed = false;
        if (!login.equals(user.getLogin())) {
            // Before changing the login, check if another user already owns it.
            // This handles GitHub entities that share a login across different IDs
            // (e.g., Copilot AI user vs copilot-pull-request-reviewer bot), or
            // users who renamed and another took their old username.
            freeLoginIfOwnedByOtherUser(user.getId(), login);
            user.setLogin(login);
            changed = true;
        }
        if (!name.equals(user.getName())) {
            user.setName(name);
            changed = true;
        }
        if (!avatarUrl.equals(user.getAvatarUrl())) {
            user.setAvatarUrl(avatarUrl);
            changed = true;
        }
        if (!htmlUrl.equals(user.getHtmlUrl())) {
            user.setHtmlUrl(htmlUrl);
            changed = true;
        }
        if (userType != user.getType()) {
            user.setType(userType);
            changed = true;
        }
        if (dto.email() != null && !dto.email().equals(user.getEmail())) {
            user.setEmail(dto.email());
            changed = true;
        }
        if (dto.createdAt() != null && !dto.createdAt().equals(user.getCreatedAt())) {
            user.setCreatedAt(dto.createdAt());
            changed = true;
        }
        if (dto.updatedAt() != null && !dto.updatedAt().equals(user.getUpdatedAt())) {
            user.setUpdatedAt(dto.updatedAt());
            changed = true;
        }
        return changed;
    }

    /**
     * Ensure a user exists, creating if necessary.
     * <p>
     * Convenience method that extracts the user ID from the DTO and ensures
     * the user exists in the database.
     *
     * @param dto the GitHub user DTO
     * @return the User entity, or null if dto is null or has no ID
     */
    @Nullable
    @Transactional
    public User ensureExists(GitHubUserDTO dto) {
        return findOrCreate(dto);
    }

    /**
     * Free up a login if it's currently owned by a different user.
     * <p>
     * This handles the case where an existing user's login needs to change to a value
     * that another row already has. For example, GitHub bot accounts like "Copilot" may
     * appear with different internal IDs across installations, or a user renames and
     * another user takes the old name.
     * <p>
     * Uses an atomic native query to rename the conflicting user without loading it
     * into the Hibernate session, avoiding stale-entity issues during flush.
     *
     * @param currentUserId the ID of the user we're updating (should keep the login)
     * @param login the desired login value
     */
    private void freeLoginIfOwnedByOtherUser(Long currentUserId, String login) {
        int updated = userRepository.freeLogin(login, currentUserId);
        if (updated > 0) {
            log.info(
                "Freed up login during update: login={}, targetUserId={}, conflictsResolved={}",
                login,
                currentUserId,
                updated
            );
        }
    }

    /**
     * Check if the exception is a login unique constraint violation.
     */
    private boolean isLoginConflict(DataIntegrityViolationException e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        // Check for the constraint name or login column reference
        return message.contains("uk_user_login") || message.contains("login");
    }

    /**
     * Handle a login conflict during INSERT by freeing up the login from the old user.
     * <p>
     * When a user renames their GitHub account, another user can take their old username.
     * Also handles cases where GitHub entities share a login across different IDs (e.g.,
     * the Copilot AI user and the copilot-pull-request-reviewer bot both use "Copilot").
     * <p>
     * Uses {@link UserRepository#freeLogin} (native SQL) instead of loading the
     * conflicting entity via findByLogin to avoid polluting the Hibernate L1 cache
     * with a stale entity that could interfere with subsequent flush operations.
     */
    private void handleLoginConflict(
        Long userId,
        String login,
        String name,
        String avatarUrl,
        String htmlUrl,
        String type
    ) {
        // Free up the login atomically via native SQL without loading the conflicting
        // entity into the Hibernate session. This avoids the stale-entity problem where
        // findByLogin would cache the old user, updateLogin would rename it at SQL level,
        // but Hibernate's flush would try to sync the stale cached state back.
        int freed = userRepository.freeLogin(login, userId);
        if (freed > 0) {
            log.info(
                "Freed up login for new user insert: login={}, newUserId={}, conflictsResolved={}",
                login,
                userId,
                freed
            );
        } else {
            // Concurrent modification - the conflict may have been resolved by another thread
            log.debug("Login conflict resolved concurrently: login={}", login);
        }

        // Retry the insert
        try {
            userRepository.insertIgnore(userId, login, name, avatarUrl, htmlUrl, type);
        } catch (DataIntegrityViolationException e) {
            // Still failing - might be a race condition or different constraint
            log.warn("Insert still failed after freeing login: userId={}, login={}", userId, login, e);
        }
    }
}
