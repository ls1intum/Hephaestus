package de.tum.in.www1.hephaestus.gitprovider.issuecomment.github;

import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.GitHubIssueSyncService;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;

@Component
public class GitHubIssueCommentMessageHandler extends GitHubMessageHandler<GHEventPayload.IssueComment> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubIssueCommentMessageHandler.class);

    private final GitHubRepositorySyncService repositorySyncService;
    private final GitHubIssueSyncService issueSyncService;
    private final GitHubIssueCommentSyncService issueCommentSyncService;

    private GitHubIssueCommentMessageHandler(
            GitHubRepositorySyncService repositorySyncService,
            GitHubIssueSyncService issueSyncService,
            GitHubIssueCommentSyncService issueCommentSyncService) {
        super(GHEventPayload.IssueComment.class);
        this.repositorySyncService = repositorySyncService;
        this.issueSyncService = issueSyncService;
        this.issueCommentSyncService = issueCommentSyncService;
    }

    @Override
    protected void handleEvent(GHEventPayload.IssueComment eventPayload) {
        var repository = eventPayload.getRepository();
        var issue = eventPayload.getIssue();
        var comment = eventPayload.getComment();
        logger.info("Received issue comment event for repository: {}, issue: {}, action: {}, commentId: {}",
                repository.getFullName(),
                issue.getNumber(),
                eventPayload.getAction(),
                comment.getId());
        repositorySyncService.processRepository(repository);
        issueSyncService.processIssue(issue);
        issueCommentSyncService.processIssueComment(comment);
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.ISSUE_COMMENT;
    }
}
