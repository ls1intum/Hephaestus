package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.installationtarget.InstallationTarget;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayloadInstallationTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GitHubInstallationTargetMessageHandler extends GitHubMessageHandler<GHEventPayloadInstallationTarget> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubInstallationTargetMessageHandler.class);

    private final GitHubInstallationSyncService installationSyncService;

    public GitHubInstallationTargetMessageHandler(GitHubInstallationSyncService installationSyncService) {
        super(GHEventPayloadInstallationTarget.class);
        this.installationSyncService = installationSyncService;
    }

    @Override
    protected void handleEvent(GHEventPayloadInstallationTarget payload) {
        logger.info(
            "Received installation_target event: action={}, targetId={}, installationId={}",
            payload.getAction(),
            payload.getAccount() != null ? payload.getAccount().getId() : null,
            payload.getInstallationRef() != null ? payload.getInstallationRef().getId() : null
        );
        InstallationTarget target = installationSyncService.handleInstallationTargetEvent(payload);
        if (target == null) {
            logger.warn(
                "installation_target webhook {} skipped because account payload was empty",
                payload.getAction()
            );
        }
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.UNKNOWN;
    }

    @Override
    public GitHubMessageDomain getDomain() {
        return GitHubMessageDomain.INSTALLATION;
    }

    @Override
    public String getSubjectSuffix() {
        return "installation_target";
    }
}
