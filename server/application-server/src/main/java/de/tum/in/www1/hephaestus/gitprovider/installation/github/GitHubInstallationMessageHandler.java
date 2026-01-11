package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.WorkspaceProvisioningListener;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.WorkspaceProvisioningListener.AccountType;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.WorkspaceProvisioningListener.InstallationData;
import de.tum.in.www1.hephaestus.gitprovider.installation.github.dto.GitHubInstallationEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationService;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GitHub installation webhook events and provisions workspaces.
 */
@Component
public class GitHubInstallationMessageHandler extends GitHubMessageHandler<GitHubInstallationEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitHubInstallationMessageHandler.class);

    private final WorkspaceProvisioningListener provisioningListener;
    private final OrganizationService organizationService;

    GitHubInstallationMessageHandler(
        WorkspaceProvisioningListener provisioningListener,
        OrganizationService organizationService,
        NatsMessageDeserializer deserializer
    ) {
        super(GitHubInstallationEventDTO.class, deserializer);
        this.provisioningListener = provisioningListener;
        this.organizationService = organizationService;
    }

    @Override
    public GitHubEventType getEventType() {
        return GitHubEventType.INSTALLATION;
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
            log.warn("Received installation event with missing data");
            return;
        }

        var account = installation.account();
        String accountLogin = account != null ? account.login() : null;
        Long installationId = installation.id();

        log.info(
            "Received installation event: action={}, installation={}, account={}",
            event.action(),
            installationId,
            accountLogin != null ? accountLogin : "unknown"
        );

        GitHubEventAction.Installation action = event.actionType();

        // Handle deletion early - no workspace provisioning needed
        if (action == GitHubEventAction.Installation.DELETED) {
            log.info("App uninstalled from account: {}", accountLogin);
            provisioningListener.onInstallationDeleted(installationId);
            return;
        }

        // Build installation data for workspace provisioning
        String repositorySelection = installation.repositorySelection();
        String avatarUrl = account != null ? account.avatarUrl() : null;
        Long accountId = account != null ? account.id() : null;
        AccountType accountType = account != null ? AccountType.fromGitHubType(account.type()) : AccountType.USER;

        // Extract repository names from the installation event payload
        // These are provided for "created" events with "selected" repository selection
        List<String> repositoryNames = event.repositories() != null
            ? event.repositories().stream().map(GitHubRepositoryRefDTO::fullName).toList()
            : Collections.emptyList();

        InstallationData installationData = new InstallationData(
            installationId,
            accountId,
            accountLogin,
            accountType,
            avatarUrl,
            repositoryNames
        );

        provisioningListener.onInstallationCreated(installationData);
        provisioningListener.onRepositorySelectionChanged(installationId, repositorySelection);

        // Ensure organization identity is up-to-date if applicable
        if (account != null && "Organization".equalsIgnoreCase(account.type())) {
            organizationService.upsertIdentity(account.id(), accountLogin);
        }

        // Handle status changes
        switch (action) {
            case SUSPEND -> provisioningListener.onInstallationSuspended(installationId);
            case UNSUSPEND, CREATED -> provisioningListener.onInstallationActivated(installationId);
            default -> log.debug("Unhandled installation action: {}", event.action());
        }
    }
}
