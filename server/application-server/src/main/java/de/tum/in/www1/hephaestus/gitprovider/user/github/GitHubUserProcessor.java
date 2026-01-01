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
     * will return the same user entity.
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
            .orElseGet(() -> {
                User user = new User();
                user.setId(userId);
                user.setLogin(dto.login());
                user.setAvatarUrl(dto.avatarUrl() != null ? dto.avatarUrl() : "");
                // Use login as fallback for name if null (name is @NonNull)
                user.setName(dto.name() != null ? dto.name() : dto.login());
                user.setHtmlUrl(dto.htmlUrl() != null ? dto.htmlUrl() : "");
                user.setType(User.Type.USER);
                User saved = userRepository.save(user);
                logger.debug("Created user {} ({})", sanitizeForLog(saved.getLogin()), saved.getId());
                return saved;
            });
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
