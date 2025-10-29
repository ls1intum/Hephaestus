package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.GitHubPullRequestSyncService;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayloadPullRequestReviewThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GitHubPullRequestReviewThreadMessageHandler
    extends GitHubMessageHandler<GHEventPayloadPullRequestReviewThread> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestReviewThreadMessageHandler.class);

    private final GitHubPullRequestReviewThreadSyncService threadSyncService;
    private final GitHubPullRequestSyncService pullRequestSyncService;
    private final GitHubRepositorySyncService repositorySyncService;

    public GitHubPullRequestReviewThreadMessageHandler(
        GitHubPullRequestReviewThreadSyncService threadSyncService,
        GitHubPullRequestSyncService pullRequestSyncService,
        GitHubRepositorySyncService repositorySyncService
    ) {
        super(GHEventPayloadPullRequestReviewThread.class);
        this.threadSyncService = threadSyncService;
        this.pullRequestSyncService = pullRequestSyncService;
        this.repositorySyncService = repositorySyncService;
    }

    @Override
    protected void handleEvent(GHEventPayloadPullRequestReviewThread eventPayload) {
        var action = eventPayload.getAction();
        var pullRequest = eventPayload.getPullRequest();
        var repository = eventPayload.getRepository();

        logger.info(
            "Received pull request review thread event for repository: {}, pull request: {}, action: {}",
            repository.getFullName(),
            pullRequest.getNumber(),
            action
        );

        repositorySyncService.processRepository(repository);
        pullRequestSyncService.processPullRequest(pullRequest);

        threadSyncService.processThreadEvent(eventPayload);
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.PULL_REQUEST_REVIEW_THREAD;
    }
}
