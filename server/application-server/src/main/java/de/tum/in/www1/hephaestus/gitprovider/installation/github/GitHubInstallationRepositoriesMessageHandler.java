package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.installation.github.dto.GitHubInstallationRepositoriesEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles GitHub installation_repositories webhook events.
 * <p>
 * Uses DTOs directly (no hub4j) for complete field coverage.
 */
@Component
public class GitHubInstallationRepositoriesMessageHandler
    extends GitHubMessageHandler<GitHubInstallationRepositoriesEventDTO> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubInstallationRepositoriesMessageHandler.class);

    GitHubInstallationRepositoriesMessageHandler() {
        super(GitHubInstallationRepositoriesEventDTO.class);
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

        // TODO: Implement repository add/remove when services are migrated
        for (GitHubRepositoryRefDTO repo : added) {
            logger.info("Repository added to installation: {}", repo.fullName());
        }
        for (GitHubRepositoryRefDTO repo : removed) {
            logger.info("Repository removed from installation: {}", repo.fullName());
        }
    }
}
