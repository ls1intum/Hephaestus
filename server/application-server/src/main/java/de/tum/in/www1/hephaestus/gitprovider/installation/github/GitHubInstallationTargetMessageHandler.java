package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.installation.github.dto.GitHubInstallationTargetEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles GitHub installation_target webhook events.
 * <p>
 * Uses DTOs directly for complete field coverage.
 */
@Component
public class GitHubInstallationTargetMessageHandler extends GitHubMessageHandler<GitHubInstallationTargetEventDTO> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubInstallationTargetMessageHandler.class);

    GitHubInstallationTargetMessageHandler() {
        super(GitHubInstallationTargetEventDTO.class);
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
    protected void handleEvent(GitHubInstallationTargetEventDTO event) {
        var account = event.account();

        if (account == null) {
            logger.warn("Received installation_target event with missing data");
            return;
        }

        logger.info(
            "Received installation_target event: action={}, account={}, targetType={}",
            event.action(),
            account.login(),
            event.targetType()
        );

        // Future: Account name updates will be implemented here
        switch (event.action()) {
            case "renamed" -> logger.info("Account renamed: {}", account.login());
            default -> logger.debug("Unhandled installation_target action: {}", event.action());
        }
    }
}
