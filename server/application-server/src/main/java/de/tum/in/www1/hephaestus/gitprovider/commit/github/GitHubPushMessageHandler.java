package de.tum.in.www1.hephaestus.gitprovider.commit.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GitHubPushMessageHandler extends GitHubMessageHandler<GHEventPayload.Push> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPushMessageHandler.class);

    private final GitHubRepositorySyncService repositorySyncService;
    private final GitHubPushCommitService pushCommitService;

    public GitHubPushMessageHandler(
        GitHubRepositorySyncService repositorySyncService,
        GitHubPushCommitService pushCommitService
    ) {
        super(GHEventPayload.Push.class);
        this.repositorySyncService = repositorySyncService;
        this.pushCommitService = pushCommitService;
    }

    @Override
    protected void handleEvent(GHEventPayload.Push eventPayload) {
        var repository = eventPayload.getRepository();
        if (repository != null) {
            repositorySyncService.processRepository(repository);
            logger.info(
                "Received push event for repo={} ref={} commits={}",
                repository.getFullName(),
                eventPayload.getRef(),
                eventPayload.getCommits().size()
            );
        } else {
            logger.warn("Push event without repository context");
        }

        pushCommitService.ingestPush(eventPayload);
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.PUSH;
    }
}
