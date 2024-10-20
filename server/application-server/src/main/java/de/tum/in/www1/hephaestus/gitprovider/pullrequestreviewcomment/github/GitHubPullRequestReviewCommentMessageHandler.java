package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github;

import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.GitHubPullRequestSyncService;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;

@Component
public class GitHubPullRequestReviewCommentMessageHandler
        extends GitHubMessageHandler<GHEventPayload.PullRequestReviewComment> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestReviewCommentMessageHandler.class);

    private final GitHubPullRequestReviewCommentSyncService pullRequestReviewCommentSyncService;
    private final GitHubPullRequestSyncService pullRequestSyncService;
    private final GitHubRepositorySyncService repositorySyncService;

    private GitHubPullRequestReviewCommentMessageHandler(
            GitHubPullRequestReviewCommentSyncService pullRequestReviewCommentSyncService,
            GitHubPullRequestSyncService pullRequestSyncService,
            GitHubRepositorySyncService repositorySyncService) {
        super(GHEventPayload.PullRequestReviewComment.class);
        this.pullRequestReviewCommentSyncService = pullRequestReviewCommentSyncService;
        this.pullRequestSyncService = pullRequestSyncService;
        this.repositorySyncService = repositorySyncService;
    }

    @Override
    protected void handleEvent(GHEventPayload.PullRequestReviewComment eventPayload) {
        var pullRequest = eventPayload.getPullRequest();
        var repository = pullRequest.getRepository();
        var comment = eventPayload.getComment();
        logger.info(
                "Received pull request review comment event for repository: {}, pull request: {}, action: {}, commentId: {}",
                repository.getFullName(), pullRequest.getNumber(), eventPayload.getAction(), comment.getId());
        repositorySyncService.processRepository(repository);
        pullRequestSyncService.processPullRequest(pullRequest);
        pullRequestReviewCommentSyncService.processPullRequestReviewComment(comment);
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.PULL_REQUEST_REVIEW_COMMENT;
    }
}
