package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.installation.github.dto.GitHubInstallationEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles GitHub installation webhook events.
 * <p>
 * Uses DTOs directly for complete field coverage.
 * <p>
 * <b>Future work:</b> Implement full workspace provisioning when WorkspaceProvisioningService
 * is available.
 */
@Component
public class GitHubInstallationMessageHandler extends GitHubMessageHandler<GitHubInstallationEventDTO> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubInstallationMessageHandler.class);

    GitHubInstallationMessageHandler() {
        super(GitHubInstallationEventDTO.class);
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

        logger.info(
            "Received installation event: action={}, installation={}, account={}",
            event.action(),
            installation.id(),
            installation.account() != null ? installation.account().login() : "unknown"
        );

        // Future: Workspace provisioning will be implemented here
        switch (event.action()) {
            case "created" -> logger.info(
                "App installed for account: {}",
                installation.account() != null ? installation.account().login() : "unknown"
            );
            case "deleted" -> logger.info(
                "App uninstalled from account: {}",
                installation.account() != null ? installation.account().login() : "unknown"
            );
            case "suspend" -> logger.info(
                "App suspended for account: {}",
                installation.account() != null ? installation.account().login() : "unknown"
            );
            case "unsuspend" -> logger.info(
                "App unsuspended for account: {}",
                installation.account() != null ? installation.account().login() : "unknown"
            );
            default -> logger.debug("Unhandled installation action: {}", event.action());
        }
    }
}
