package de.tum.in.www1.hephaestus.gitprovider.user.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

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

    private static final Logger logger = LoggerFactory.getLogger(GitHubUserProcessor.class);

    private final UserRepository userRepository;

    public GitHubUserProcessor(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Find an existing user or create a new one from the DTO.
     * <p>
     * This method is idempotent - calling it multiple times with the same DTO
     * will return the same user entity. Profile fields (bio, company, location,
     * blog, followers, following) are synced when available in the DTO.
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
            logger.debug("User DTO has no database ID, skipping");
            return null;
        }
        return userRepository
            .findById(userId)
            .map(existing -> updateProfileFields(existing, dto))
            .orElseGet(() -> {
                User user = new User();
                user.setId(userId);
                user.setLogin(dto.login());
                user.setAvatarUrl(dto.avatarUrl() != null ? dto.avatarUrl() : "");
                // Use login as fallback for name if null (name is @NonNull)
                user.setName(dto.name() != null ? dto.name() : dto.login());
                user.setHtmlUrl(dto.htmlUrl() != null ? dto.htmlUrl() : "");
                user.setType(User.Type.USER);
                // Sync profile fields from DTO
                syncProfileFieldsToUser(user, dto);
                User saved = userRepository.save(user);
                logger.debug("Created user {} ({})", sanitizeForLog(saved.getLogin()), saved.getId());
                return saved;
            });
    }

    /**
     * Updates profile fields on an existing user if the DTO has richer data.
     * <p>
     * Only updates fields that are non-null in the DTO to avoid overwriting
     * existing data with null from minimal webhook payloads.
     *
     * @param existing the existing user entity
     * @param dto the DTO with potentially updated profile data
     * @return the updated user (saved if changed)
     */
    private User updateProfileFields(User existing, GitHubUserDTO dto) {
        boolean changed = false;

        // Update basic fields that might have changed
        if (dto.avatarUrl() != null && !dto.avatarUrl().equals(existing.getAvatarUrl())) {
            existing.setAvatarUrl(dto.avatarUrl());
            changed = true;
        }
        if (dto.name() != null && !dto.name().equals(existing.getName())) {
            existing.setName(dto.name());
            changed = true;
        }

        // Profile fields - only update if DTO has data (non-null means it came from full fetch)
        if (dto.bio() != null) {
            existing.setDescription(dto.bio());
            changed = true;
        }
        if (dto.company() != null) {
            existing.setCompany(dto.company());
            changed = true;
        }
        if (dto.location() != null) {
            existing.setLocation(dto.location());
            changed = true;
        }
        if (dto.blog() != null) {
            existing.setBlog(dto.blog());
            changed = true;
        }
        if (dto.email() != null && !dto.email().equals(existing.getEmail())) {
            existing.setEmail(dto.email());
            changed = true;
        }
        if (dto.followers() != null) {
            existing.setFollowers(dto.followers());
            changed = true;
        }
        if (dto.following() != null) {
            existing.setFollowing(dto.following());
            changed = true;
        }
        if (dto.createdAt() != null && !dto.createdAt().equals(existing.getCreatedAt())) {
            existing.setCreatedAt(dto.createdAt());
            changed = true;
        }
        if (dto.updatedAt() != null && !dto.updatedAt().equals(existing.getUpdatedAt())) {
            existing.setUpdatedAt(dto.updatedAt());
            changed = true;
        }

        if (changed) {
            logger.debug(
                "Updated profile fields for user {} ({})",
                sanitizeForLog(existing.getLogin()),
                existing.getId()
            );
            return userRepository.save(existing);
        }
        return existing;
    }

    /**
     * Syncs profile fields from DTO to a new User entity.
     */
    private void syncProfileFieldsToUser(User user, GitHubUserDTO dto) {
        if (dto.bio() != null) {
            user.setDescription(dto.bio());
        }
        if (dto.company() != null) {
            user.setCompany(dto.company());
        }
        if (dto.location() != null) {
            user.setLocation(dto.location());
        }
        if (dto.blog() != null) {
            user.setBlog(dto.blog());
        }
        if (dto.email() != null) {
            user.setEmail(dto.email());
        }
        if (dto.followers() != null) {
            user.setFollowers(dto.followers());
        }
        if (dto.following() != null) {
            user.setFollowing(dto.following());
        }
        if (dto.createdAt() != null) {
            user.setCreatedAt(dto.createdAt());
        }
        if (dto.updatedAt() != null) {
            user.setUpdatedAt(dto.updatedAt());
        }
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
