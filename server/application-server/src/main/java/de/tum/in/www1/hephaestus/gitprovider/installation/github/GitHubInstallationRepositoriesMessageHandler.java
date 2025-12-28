package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import de.tum.in.www1.hephaestus.workspace.WorkspaceService;
import java.util.Locale;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHRepositorySelection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Handles GitHub App installation_repositories events (repositories added or removed from an installation).
 */
@Component
public class GitHubInstallationRepositoriesMessageHandler
    extends GitHubMessageHandler<GHEventPayload.InstallationRepositories> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubInstallationRepositoriesMessageHandler.class);

    private final GitHubRepositorySyncService repositorySyncService;
    private final WorkspaceService workspaceService;

    public GitHubInstallationRepositoriesMessageHandler(
        GitHubRepositorySyncService repositorySyncService,
        @Lazy WorkspaceService workspaceService
    ) {
        super(GHEventPayload.InstallationRepositories.class);
        this.repositorySyncService = repositorySyncService;
        this.workspaceService = workspaceService;
    }

    @Override
    protected void handleEvent(GHEventPayload.InstallationRepositories payload) {
        var action = payload.getAction();
        var installation = payload.getInstallation();
        var added = payload.getRepositoriesAdded();
        var removed = payload.getRepositoriesRemoved();
        var selection = parseSelection(payload.getRepositorySelection());

        logger.info(
            "Received installation_repositories event: action={}, appId={}, added={}, removed={}",
            action,
            installation != null ? installation.getAppId() : null,
            added != null ? added.size() : 0,
            removed != null ? removed.size() : 0
        );

        Long installationId = installation != null ? installation.getId() : null;

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
                    if (installationId != null) {
                        workspaceService.ensureRepositoryMonitorForInstallation(installationId, r.getFullName());
                    }
                }
            });
        }

        // Delete any removed repositories
        if (removed != null && !removed.isEmpty()) {
            var ids = removed
                .stream()
                .map(r -> r.getId())
                .toList();
            repositorySyncService.deleteRepositoriesByIds(ids);
            if (installationId != null) {
                removed.forEach(r ->
                    workspaceService.removeRepositoryMonitorForInstallation(installationId, r.getFullName())
                );
            }
        }

        if (installationId != null && selection == GHRepositorySelection.ALL && (added == null || added.isEmpty())) {
            workspaceService.ensureAllInstallationRepositoriesCovered(installationId);
        }
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.INSTALLATION_REPOSITORIES;
    }

    private GHRepositorySelection parseSelection(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return GHRepositorySelection.valueOf(value.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException ex) {
            logger.warn("Unsupported repository_selection '{}' in installation_repositories payload", value);
            return null;
        }
    }
}
