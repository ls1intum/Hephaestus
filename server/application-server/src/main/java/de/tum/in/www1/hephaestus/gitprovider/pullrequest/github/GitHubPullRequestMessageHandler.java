package de.tum.in.www1.hephaestus.gitprovider.pullrequest.github;

import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;

@Component
public class GitHubPullRequestMessageHandler extends GitHubMessageHandler<GHEventPayload.PullRequest> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestMessageHandler.class);

    private final GitHubPullRequestSyncService pullRequestSyncService;

    private GitHubPullRequestMessageHandler(GitHubPullRequestSyncService pullRequestSyncService) {
        super(GHEventPayload.PullRequest.class);
        this.pullRequestSyncService = pullRequestSyncService;
    }

    @Override
    protected void handleEvent(GHEventPayload.PullRequest eventPayload) {
        logger.info("Received pull request event for number: {}, action: {}", eventPayload.getNumber(),
                eventPayload.getAction());
        // TODO: REPO
        pullRequestSyncService.processPullRequest(eventPayload.getPullRequest());
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.PULL_REQUEST;
    }
}
