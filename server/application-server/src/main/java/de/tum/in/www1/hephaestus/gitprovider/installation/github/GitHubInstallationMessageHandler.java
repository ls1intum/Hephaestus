package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.installation.github.dto.GitHubInstallationEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationService;
import de.tum.in.www1.hephaestus.workspace.RepositorySelection;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Handles GitHub installation webhook events and provisions workspaces.
 */
@Component
public class GitHubInstallationMessageHandler extends GitHubMessageHandler<GitHubInstallationEventDTO> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubInstallationMessageHandler.class);

    private final WorkspaceService workspaceService;
    private final OrganizationService organizationService;

    GitHubInstallationMessageHandler(@Lazy WorkspaceService workspaceService, OrganizationService organizationService) {
        super(GitHubInstallationEventDTO.class);
        this.workspaceService = workspaceService;
        this.organizationService = organizationService;
    }

    @Override
    protected String getEventKey() {
        return "installation";
    }

    @Override
    public GitHubMessageDomain getDomain() {
        return GitHubMessageDomain.INSTALLATION;
    }

    @Override
    protected void handleEvent(GitHubInstallationEventDTO event) {
        var installation = event.installation();

        if (installation == null) {
            logger.warn("Received installation event with missing data");
            return;
        }

        var account = installation.account();
        String accountLogin = account != null ? account.login() : null;
        Long installationId = installation.id();

        logger.info(
            "Received installation event: action={}, installation={}, account={}",
            event.action(),
            installationId,
            accountLogin != null ? accountLogin : "unknown"
        );

        GitHubEventAction.Installation action = event.actionType();

        // Handle deletion early - no workspace provisioning needed
        if (action == GitHubEventAction.Installation.DELETED) {
            logger.info("App uninstalled from account: {}", accountLogin);
            // Future: Handle workspace cleanup
            return;
        }

        // Provision or update workspace
        RepositorySelection selection = parseRepositorySelection(installation.repositorySelection());
        Workspace workspace = workspaceService.ensureForInstallation(installationId, accountLogin, selection);

        if (workspace == null) {
            logger.info("Skipping installation event for {}: workspace could not be ensured", installationId);
            return;
        }

        workspaceService.updateRepositorySelection(installationId, selection);

        // Link organization to installation if applicable
        if (account != null && "Organization".equalsIgnoreCase(account.type())) {
            organizationService.upsertIdentityAndAttachInstallation(account.id(), accountLogin, installationId);
        }

        // Handle status changes
        switch (action) {
            case SUSPEND -> workspaceService.updateStatusForInstallation(
                installationId,
                Workspace.WorkspaceStatus.SUSPENDED
            );
            case UNSUSPEND, CREATED -> workspaceService.updateStatusForInstallation(
                installationId,
                Workspace.WorkspaceStatus.ACTIVE
            );
            default -> logger.debug("Unhandled installation action: {}", event.action());
        }
    }

    private RepositorySelection parseRepositorySelection(String selection) {
        if ("all".equalsIgnoreCase(selection)) {
            return RepositorySelection.ALL;
        }
        return RepositorySelection.SELECTED;
    }
}
