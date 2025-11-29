package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationService;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceService;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHRepositorySelection;
import org.kohsuke.github.GHUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Handles GitHub App installation events.
 */
@Component
public class GitHubInstallationMessageHandler extends GitHubMessageHandler<GHEventPayload.Installation> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubInstallationMessageHandler.class);

    private final GitHubRepositorySyncService repositorySyncService;
    private final WorkspaceService workspaceService;
    private final OrganizationService organizationService;

    public GitHubInstallationMessageHandler(
        GitHubRepositorySyncService repositorySyncService,
        @Lazy WorkspaceService workspaceService,
        OrganizationService organizationService
    ) {
        super(GHEventPayload.Installation.class);
        this.repositorySyncService = repositorySyncService;
        this.workspaceService = workspaceService;
        this.organizationService = organizationService;
    }

    @Override
    protected void handleEvent(GHEventPayload.Installation payload) {
        var action = payload.getAction();
        var installation = payload.getInstallation();
        var rawRepositories = safeGetRawRepositories(payload);
        logger.info(
            "Received installation event: action={}, appId={}, repositories={}",
            action,
            installation != null ? installation.getAppId() : null,
            rawRepositories != null ? rawRepositories.size() : 0
        );

        if (installation == null) {
            logger.warn("Ignoring installation event without installation payload (action={}).", action);
            return;
        }

        long installationId = installation.getId();
        var selection = safeGetRepositorySelection(installation);
        GHUser account = safeGetAccount(installation);
        String accountLogin = account != null ? account.getLogin() : null;
        boolean isDeletion = "deleted".equalsIgnoreCase(action);
        if (!isDeletion) {
            Workspace workspace = workspaceService.ensureForInstallation(installationId, accountLogin, selection);
            if (workspace == null) {
                // Could not create workspace (e.g., old installation, can't sync user)
                logger.info(
                    "Skipping installation event for {} (action={}): workspace could not be ensured.",
                    installationId,
                    action
                );
                return;
            }
            workspaceService.updateRepositorySelection(installationId, selection);

            if (account != null && "Organization".equalsIgnoreCase(safeGetAccountType(account))) {
                organizationService.upsertIdentityAndAttachInstallation(account.getId(), accountLogin, installationId);
            }
        }

        // Deleted: remove repositories and exit early
        if (isDeletion) {
            handleInstallationDeleted(installationId, rawRepositories, accountLogin);
            return;
        }

        if ("suspend".equalsIgnoreCase(action)) {
            workspaceService.updateStatusForInstallation(installationId, Workspace.WorkspaceStatus.SUSPENDED);
        } else if ("unsuspend".equalsIgnoreCase(action) || "created".equalsIgnoreCase(action)) {
            workspaceService.updateStatusForInstallation(installationId, Workspace.WorkspaceStatus.ACTIVE);
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
                    workspaceService.ensureRepositoryMonitorForInstallation(installationId, r.getFullName());
                }
            });
        }
        if (selection == GHRepositorySelection.ALL) {
            Set<String> protectedRepositories = rawRepositories == null
                ? Collections.emptySet()
                : rawRepositories
                    .stream()
                    .map(GHEventPayload.Installation.Repository::getFullName)
                    .filter(fullName -> fullName != null && !fullName.isBlank())
                    .collect(Collectors.toSet());
            workspaceService.ensureAllInstallationRepositoriesCovered(installationId, protectedRepositories);
        }
    }

    /**
     * Handle installation deletion: clean up monitors, repositories, organization link, and NATS consumers.
     * <p>
     * GitHub does NOT guarantee that the repositories array is populated on deletion events,
     * especially when repository_selection is ALL. We therefore clean up by owner prefix rather
     * than relying on the webhook payload.
     * </p>
     */
    private void handleInstallationDeleted(
        long installationId,
        List<GHEventPayload.Installation.Repository> rawRepositories,
        String accountLogin
    ) {
        // 1. Stop NATS consumer for the workspace first (before removing monitors)
        workspaceService.stopNatsConsumerForInstallation(installationId);

        // 2. Remove all repository monitors
        workspaceService.removeAllRepositoryMonitorsForInstallation(installationId);

        // 3. Delete repositories - use both approaches for safety:
        //    a) Delete by IDs if provided in payload
        //    b) Delete by owner prefix (catches any repos not in payload)
        if (rawRepositories != null && !rawRepositories.isEmpty()) {
            var ids = rawRepositories.stream().map(GHEventPayload.Installation.Repository::getId).toList();
            repositorySyncService.deleteRepositoriesByIds(ids);
        }
        if (accountLogin != null && !accountLogin.isBlank()) {
            repositorySyncService.deleteRepositoriesByOwnerPrefix(accountLogin + "/");
        }

        // 4. Detach organization from installation (set installationId to null)
        organizationService.detachInstallation(installationId);

        // 5. Mark workspace as PURGED (not SUSPENDED - deleted is permanent)
        workspaceService.updateStatusForInstallation(installationId, Workspace.WorkspaceStatus.PURGED);
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.INSTALLATION;
    }

    @Override
    public GitHubMessageDomain getDomain() {
        return GitHubMessageDomain.INSTALLATION;
    }

    private GHUser safeGetAccount(GHAppInstallation installation) {
        return installation.getAccount();
    }

    private GHRepositorySelection safeGetRepositorySelection(GHAppInstallation installation) {
        return installation.getRepositorySelection();
    }

    private String safeGetAccountType(GHUser account) {
        if (account == null) {
            return null;
        }
        try {
            return account.getType();
        } catch (IOException e) {
            logger.warn("Failed to resolve account type for {}: {}", account.getLogin(), e.getMessage());
            return null;
        }
    }

    private List<GHEventPayload.Installation.Repository> safeGetRawRepositories(GHEventPayload.Installation payload) {
        try {
            return payload.getRawRepositories();
        } catch (NullPointerException e) {
            return null;
        }
    }
}
