package de.tum.in.www1.hephaestus.gitprovider.user.github;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
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

    public GitHubUserProcessor(UserRepository userRepository) {
        this.userRepository = userRepository;
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
            .map(user -> {
                boolean changed = false;
                // Update core fields if they've changed
                if (!login.equals(user.getLogin())) {
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
                // Update additional profile fields
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
                if (changed) {
                    return userRepository.save(user);
                }
                return user;
            })
            .orElse(null);
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
     * Handle a login conflict by freeing up the login from the old user.
     * <p>
     * When a user renames their GitHub account, another user can take their old username.
     * This method handles that case by:
     * 1. Finding the user who currently has the conflicting login
     * 2. Renaming that user's login to a placeholder (RENAMED_&lt;id&gt;)
     * 3. Retrying the insert for the new user
     */
    private void handleLoginConflict(
        Long userId,
        String login,
        String name,
        String avatarUrl,
        String htmlUrl,
        String type
    ) {
        // Find the user who has this login
        User oldUser = userRepository.findByLogin(login).orElse(null);
        if (oldUser == null) {
            // Concurrent modification - the conflict may have been resolved
            log.debug("Login conflict resolved concurrently: login={}", login);
            return;
        }

        if (oldUser.getId().equals(userId)) {
            // Same user - this shouldn't happen but handle it gracefully
            log.debug("Login conflict was for same user: userId={}, login={}", userId, login);
            return;
        }

        // Rename the old user's login to free up the username
        String renamedLogin = "RENAMED_" + oldUser.getId();
        log.info(
            "Freeing up login for renamed user: oldUserId={}, oldLogin={}, newLogin={}, newUserId={}",
            oldUser.getId(),
            login,
            renamedLogin,
            userId
        );
        userRepository.updateLogin(oldUser.getId(), renamedLogin);

        // Retry the insert
        try {
            userRepository.insertIgnore(userId, login, name, avatarUrl, htmlUrl, type);
        } catch (DataIntegrityViolationException e) {
            // Still failing - might be a race condition or different constraint
            log.warn("Insert still failed after freeing login: userId={}, login={}", userId, login, e);
        }
    }
}
