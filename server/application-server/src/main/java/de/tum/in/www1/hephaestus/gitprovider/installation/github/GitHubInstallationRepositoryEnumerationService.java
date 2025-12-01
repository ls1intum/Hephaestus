package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Enumerates repositories accessible to a GitHub App installation so we can backfill monitors.
 */
@Service
public class GitHubInstallationRepositoryEnumerationService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubInstallationRepositoryEnumerationService.class);

    private final GitHubAppTokenService gitHubAppTokenService;

    public GitHubInstallationRepositoryEnumerationService(GitHubAppTokenService gitHubAppTokenService) {
        this.gitHubAppTokenService = gitHubAppTokenService;
    }

    /**
     * Return a lightweight snapshot of every repository visible to the installation.
     * Falls back to an empty list if GitHub App credentials are not configured or enumeration fails.
     */
    public List<InstallationRepositorySnapshot> enumerate(long installationId) {
        if (!gitHubAppTokenService.isConfigured()) {
            logger.warn(
                "GitHub App credentials missing; cannot enumerate installation {} repositories.",
                installationId
            );
            return List.of();
        }

        try {
            GitHub installationClient = gitHubAppTokenService.clientForInstallation(installationId);
            return installationClient
                .getInstallation()
                .listRepositories()
                .withPageSize(100)
                .toList()
                .stream()
                .map(this::toSnapshot)
                .collect(Collectors.toList());
        } catch (IOException e) {
            logger.warn(
                "Failed to enumerate installation {} repositories via GitHub API: {}",
                installationId,
                e.getMessage()
            );
            logger.debug("GitHub installation enumeration failure", e);
            return List.of();
        }
    }

    private InstallationRepositorySnapshot toSnapshot(GHRepository repo) {
        return new InstallationRepositorySnapshot(repo.getId(), repo.getFullName(), repo.getName(), repo.isPrivate());
    }

    public record InstallationRepositorySnapshot(long id, String nameWithOwner, String name, boolean isPrivate) {}
}
