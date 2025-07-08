package de.tum.in.www1.hephaestus.gitprovider.user.github;

import de.tum.in.www1.hephaestus.gitprovider.common.DateUtil;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.util.Date;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GitHubUserSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubUserSyncService.class);

    private final GitHub github;
    private final UserRepository userRepository;
    private final GitHubUserConverter userConverter;

    public GitHubUserSyncService(GitHub github, UserRepository userRepository, GitHubUserConverter userConverter) {
        this.github = github;
        this.userRepository = userRepository;
        this.userConverter = userConverter;
    }

    /**
     * Sync all existing users in the local repository with their GitHub
     * data.
     */
    public void syncAllExistingUsers() {
        userRepository.findAll().stream().map(User::getLogin).forEach(this::syncUser);
    }

    /**
     * Sync a GitHub user's data by their login and processes it to synchronize
     * with the local repository.
     *
     * @param login The GitHub username (login) of the user to fetch.
     */
    public void syncUser(String login) {
        try {
            processUser(github.getUser(login));
        } catch (IOException e) {
            logger.error("Failed to fetch user {}: {}", login, e.getMessage());
        }
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
                    if (
                        user.getUpdatedAt() == null ||
                        user.getUpdatedAt().isBefore(DateUtil.convertToOffsetDateTime(Date.from(ghUser.getUpdatedAt())))
                    ) {
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
}
