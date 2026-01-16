package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ProvisioningListener;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ProvisioningListener.AccountType;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ProvisioningListener.InstallationData;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ProvisioningListener.RepositorySnapshot;
import de.tum.in.www1.hephaestus.gitprovider.installation.github.dto.GitHubInstallationEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationService;
import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GitHub installation webhook events and provisions scopes.
 */
@Component
public class GitHubInstallationMessageHandler extends GitHubMessageHandler<GitHubInstallationEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitHubInstallationMessageHandler.class);

    private final ProvisioningListener provisioningListener;
    private final OrganizationService organizationService;

    GitHubInstallationMessageHandler(
        ProvisioningListener provisioningListener,
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
            log.warn("Received installation event with missing data: action={}", event.action());
            return;
        }

        var account = installation.account();
        String accountLogin = account != null ? account.login() : null;
        Long installationId = installation.id();

        log.info(
            "Received installation event: action={}, installationId={}, accountLogin={}",
            event.action(),
            installationId,
            accountLogin != null ? sanitizeForLog(accountLogin) : "unknown"
        );

        GitHubEventAction.Installation action = event.actionType();

        // Handle deletion early - no scope provisioning needed
        if (action == GitHubEventAction.Installation.DELETED) {
            log.info("Processed installation deletion: installationId={}, accountLogin={}", installationId, sanitizeForLog(accountLogin));
            provisioningListener.onInstallationDeleted(installationId);
            return;
        }

        // Build installation data for scope provisioning
        String repositorySelection = installation.repositorySelection();
        String avatarUrl = account != null ? account.avatarUrl() : null;
        Long accountId = account != null ? account.id() : null;
        AccountType accountType = account != null && "Organization".equalsIgnoreCase(account.type())
            ? AccountType.ORGANIZATION
            : AccountType.USER;

        // Extract repository snapshots from the installation event payload
        // These are provided for "created" events with "selected" repository selection
        List<RepositorySnapshot> repositories = event.repositories() != null
            ? event.repositories().stream()
                .map(ref -> new RepositorySnapshot(ref.id(), ref.fullName(), ref.name(), ref.isPrivate()))
                .toList()
            : Collections.emptyList();

        InstallationData installationData = new InstallationData(
            installationId,
            accountId,
            accountLogin,
            accountType,
            avatarUrl,
            repositories
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
            default -> log.debug("Skipped installation event: reason=unhandledAction, action={}, installationId={}", event.action(), installationId);
        }
    }
}
