package de.tum.in.www1.hephaestus.gitprovider.user.github;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.HttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for synchronizing GitHub users with the local database.
 */
@Service
public class GitHubUserSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubUserSyncService.class);

    private final UserRepository userRepository;
    private final GitHubUserConverter userConverter;

    /** Cache of logins that returned 404 - avoids repeated API calls for deleted/invalid accounts */
    private final Set<String> missingLogins = ConcurrentHashMap.newKeySet();

    public GitHubUserSyncService(UserRepository userRepository, GitHubUserConverter userConverter) {
        this.userRepository = userRepository;
        this.userConverter = userConverter;
    }

    /**
     * Sync all existing users in the local repository with their GitHub data.
     */
    public void syncAllExistingUsers(GitHub github) {
        userRepository
            .findAll()
            .stream()
            .map(User::getLogin)
            .filter(login -> !isLoginMarkedMissing(login))
            .forEach(login -> syncUser(github, login));
    }

    /**
     * Sync a GitHub user's data by their login.
     *
     * @param github The GitHub client to use.
     * @param login The GitHub username (login) of the user to fetch.
     * @return The synced user, or null if not found/error.
     */
    public User syncUser(GitHub github, String login) {
        try {
            var user = processUser(github.getUser(login));
            missingLogins.remove(normalizeLogin(login));
            return user;
        } catch (GHFileNotFoundException e) {
            logger.info("Skipping user sync for '{}': user not found on GitHub.", login);
            markLoginMissing(login);
            return null;
        } catch (HttpException e) {
            if (e.getResponseCode() == 404) {
                logger.info("Skipping user sync for '{}': user not found on GitHub.", login);
                markLoginMissing(login);
                return null;
            }
            logger.warn("Failed to fetch user '{}': HTTP {} - {}", login, e.getResponseCode(), e.getMessage());
            return null;
        } catch (FileNotFoundException e) {
            logger.info("Skipping user sync for '{}': user not found on GitHub.", login);
            markLoginMissing(login);
            return null;
        } catch (IOException e) {
            logger.warn("Failed to fetch user '{}': {}", login, e.getMessage());
            return null;
        }
    }

    /**
     * Processes a fully-fetched GitHub user by either updating or creating in the repository.
     * <p>
     * Uses {@link GitHubUserConverter#updateFromFullUser} which accesses ALL user fields.
     * Should ONLY be called with users from {@code GitHub.getUser(login)}.
     *
     * @param ghUser The fully-fetched GitHub user data.
     * @return The updated or newly created User entity.
     */
    @Transactional
    public User processUser(GHUser ghUser) {
        var user = userRepository
            .findById(ghUser.getId())
            .map(existingUser -> userConverter.updateFromFullUser(ghUser, existingUser))
            .orElseGet(() -> {
                User newUser = new User();
                return userConverter.updateFromFullUser(ghUser, newUser);
            });

        return userRepository.save(user);
    }

    /**
     * Gets an existing user by ID or creates a new one from basic GHUser data.
     * <p>
     * Uses only the basic fields available in webhook payloads (id, login, avatar_url, type).
     * For full user data sync, use {@link #processUser(GHUser)} instead.
     *
     * @param ghUser The GitHub user from a webhook payload or API response.
     * @return The existing or newly created User entity.
     */
    @Transactional
    public User getOrCreateUser(GHUser ghUser) {
        return userRepository
            .findById(ghUser.getId())
            .orElseGet(() -> userRepository.save(userConverter.convert(ghUser)));
    }

    private boolean isLoginMarkedMissing(String login) {
        var normalized = normalizeLogin(login);
        return normalized != null && missingLogins.contains(normalized);
    }

    private void markLoginMissing(String login) {
        var normalized = normalizeLogin(login);
        if (normalized != null) {
            missingLogins.add(normalized);
        }
    }

    private String normalizeLogin(String login) {
        return login == null ? null : login.trim().toLowerCase(Locale.ROOT);
    }
}
