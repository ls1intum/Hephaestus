package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github;

import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;

@Component
public class GitHubPullRequestReviewMessageHandler extends GitHubMessageHandler<GHEventPayload.PullRequestReview> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestReviewMessageHandler.class);

    private GitHubPullRequestReviewMessageHandler() {
        super(GHEventPayload.PullRequestReview.class);
    }

    @Override
    protected void handleEvent(GHEventPayload.PullRequestReview eventPayload) {
        logger.info("Received pull request review event: {}", eventPayload);
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.PULL_REQUEST_REVIEW;
    }
}
