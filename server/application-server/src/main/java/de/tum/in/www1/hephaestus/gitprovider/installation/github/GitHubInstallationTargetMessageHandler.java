package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.WorkspaceProvisioningListener;
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

    private final WorkspaceProvisioningListener provisioningListener;
    private final OrganizationService organizationService;

    GitHubInstallationTargetMessageHandler(
        WorkspaceProvisioningListener provisioningListener,
        OrganizationService organizationService,
        NatsMessageDeserializer deserializer
    ) {
        super(GitHubInstallationTargetEventDTO.class, deserializer);
        this.provisioningListener = provisioningListener;
        this.organizationService = organizationService;
    }

    @Override
    protected String getEventKey() {
        return "installation_target";
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
            log.warn("installation_target payload missing installation/account block");
            return;
        }

        long installationId = installation.id();
        String newLogin = account.login();

        if (newLogin == null || newLogin.isBlank()) {
            log.warn("installation_target event {} ignored because login is empty", installationId);
            return;
        }

        String previousLogin = null;
        if (event.changes() != null && event.changes().login() != null) {
            previousLogin = event.changes().login().from();
        }

        provisioningListener.onAccountRenamed(installationId, previousLogin, newLogin);
        upsertOrganization(event, installationId, newLogin);

        log.info(
            "Handled installation_target rename for installation {}: {} -> {}",
            installationId,
            previousLogin,
            newLogin
        );
    }

    private void upsertOrganization(GitHubInstallationTargetEventDTO event, long installationId, String login) {
        if (!"Organization".equalsIgnoreCase(event.targetType())) {
            return;
        }

        var account = event.account();
        if (account == null || account.id() == null) {
            log.warn("installation_target event {} missing account details for organization upsert", installationId);
            return;
        }

        organizationService.upsertIdentityAndAttachInstallation(account.id(), login, installationId);
    }
}
