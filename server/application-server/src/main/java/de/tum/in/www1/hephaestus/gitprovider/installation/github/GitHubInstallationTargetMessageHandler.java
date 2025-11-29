package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationService;
import de.tum.in.www1.hephaestus.workspace.WorkspaceService;
import org.kohsuke.github.GHEventPayloadInstallationTarget;
import org.kohsuke.github.GHEventPayloadInstallationTarget.Account;
import org.kohsuke.github.GHEventPayloadInstallationTarget.Changes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Handles installation_target rename events so workspace/account metadata stays in sync.
 */
@Component
public class GitHubInstallationTargetMessageHandler extends GitHubMessageHandler<GHEventPayloadInstallationTarget> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubInstallationTargetMessageHandler.class);

    private final WorkspaceService workspaceService;
    private final OrganizationService organizationService;

    public GitHubInstallationTargetMessageHandler(
        @Lazy WorkspaceService workspaceService,
        OrganizationService organizationService
    ) {
        super(GHEventPayloadInstallationTarget.class);
        this.workspaceService = workspaceService;
        this.organizationService = organizationService;
    }

    @Override
    protected void handleEvent(GHEventPayloadInstallationTarget payload) {
        if (!"renamed".equalsIgnoreCase(payload.getAction())) {
            return;
        }
        var installation = payload.getInstallation();
        Account account = payload.getAccount();
        if (installation == null || account == null) {
            logger.warn("installation_target payload missing installation/account block");
            return;
        }

        long installationId = installation.getId();
        String newLogin = account.getLogin();
        if (newLogin == null || newLogin.isBlank()) {
            logger.warn("installation_target event {} ignored because login is empty", installationId);
            return;
        }
        Changes changes = payload.getChanges();
        String previousLogin = changes != null && changes.getLogin() != null ? changes.getLogin().getFrom() : null;

        workspaceService.handleInstallationTargetRename(installationId, previousLogin, newLogin);
        upsertOrganization(payload, installationId, newLogin);
        logger.info(
            "Handled installation_target rename for installation {}: {} -> {}",
            installationId,
            previousLogin,
            newLogin
        );
    }

    private void upsertOrganization(GHEventPayloadInstallationTarget payload, long installationId, String login) {
        if (!"Organization".equalsIgnoreCase(payload.getTargetType())) {
            return;
        }

        Account account = payload.getAccount();
        if (account == null || account.getId() == null) {
            logger.warn("installation_target event {} missing account details for organization upsert", installationId);
            return;
        }

        organizationService.upsertIdentityAndAttachInstallation(account.getId(), login, installationId);
    }

    @Override
    protected String getEventKey() {
        return "installation_target";
    }

    @Override
    public GitHubMessageDomain getDomain() {
        return GitHubMessageDomain.INSTALLATION;
    }
}
