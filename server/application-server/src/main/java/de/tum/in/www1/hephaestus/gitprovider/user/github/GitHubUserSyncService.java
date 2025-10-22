package de.tum.in.www1.hephaestus.gitprovider.user.github;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import jakarta.transaction.Transactional;
import java.io.IOException;
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
     * @return The updated or newly created User entity.
     */
    @Transactional
    public User processUser(GHUser ghUser) {
        var user = userRepository
            .findById(ghUser.getId())
            .map(existingUser -> userConverter.update(ghUser, existingUser))
            .orElseGet(() -> userConverter.convert(ghUser));

        return userRepository.save(user);
    }
}
