package de.tum.in.www1.hephaestus.gitprovider.pullrequest.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GitHubPullRequestMessageHandler extends GitHubMessageHandler<GHEventPayload.PullRequest> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestMessageHandler.class);

    private final GitHubPullRequestSyncService pullRequestSyncService;
    private final GitHubRepositorySyncService repositorySyncService;

    private GitHubPullRequestMessageHandler(
        GitHubPullRequestSyncService pullRequestSyncService,
        GitHubRepositorySyncService repositorySyncService
    ) {
        super(GHEventPayload.PullRequest.class);
        this.pullRequestSyncService = pullRequestSyncService;
        this.repositorySyncService = repositorySyncService;
    }

    @Override
    protected void handleEvent(GHEventPayload.PullRequest eventPayload) {
        logger.info(
            "Received pull request event for repository: {}, pull request: {}, action: {}",
            eventPayload.getRepository().getFullName(),
            eventPayload.getPullRequest().getNumber(),
            eventPayload.getAction()
        );
        repositorySyncService.processRepository(eventPayload.getRepository());
        // We don't need to handle the deleted action here, as pull requests are not deleted
        pullRequestSyncService.processPullRequest(eventPayload.getPullRequest());
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.PULL_REQUEST;
    }
}
