package de.tum.in.www1.hephaestus.gitprovider.user.github;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
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
     * will return the same user entity. Uses PostgreSQL upsert to avoid race conditions.
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

        // Use upsert for thread-safe concurrent inserts
        userRepository.upsert(userId, login, name, avatarUrl, htmlUrl, User.Type.USER.name());

        // Fetch the entity to update additional profile fields if needed
        return userRepository.findById(userId).map(user -> {
            boolean changed = false;
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
        }).orElse(null);
    }

    /**
     * Check if a user exists by ID.
     *
     * @param userId the user ID
     * @return true if the user exists
     */
    public boolean exists(Long userId) {
        return userId != null && userRepository.existsById(userId);
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
}
