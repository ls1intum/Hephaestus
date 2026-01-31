package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.exception.InstallationNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ProvisioningListener;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ProvisioningListener.AccountType;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ProvisioningListener.InstallationData;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ProvisioningListener.RepositorySnapshot;
import de.tum.in.www1.hephaestus.gitprovider.installation.github.dto.GitHubInstallationEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationService;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles GitHub installation webhook events and provisions scopes.
 */
@Component
public class GitHubInstallationMessageHandler extends GitHubMessageHandler<GitHubInstallationEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitHubInstallationMessageHandler.class);

    private final ProvisioningListener provisioningListener;
    private final OrganizationService organizationService;
    private final GitHubAppTokenService gitHubAppTokenService;

    GitHubInstallationMessageHandler(
        ProvisioningListener provisioningListener,
        OrganizationService organizationService,
        GitHubAppTokenService gitHubAppTokenService,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitHubInstallationEventDTO.class, deserializer, transactionTemplate);
        this.provisioningListener = provisioningListener;
        this.organizationService = organizationService;
        this.gitHubAppTokenService = gitHubAppTokenService;
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
            log.info(
                "Processed installation deletion: installationId={}, accountLogin={}",
                installationId,
                sanitizeForLog(accountLogin)
            );
            provisioningListener.onInstallationDeleted(installationId);
            return;
        }

        // For SUSPEND/UNSUSPEND: verify via API FIRST, then update status only
        // Don't do full provisioning - just check current state and sync it
        if (action == GitHubEventAction.Installation.SUSPEND || action == GitHubEventAction.Installation.UNSUSPEND) {
            verifyAndUpdateInstallationStatus(installationId);
            return;
        }

        // For CREATED and other events: do full provisioning
        String repositorySelection = installation.repositorySelection();
        String avatarUrl = account != null ? account.avatarUrl() : null;
        Long accountId = account != null ? account.id() : null;
        AccountType accountType =
            account != null && "Organization".equalsIgnoreCase(account.type())
                ? AccountType.ORGANIZATION
                : AccountType.USER;

        // Extract repository snapshots from the installation event payload
        // These are provided for "created" events with "selected" repository selection
        List<RepositorySnapshot> repositories =
            event.repositories() != null
                ? event
                      .repositories()
                      .stream()
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

        // For CREATED events - verify current installation status first
        // This prevents reactivating suspended installations from stale NATS replay events
        if (action == GitHubEventAction.Installation.CREATED) {
            try {
                boolean currentlySuspended = gitHubAppTokenService.isInstallationSuspended(installationId);
                if (currentlySuspended) {
                    log.info(
                        "Installation created event received but installation is currently suspended, marking suspended: installationId={}",
                        installationId
                    );
                    gitHubAppTokenService.markInstallationSuspended(installationId);
                    // Still create the workspace but don't activate it
                    provisioningListener.onInstallationCreated(installationData);
                    provisioningListener.onRepositorySelectionChanged(installationId, repositorySelection);
                    provisioningListener.onInstallationSuspended(installationId);
                    return;
                }
            } catch (InstallationNotFoundException e) {
                // Installation no longer exists on GitHub - do NOT create workspace for deleted installation
                log.info(
                    "Installation no longer exists on GitHub, skipping workspace creation: installationId={}",
                    installationId
                );
                return;
            } catch (RuntimeException e) {
                // Network errors, credentials not configured, or other transient issues
                // Proceed with activation - if installation is truly suspended, token minting will fail fast
                log.warn(
                    "Could not verify installation status for created event, proceeding with activation: installationId={}, error={}",
                    installationId,
                    e.getMessage()
                );
            }
        }

        provisioningListener.onInstallationCreated(installationData);
        provisioningListener.onRepositorySelectionChanged(installationId, repositorySelection);

        // Ensure organization identity is up-to-date if applicable
        if (account != null && "Organization".equalsIgnoreCase(account.type())) {
            organizationService.upsertIdentity(account.id(), accountLogin);
        }

        // Handle activation for CREATED events
        if (action == GitHubEventAction.Installation.CREATED) {
            provisioningListener.onInstallationActivated(installationId);
        }
    }

    /**
     * Verify installation status via GitHub API and update workspace accordingly.
     * This is the "webhook as trigger" pattern - we don't trust the webhook action,
     * instead we verify the CURRENT state via API and update based on that.
     */
    private void verifyAndUpdateInstallationStatus(Long installationId) {
        try {
            boolean isSuspended = gitHubAppTokenService.isInstallationSuspended(installationId);

            if (isSuspended) {
                log.info("Verified installation status via API: installationId={}, status=SUSPENDED", installationId);
                // Mark in-memory FIRST to immediately block all running threads from minting tokens
                gitHubAppTokenService.markInstallationSuspended(installationId);
                provisioningListener.onInstallationSuspended(installationId);
            } else {
                log.info("Verified installation status via API: installationId={}, status=ACTIVE", installationId);
                // Clear in-memory suspension flag
                gitHubAppTokenService.markInstallationActive(installationId);
                provisioningListener.onInstallationActivated(installationId);
            }
        } catch (InstallationNotFoundException e) {
            // Installation was deleted - nothing to update
            log.info("Installation no longer exists, skipping status update: installationId={}", installationId);
        } catch (RuntimeException e) {
            // Network errors or other transient issues - don't update status if we can't verify
            log.warn(
                "Failed to verify installation status via API, skipping status update: installationId={}, error={}",
                installationId,
                e.getMessage()
            );
        }
    }
}
