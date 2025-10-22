package de.tum.in.www1.hephaestus.gitprovider.user.github;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import jakarta.transaction.Transactional;
import java.io.IOException;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
public class GitHubUserSyncService {

    private GitHubUserSyncService self;

    private static final Logger logger = LoggerFactory.getLogger(GitHubUserSyncService.class);

    private final UserRepository userRepository;
    private final GitHubUserConverter userConverter;

    public GitHubUserSyncService(UserRepository userRepository, GitHubUserConverter userConverter) {
        this.userRepository = userRepository;
        this.userConverter = userConverter;
    }

    /**
     * Sync all existing users in the local repository with their GitHub
     * data.
     */
    public void syncAllExistingUsers(GitHub github) {
        userRepository.findAll().stream().map(User::getLogin).forEach(login -> syncUser(github, login));
    }

    /**
     * Sync a GitHub user's data by their login and processes it to synchronize
     * with the local repository.
     *
     * @param login The GitHub username (login) of the user to fetch.
     */
    public User syncUser(GitHub github, String login) {
        try {
            return self.processUser(github.getUser(login));
        } catch (IOException e) {
            logger.error("Failed to fetch user {}: {}", login, e.getMessage());
        }
        return null;
    }

    /**
     * Processes a GitHub user by either updating the existing user in the
     * repository or creating a new one.
     *
     * @param ghUser The GitHub user data to process.
     * @return The updated or newly created User entity, or {@code null} if an error
     *         occurred during update.
     */
    @Transactional
    public User processUser(GHUser ghUser) {
        var result = userRepository
            .findById(ghUser.getId())
            .map(user -> {
                try {
                    if (user.getUpdatedAt() == null || user.getUpdatedAt().isBefore(ghUser.getUpdatedAt())) {
                        return userConverter.update(ghUser, user);
                    }
                    return user;
                } catch (IOException e) {
                    logger.error("Failed to update repository {}: {}", ghUser.getId(), e.getMessage());
                    return null;
                }
            })
            .orElseGet(() -> userConverter.convert(ghUser));

        if (result == null) {
            return null;
        }

        return userRepository.save(result);
    }

    @Autowired
    @Lazy
    public void setSelf(GitHubUserSyncService self) {
        this.self = self;
    }
}
