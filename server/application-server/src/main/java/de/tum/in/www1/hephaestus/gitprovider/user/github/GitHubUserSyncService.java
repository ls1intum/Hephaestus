package de.tum.in.www1.hephaestus.gitprovider.user.github;

import org.kohsuke.github.GitHub;
import org.springframework.stereotype.Service;

import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;

@Service
public class GitHubUserSyncService {

    private final GitHub github;
    private final UserRepository userRepository;

    public GitHubUserSyncService(GitHub github, UserRepository userRepository) {
        this.github = github;
        this.userRepository = userRepository;
    }

    // TODO: github.listUsers()

    public void syncAllUsers() {
        // ...
    }

    public void syncUser(String username) {

    }
}
