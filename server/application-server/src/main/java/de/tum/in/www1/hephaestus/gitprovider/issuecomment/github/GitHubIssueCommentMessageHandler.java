package de.tum.in.www1.hephaestus.gitprovider.issuecomment.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.GitHubIssueProcessor;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.github.dto.GitHubIssueCommentEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GitHub issue_comment webhook events.
 */
@Component
public class GitHubIssueCommentMessageHandler extends GitHubMessageHandler<GitHubIssueCommentEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitHubIssueCommentMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubIssueProcessor issueProcessor;
    private final GitHubIssueCommentProcessor commentProcessor;

    GitHubIssueCommentMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubIssueProcessor issueProcessor,
        GitHubIssueCommentProcessor commentProcessor,
        NatsMessageDeserializer deserializer
    ) {
        super(GitHubIssueCommentEventDTO.class, deserializer);
        this.contextFactory = contextFactory;
        this.issueProcessor = issueProcessor;
        this.commentProcessor = commentProcessor;
    }

    @Override
    public GitHubEventType getEventType() {
        return GitHubEventType.ISSUE_COMMENT;
    }

    @Override
    @Transactional
    protected void handleEvent(GitHubIssueCommentEventDTO event) {
        var commentDto = event.comment();
        var issueDto = event.issue();

        if (commentDto == null || issueDto == null) {
            log.warn("Received issue_comment event with missing data: action={}", event.action());
            return;
        }

        log.info(
            "Received issue_comment event: action={}, issueNumber={}, commentId={}, repoName={}",
            event.action(),
            issueDto.number(),
            commentDto.id(),
            event.repository() != null ? sanitizeForLog(event.repository().fullName()) : "unknown"
        );

        ProcessingContext context = contextFactory.forWebhookEvent(event).orElse(null);
        if (context == null) {
            return;
        }

        // Only process as Issue if it's not a PR.
        // GitHub fires issue_comment events for both issues AND pull requests.
        // For PRs, the issue payload contains a pull_request field.
        // PRs are already synced via pull_request webhooks, so we skip issue processing
        // to avoid creating duplicate ISSUE records for what should be PULL_REQUEST.
        if (!issueDto.isPullRequest()) {
            issueProcessor.process(issueDto, context);
        } else {
            log.debug(
                "Skipped issue processing for PR comment: issueNumber={}, repoName={}",
                issueDto.number(),
                event.repository() != null ? sanitizeForLog(event.repository().fullName()) : "unknown"
            );
        }

        // Handle comment action
        if (event.actionType() == GitHubEventAction.IssueComment.DELETED) {
            commentProcessor.delete(commentDto.id(), context);
        } else {
            commentProcessor.process(commentDto, issueDto.getDatabaseId(), context);
        }
    }
}
