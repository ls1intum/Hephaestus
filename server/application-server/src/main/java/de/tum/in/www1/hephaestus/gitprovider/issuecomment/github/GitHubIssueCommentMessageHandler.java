package de.tum.in.www1.hephaestus.gitprovider.issuecomment.github;

import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;

@Component
public class GitHubIssueCommentMessageHandler extends GitHubMessageHandler<GHEventPayload.IssueComment> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubIssueCommentMessageHandler.class);

    private GitHubIssueCommentMessageHandler() {
        super(GHEventPayload.IssueComment.class);
    }

    @Override
    protected void handleEvent(GHEventPayload.IssueComment eventPayload) {
        logger.info("Received issue comment event: {}", eventPayload);
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.ISSUE_COMMENT;
    }
}
