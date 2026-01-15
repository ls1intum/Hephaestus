package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ProvisioningListener;
import de.tum.in.www1.hephaestus.gitprovider.installation.github.dto.GitHubInstallationRepositoriesEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GitHub installation_repositories webhook events.
 * <p>
 * When repositories are added or removed from a GitHub App installation,
 * this handler notifies the consuming module via SPI to update monitored repositories.
 */
@Component
public class GitHubInstallationRepositoriesMessageHandler
    extends GitHubMessageHandler<GitHubInstallationRepositoriesEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitHubInstallationRepositoriesMessageHandler.class);

    private final ProvisioningListener provisioningListener;

    GitHubInstallationRepositoriesMessageHandler(
        NatsMessageDeserializer deserializer,
        ProvisioningListener provisioningListener
    ) {
        super(GitHubInstallationRepositoriesEventDTO.class, deserializer);
        this.provisioningListener = provisioningListener;
    }

    @Override
    public GitHubEventType getEventType() {
        return GitHubEventType.INSTALLATION_REPOSITORIES;
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
            log.warn("Received installation_repositories event with missing data: action={}", event.action());
            return;
        }

        List<GitHubRepositoryRefDTO> added = event.repositoriesAdded() != null ? event.repositoriesAdded() : List.of();
        List<GitHubRepositoryRefDTO> removed = event.repositoriesRemoved() != null
            ? event.repositoriesRemoved()
            : List.of();

        log.info(
            "Received installation_repositories event: action={}, installationId={}, addedCount={}, removedCount={}",
            event.action(),
            installation.id(),
            added.size(),
            removed.size()
        );

        long installationId = installation.id();

        // Notify consuming module via SPI for added repositories
        if (!added.isEmpty()) {
            List<String> addedNames = added.stream().map(GitHubRepositoryRefDTO::fullName).toList();
            provisioningListener.onRepositoriesAdded(installationId, addedNames);
            log.info("Added repositories to installation: installationId={}, repoCount={}", installationId, addedNames.size());
        }

        // Notify consuming module via SPI for removed repositories
        if (!removed.isEmpty()) {
            List<String> removedNames = removed.stream().map(GitHubRepositoryRefDTO::fullName).toList();
            provisioningListener.onRepositoriesRemoved(installationId, removedNames);
            log.info("Removed repositories from installation: installationId={}, repoCount={}", installationId, removedNames.size());
        }
    }
}
