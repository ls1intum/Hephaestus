package de.tum.in.www1.hephaestus.gitprovider.issuecomment.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.GitHubIssueProcessor;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.github.dto.GitHubIssueCommentEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GitHub issue_comment webhook events.
 * <p>
 * Uses DTOs directly for complete field coverage.
 */
@Component
public class GitHubIssueCommentMessageHandler extends GitHubMessageHandler<GitHubIssueCommentEventDTO> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubIssueCommentMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubIssueProcessor issueProcessor;
    private final GitHubIssueCommentProcessor commentProcessor;

    GitHubIssueCommentMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubIssueProcessor issueProcessor,
        GitHubIssueCommentProcessor commentProcessor
    ) {
        super(GitHubIssueCommentEventDTO.class);
        this.contextFactory = contextFactory;
        this.issueProcessor = issueProcessor;
        this.commentProcessor = commentProcessor;
    }

    @Override
    protected String getEventKey() {
        return "issue_comment";
    }

    @Override
    @Transactional
    protected void handleEvent(GitHubIssueCommentEventDTO event) {
        var commentDto = event.comment();
        var issueDto = event.issue();

        if (commentDto == null || issueDto == null) {
            logger.warn("Received issue_comment event with missing data");
            return;
        }

        logger.info(
            "Received issue_comment event: action={}, issue=#{}, comment={}, repo={}",
            event.action(),
            issueDto.number(),
            commentDto.id(),
            event.repository() != null ? event.repository().fullName() : "unknown"
        );

        ProcessingContext context = contextFactory.forWebhookEvent(event).orElse(null);
        if (context == null) {
            return;
        }

        // Ensure issue exists
        issueProcessor.process(issueDto, context);

        // Handle comment action
        if ("deleted".equals(event.action())) {
            commentProcessor.delete(commentDto.id(), context);
        } else {
            commentProcessor.process(commentDto, issueDto.getDatabaseId(), context);
        }
    }
}
