package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.GitHubPullRequestSyncService;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GitHubPullRequestReviewMessageHandler extends GitHubMessageHandler<GHEventPayload.PullRequestReview> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestReviewMessageHandler.class);

    private final GitHubPullRequestReviewSyncService pullRequestReviewSyncService;
    private final GitHubPullRequestSyncService pullRequestSyncService;
    private final GitHubRepositorySyncService repositorySyncService;

    private GitHubPullRequestReviewMessageHandler(
        GitHubPullRequestReviewSyncService pullRequestReviewSyncService,
        GitHubPullRequestSyncService pullRequestSyncService,
        GitHubRepositorySyncService repositorySyncService
    ) {
        super(GHEventPayload.PullRequestReview.class);
        this.pullRequestReviewSyncService = pullRequestReviewSyncService;
        this.pullRequestSyncService = pullRequestSyncService;
        this.repositorySyncService = repositorySyncService;
    }

    @Override
    protected void handleEvent(GHEventPayload.PullRequestReview eventPayload) {
        var pullRequest = eventPayload.getPullRequest();
        var repository = pullRequest.getRepository();
        var review = eventPayload.getReview();
        logger.info(
            "Received pull request review event for repository: {}, pull request: {}, action: {}, reviewId: {}",
            repository.getFullName(),
            pullRequest.getNumber(),
            eventPayload.getAction(),
            review.getId()
        );
        repositorySyncService.processRepository(repository);
        pullRequestSyncService.processPullRequest(pullRequest);
        // We don't need to handle the deleted action here, as reviews are not deleted, they are only dismissed
        pullRequestReviewSyncService.processPullRequestReview(review);
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.PULL_REQUEST_REVIEW;
    }
}
