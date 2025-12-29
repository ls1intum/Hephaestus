package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.installation.github.dto.GitHubInstallationRepositoriesEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepositoryMonitorService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GitHub installation_repositories webhook events.
 * <p>
 * When repositories are added or removed from a GitHub App installation,
 * this handler updates the workspace's monitored repositories accordingly.
 */
@Component
public class GitHubInstallationRepositoriesMessageHandler
    extends GitHubMessageHandler<GitHubInstallationRepositoriesEventDTO> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubInstallationRepositoriesMessageHandler.class);

    private final WorkspaceRepositoryMonitorService workspaceRepositoryMonitorService;

    GitHubInstallationRepositoriesMessageHandler(
        NatsMessageDeserializer deserializer,
        WorkspaceRepositoryMonitorService workspaceRepositoryMonitorService
    ) {
        super(GitHubInstallationRepositoriesEventDTO.class, deserializer);
        this.workspaceRepositoryMonitorService = workspaceRepositoryMonitorService;
    }

    @Override
    protected String getEventKey() {
        return "installation_repositories";
    }

    @Override
    public GitHubMessageDomain getDomain() {
        return GitHubMessageDomain.INSTALLATION;
    }

    @Override
    @Transactional
    protected void handleEvent(GitHubInstallationRepositoriesEventDTO event) {
        var installation = event.installation();

        if (installation == null) {
            logger.warn("Received installation_repositories event with missing data");
            return;
        }

        List<GitHubRepositoryRefDTO> added = event.repositoriesAdded() != null ? event.repositoriesAdded() : List.of();
        List<GitHubRepositoryRefDTO> removed = event.repositoriesRemoved() != null
            ? event.repositoriesRemoved()
            : List.of();

        logger.info(
            "Received installation_repositories event: action={}, installation={}, added={}, removed={}",
            event.action(),
            installation.id(),
            added.size(),
            removed.size()
        );

        long installationId = installation.id();

        // Handle added repositories
        for (GitHubRepositoryRefDTO repo : added) {
            logger.info("Adding repository to monitor for installation {}: {}", installationId, repo.fullName());
            workspaceRepositoryMonitorService.ensureRepositoryMonitorForInstallation(installationId, repo.fullName());
        }

        // Handle removed repositories
        for (GitHubRepositoryRefDTO repo : removed) {
            logger.info("Removing repository from monitor for installation {}: {}", installationId, repo.fullName());
            workspaceRepositoryMonitorService.removeRepositoryMonitorForInstallation(installationId, repo.fullName());
        }
    }
}
