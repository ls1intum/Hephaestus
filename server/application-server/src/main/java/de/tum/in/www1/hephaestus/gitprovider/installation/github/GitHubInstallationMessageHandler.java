package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.installation.Installation;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles GitHub App installation events.
 */
@Component
public class GitHubInstallationMessageHandler extends GitHubMessageHandler<GHEventPayload.Installation> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubInstallationMessageHandler.class);

    private final GitHubInstallationSyncService installationSyncService;

    public GitHubInstallationMessageHandler(GitHubInstallationSyncService installationSyncService) {
        super(GHEventPayload.Installation.class);
        this.installationSyncService = installationSyncService;
    }

    @Override
    protected void handleEvent(GHEventPayload.Installation payload) {
        var action = payload.getAction();
        var installation = payload.getInstallation();
        if (installation == null) {
            logger.warn("Skipping installation event {} because payload did not contain installation", action);
            return;
        }

        int repositoryCount = safeRepositoryCount(payload);

        logger.info(
            "Received installation event: action={}, appId={}, repositories={}",
            action,
            installation.getAppId(),
            repositoryCount
        );
        Installation entity = installationSyncService.handleInstallationEvent(payload);
        if (entity != null) {
            logger.debug("Installation {} processed; lifecycle state {}", entity.getId(), entity.getLifecycleState());
        }
    }

    private int safeRepositoryCount(GHEventPayload.Installation payload) {
        try {
            var repositories = payload.getRawRepositories();
            return repositories != null ? repositories.size() : 0;
        } catch (NullPointerException ex) {
            return 0;
        }
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.INSTALLATION;
    }

    @Override
    public GitHubMessageDomain getDomain() {
        return GitHubMessageDomain.INSTALLATION;
    }
}
