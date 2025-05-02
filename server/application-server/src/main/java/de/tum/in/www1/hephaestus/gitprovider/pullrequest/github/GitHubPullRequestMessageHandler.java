package de.tum.in.www1.hephaestus.gitprovider.pullrequest.github;

import de.tum.in.www1.hephaestus.activity.badpracticedetector.BadPracticeDetectorScheduler;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
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

    private final BadPracticeDetectorScheduler badPracticeDetectorScheduler;

    private GitHubPullRequestMessageHandler(
        GitHubPullRequestSyncService pullRequestSyncService,
        GitHubRepositorySyncService repositorySyncService,
        BadPracticeDetectorScheduler badPracticeDetectorScheduler
    ) {
        super(GHEventPayload.PullRequest.class);
        this.pullRequestSyncService = pullRequestSyncService;
        this.repositorySyncService = repositorySyncService;
        this.badPracticeDetectorScheduler = badPracticeDetectorScheduler;
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
        PullRequest pullRequest = pullRequestSyncService.processPullRequest(eventPayload.getPullRequest());

        scheduleBadPracticeDetectionOnEvent(eventPayload, pullRequest);
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.PULL_REQUEST;
    }

    private void scheduleBadPracticeDetectionOnEvent(GHEventPayload.PullRequest eventPayload, PullRequest pullRequest) {
        switch (eventPayload.getAction()) {
            case "opened", "ready_for_review", "reopened" ->
                    badPracticeDetectorScheduler.detectBadPracticeForPrWhenOpenedOrReadyForReviewEvent(pullRequest);
            case "closed" -> badPracticeDetectorScheduler.detectBadPracticeForPrIfClosedEvent(pullRequest);
        }
    }
}
