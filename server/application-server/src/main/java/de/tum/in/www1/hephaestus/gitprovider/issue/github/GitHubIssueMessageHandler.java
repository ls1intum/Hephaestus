package de.tum.in.www1.hephaestus.gitprovider.issue.github;

import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;

@Component
public class GitHubIssueMessageHandler extends GitHubMessageHandler<GHEventPayload.Issue> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubIssueMessageHandler.class);

    private GitHubIssueMessageHandler() {
        super(GHEventPayload.Issue.class);
    }

    @Override
    protected void handleEvent(GHEventPayload.Issue eventPayload) {
        logger.info("Received issue event: {}", eventPayload);
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.ISSUES;
    }
}
