package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github;

import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;

@Component
public class GitHubPullRequestReviewCommentMessageHandler extends GitHubMessageHandler<GHEventPayload.PullRequestReviewComment> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestReviewCommentMessageHandler.class);

    private GitHubPullRequestReviewCommentMessageHandler() {
        super(GHEventPayload.PullRequestReviewComment.class);
    }

    @Override
    protected void handleEvent(GHEventPayload.PullRequestReviewComment eventPayload) {
        logger.info("Received pull request review comment event: {}", eventPayload);
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.PULL_REQUEST_REVIEW_COMMENT;
    }
}
