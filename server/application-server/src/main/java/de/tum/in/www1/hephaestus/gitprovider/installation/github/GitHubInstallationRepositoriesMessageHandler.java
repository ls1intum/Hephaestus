package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles GitHub App installation_repositories events (repositories added or removed from an installation).
 */
@Component
public class GitHubInstallationRepositoriesMessageHandler
    extends GitHubMessageHandler<GHEventPayload.InstallationRepositories> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubInstallationRepositoriesMessageHandler.class);

    private final GitHubRepositorySyncService repositorySyncService;

    public GitHubInstallationRepositoriesMessageHandler(GitHubRepositorySyncService repositorySyncService) {
        super(GHEventPayload.InstallationRepositories.class);
        this.repositorySyncService = repositorySyncService;
    }

    @Override
    protected void handleEvent(GHEventPayload.InstallationRepositories payload) {
        var action = payload.getAction();
        var installation = payload.getInstallation();
        var added = payload.getRepositoriesAdded();
        var removed = payload.getRepositoriesRemoved();

        logger.info(
            "Received installation_repositories event: action={}, appId={}, added={}, removed={}",
            action,
            installation != null ? installation.getAppId() : null,
            added != null ? added.size() : 0,
            removed != null ? removed.size() : 0
        );

        // Upsert any added repositories
        if (added != null && !added.isEmpty()) {
            added.forEach(r -> {
                if (r.getFullName() != null && !r.getFullName().isBlank()) {
                    repositorySyncService.upsertFromInstallationPayload(
                        r.getId(),
                        r.getFullName(),
                        r.getName(),
                        r.isPrivate()
                    );
                }
            });
        }

        // Delete any removed repositories
        if (removed != null && !removed.isEmpty()) {
            var ids = removed.stream().map(r -> r.getId()).toList();
            repositorySyncService.deleteRepositoriesByIds(ids);
        }
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.INSTALLATION_REPOSITORIES;
    }
}
