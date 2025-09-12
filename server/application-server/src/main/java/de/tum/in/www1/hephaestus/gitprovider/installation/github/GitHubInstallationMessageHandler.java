package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles GitHub App installation events.
 */
@Component
public class GitHubInstallationMessageHandler extends GitHubMessageHandler<GHEventPayload.Installation> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubInstallationMessageHandler.class);

    private final GitHubRepositorySyncService repositorySyncService;

    public GitHubInstallationMessageHandler(GitHubRepositorySyncService repositorySyncService) {
        super(GHEventPayload.Installation.class);
        this.repositorySyncService = repositorySyncService;
    }

    @Override
    protected void handleEvent(GHEventPayload.Installation payload) {
        var action = payload.getAction();
        var installation = payload.getInstallation();
        var rawRepositories = payload.getRawRepositories();
        logger.info(
            "Received installation event: action={}, appId={}, repositories={}",
            action,
            installation != null ? installation.getAppId() : null,
            rawRepositories != null ? rawRepositories.size() : 0
        );

        // TODO: Persist/update the organization (installation.getAccount()) once an Organization table exists.

        // Deleted: remove repositories and exit early
        if ("deleted".equalsIgnoreCase(action)) {
            if (rawRepositories != null && !rawRepositories.isEmpty()) {
                var ids = rawRepositories.stream().map(GHEventPayload.Installation.Repository::getId).toList();
                repositorySyncService.deleteRepositoriesByIds(ids);
            }
            return;
        }

        // Other actions: upsert any provided repositories
        if (rawRepositories != null && !rawRepositories.isEmpty()) {
            rawRepositories.forEach(r -> {
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
        // TODO: Consider removing/marking other installation-scoped data as inactive on "deleted".
        // TODO: For action "suspend", consider pausing scheduled syncs and writes for this installation.
        // TODO: For action "unsuspend", resume the paused activities and optionally re-sync repository metadata.
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.INSTALLATION;
    }

    @Override
    public GitHubMessageDomain getDomain() {
        return GitHubMessageDomain.INSTALLATION;
    }
}
