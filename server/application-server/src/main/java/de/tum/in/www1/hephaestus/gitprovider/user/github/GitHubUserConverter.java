package de.tum.in.www1.hephaestus.gitprovider.user.github;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntityConverter;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import java.io.IOException;
import org.kohsuke.github.GHUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Converts GitHub user objects (GHUser) to the internal User entity.
 * <p>
 * <strong>IMPORTANT - Two Types of GHUser Objects:</strong>
 * <ol>
 *   <li><strong>Minimal users</strong> - From list operations (listCollaborators, listMembers, etc.)
 *       These only have basic fields populated. Calling most getter methods triggers an API call
 *       via {@code populate()} which fails with 401 when using GitHub App installation tokens.</li>
 *   <li><strong>Full users</strong> - From {@code GitHub.getUser(login)} or direct API fetches.
 *       These have all fields populated and all getter methods are safe to call.</li>
 * </ol>
 * <p>
 * <strong>Safe methods (never trigger API calls):</strong>
 * <ul>
 *   <li>{@code getId()}</li>
 *   <li>{@code getLogin()}</li>
 *   <li>{@code getAvatarUrl()}</li>
 *   <li>{@code getHtmlUrl()}</li>
 *   <li>{@code getBio()}</li>
 * </ul>
 * <p>
 * <strong>Unsafe methods (may trigger API calls on minimal users):</strong>
 * {@code getType()}, {@code getName()}, {@code getCompany()}, {@code getBlog()},
 * {@code getLocation()}, {@code getEmail()}, {@code getFollowersCount()},
 * {@code getFollowingCount()}, {@code getCreatedAt()}, {@code getUpdatedAt()}
 * <p>
 * Use {@link #update(GHUser, User)} for minimal users (safe).
 * Use {@link #updateFromFullUser(GHUser, User)} for fully-fetched users (accesses all fields).
 */
@Component
public class GitHubUserConverter extends BaseGitServiceEntityConverter<GHUser, User> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubUserConverter.class);

    @Override
    public User convert(@NonNull GHUser source) {
        return update(source, new User());
    }

    /**
     * Updates a User entity with data from a GHUser using ONLY safe methods.
     * <p>
     * This method is safe to call with minimal GHUser objects from list operations.
     * It will NOT trigger any additional API calls.
     *
     * @param source the GHUser (may be minimal or full)
     * @param user the User entity to update
     * @return the updated User entity
     */
    @Override
    public User update(@NonNull GHUser source, @NonNull User user) {
        // These methods are ALWAYS safe - they never call populate()
        user.setId(source.getId());
        user.setLogin(source.getLogin());
        user.setAvatarUrl(source.getAvatarUrl());
        user.setHtmlUrl(source.getHtmlUrl() != null ? source.getHtmlUrl().toString() : null);
        user.setDescription(source.getBio());

        // Default name to login if not already set
        if (user.getName() == null || user.getName().isEmpty()) {
            user.setName(source.getLogin());
        }

        // Default type to USER if not already set
        if (user.getType() == null) {
            user.setType(User.Type.USER);
        }

        return user;
    }

    /**
     * Updates a User entity with FULL data from a fully-fetched GHUser.
     * <p>
     * This method accesses all available fields including those that would trigger
     * {@code populate()} on minimal users. Only call this with users obtained from
     * {@code GitHub.getUser(login)} or similar direct fetch operations.
     *
     * @param source a fully-fetched GHUser (from GitHub.getUser() or similar)
     * @param user the User entity to update
     * @return the updated User entity
     */
    public User updateFromFullUser(@NonNull GHUser source, @NonNull User user) {
        // First apply the safe base update
        update(source, user);

        // Now access the extended fields that require a fully-populated user
        // Each is wrapped in try-catch because these methods throw IOException
        try {
            String type = source.getType();
            user.setType(convertUserType(type));
        } catch (IOException e) {
            logger.debug("Could not get type for user {}: {}", source.getLogin(), e.getMessage());
        }

        try {
            String name = source.getName();
            user.setName(name != null && !name.isEmpty() ? name : source.getLogin());
        } catch (IOException e) {
            logger.debug("Could not get name for user {}: {}", source.getLogin(), e.getMessage());
        }

        try {
            user.setCompany(source.getCompany());
        } catch (IOException e) {
            logger.debug("Could not get company for user {}: {}", source.getLogin(), e.getMessage());
        }

        try {
            user.setBlog(source.getBlog());
        } catch (IOException e) {
            logger.debug("Could not get blog for user {}: {}", source.getLogin(), e.getMessage());
        }

        try {
            user.setLocation(source.getLocation());
        } catch (IOException e) {
            logger.debug("Could not get location for user {}: {}", source.getLogin(), e.getMessage());
        }

        try {
            user.setEmail(source.getEmail());
        } catch (IOException e) {
            logger.debug("Could not get email for user {}: {}", source.getLogin(), e.getMessage());
        }

        try {
            user.setFollowers(source.getFollowersCount());
        } catch (IOException e) {
            logger.debug("Could not get followers for user {}: {}", source.getLogin(), e.getMessage());
        }

        try {
            user.setFollowing(source.getFollowingCount());
        } catch (IOException e) {
            logger.debug("Could not get following for user {}: {}", source.getLogin(), e.getMessage());
        }

        try {
            user.setCreatedAt(source.getCreatedAt());
        } catch (IOException e) {
            logger.debug("Could not get createdAt for user {}: {}", source.getLogin(), e.getMessage());
        }

        try {
            user.setUpdatedAt(source.getUpdatedAt());
        } catch (IOException e) {
            logger.debug("Could not get updatedAt for user {}: {}", source.getLogin(), e.getMessage());
        }

        return user;
    }

    private User.Type convertUserType(String type) {
        if (type == null) {
            return User.Type.USER;
        }
        return switch (type) {
            case "User" -> User.Type.USER;
            case "Organization" -> User.Type.ORGANIZATION;
            case "Bot" -> User.Type.BOT;
            default -> User.Type.USER;
        };
    }
}
