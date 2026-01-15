package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ProvisioningListener;
import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.installation.github.dto.GitHubInstallationTargetEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GitHub installation_target webhook events.
 */
@Component
public class GitHubInstallationTargetMessageHandler extends GitHubMessageHandler<GitHubInstallationTargetEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitHubInstallationTargetMessageHandler.class);

    private final ProvisioningListener provisioningListener;
    private final OrganizationService organizationService;

    GitHubInstallationTargetMessageHandler(
        ProvisioningListener provisioningListener,
        OrganizationService organizationService,
        NatsMessageDeserializer deserializer
    ) {
        super(GitHubInstallationTargetEventDTO.class, deserializer);
        this.provisioningListener = provisioningListener;
        this.organizationService = organizationService;
    }

    @Override
    public GitHubEventType getEventType() {
        return GitHubEventType.INSTALLATION_TARGET;
    }

    @Override
    public GitHubMessageDomain getDomain() {
        return GitHubMessageDomain.INSTALLATION;
    }

    @Override
    @Transactional
    protected void handleEvent(GitHubInstallationTargetEventDTO event) {
        if (event.actionType() != GitHubEventAction.InstallationTarget.RENAMED) {
            return;
        }

        var installation = event.installation();
        var account = event.account();

        if (installation == null || account == null) {
            log.warn("Received installation_target event with missing data: action={}", event.action());
            return;
        }

        long installationId = installation.id();
        String newLogin = account.login();

        if (newLogin == null || newLogin.isBlank()) {
            log.warn("Skipped installation_target event: reason=emptyLogin, installationId={}", installationId);
            return;
        }

        String previousLogin = null;
        if (event.changes() != null && event.changes().login() != null) {
            previousLogin = event.changes().login().from();
        }

        provisioningListener.onAccountRenamed(installationId, previousLogin, newLogin);
        upsertOrganization(event, installationId, newLogin);

        log.info(
            "Processed installation_target rename: installationId={}, previousLogin={}, newLogin={}",
            installationId,
            sanitizeForLog(previousLogin),
            sanitizeForLog(newLogin)
        );
    }

    private void upsertOrganization(GitHubInstallationTargetEventDTO event, long installationId, String login) {
        if (!"Organization".equalsIgnoreCase(event.targetType())) {
            return;
        }

        var account = event.account();
        if (account == null || account.id() == null) {
            log.warn("Skipped organization upsert: reason=missingAccountDetails, installationId={}", installationId);
            return;
        }

        organizationService.upsertIdentity(account.id(), login);
    }
}
