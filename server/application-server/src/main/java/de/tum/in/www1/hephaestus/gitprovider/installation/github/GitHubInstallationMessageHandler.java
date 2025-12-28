package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.WorkspaceProvisioningListener;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.WorkspaceProvisioningListener.AccountType;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.WorkspaceProvisioningListener.InstallationData;
import de.tum.in.www1.hephaestus.gitprovider.installation.github.dto.GitHubInstallationEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationService;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GitHub installation webhook events and provisions workspaces.
 */
@Component
public class GitHubInstallationMessageHandler extends GitHubMessageHandler<GitHubInstallationEventDTO> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubInstallationMessageHandler.class);

    private final WorkspaceProvisioningListener provisioningListener;
    private final OrganizationService organizationService;

    GitHubInstallationMessageHandler(
        WorkspaceProvisioningListener provisioningListener,
        OrganizationService organizationService
    ) {
        super(GitHubInstallationEventDTO.class);
        this.provisioningListener = provisioningListener;
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
    @Transactional
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
            provisioningListener.onInstallationDeleted(installationId);
            return;
        }

        // Build installation data for workspace provisioning
        String repositorySelection = installation.repositorySelection();
        String avatarUrl = account != null ? account.avatarUrl() : null;
        AccountType accountType = account != null ? AccountType.fromGitHubType(account.type()) : AccountType.USER;

        InstallationData installationData = new InstallationData(
            installationId,
            accountLogin,
            accountType,
            avatarUrl,
            Collections.emptyList() // Repository names are handled separately via repository events
        );

        provisioningListener.onInstallationCreated(installationData);
        provisioningListener.onRepositorySelectionChanged(installationId, repositorySelection);

        // Link organization to installation if applicable
        if (account != null && "Organization".equalsIgnoreCase(account.type())) {
            organizationService.upsertIdentityAndAttachInstallation(account.id(), accountLogin, installationId);
        }

        // Handle status changes
        switch (action) {
            case SUSPEND -> provisioningListener.onInstallationSuspended(installationId);
            case UNSUSPEND, CREATED -> provisioningListener.onInstallationActivated(installationId);
            default -> logger.debug("Unhandled installation action: {}", event.action());
        }
    }
}
