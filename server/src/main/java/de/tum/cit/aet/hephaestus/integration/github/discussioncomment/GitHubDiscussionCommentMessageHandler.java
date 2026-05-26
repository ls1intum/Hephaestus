package de.tum.cit.aet.hephaestus.integration.github.discussioncomment;

import static de.tum.cit.aet.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.cit.aet.hephaestus.integration.scm.common.NatsMessageDeserializer;
import de.tum.cit.aet.hephaestus.integration.scm.common.ProcessingContext;
import de.tum.cit.aet.hephaestus.integration.scm.common.ProcessingContextFactory;
import de.tum.cit.aet.hephaestus.integration.scm.discussion.Discussion;
import de.tum.cit.aet.hephaestus.integration.github.common.GitHubEventAction;
import de.tum.cit.aet.hephaestus.integration.github.common.GitHubEventType;
import de.tum.cit.aet.hephaestus.integration.github.discussion.GitHubDiscussionProcessor;
import de.tum.cit.aet.hephaestus.integration.github.discussion.dto.GitHubDiscussionDTO;
import de.tum.cit.aet.hephaestus.integration.github.discussioncomment.dto.GitHubDiscussionCommentDTO;
import de.tum.cit.aet.hephaestus.integration.github.discussioncomment.dto.GitHubDiscussionCommentEventDTO;
import de.tum.cit.aet.hephaestus.integration.handler.AbstractIntegrationMessageHandler;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles GitHub discussion_comment webhook events.
 * <p>
 * Processes: created, edited, deleted
 * <p>
 * This handler processes the parent discussion first to ensure it exists
 * before attaching comments, similar to how issue comments are handled.
 */
@Slf4j
@Component
public class GitHubDiscussionCommentMessageHandler
    extends AbstractIntegrationMessageHandler<GitHubDiscussionCommentEventDTO> {

    private final ProcessingContextFactory contextFactory;
    private final GitHubDiscussionProcessor discussionProcessor;
    private final GitHubDiscussionCommentProcessor commentProcessor;

    public GitHubDiscussionCommentMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubDiscussionProcessor discussionProcessor,
        GitHubDiscussionCommentProcessor commentProcessor,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(
            IntegrationKind.GITHUB,
            "repository." + GitHubEventType.DISCUSSION_COMMENT.getValue(),
            GitHubDiscussionCommentEventDTO.class,
            deserializer,
            transactionTemplate
        );
        this.contextFactory = contextFactory;
        this.discussionProcessor = discussionProcessor;
        this.commentProcessor = commentProcessor;
    }

    @Override
    protected void handleEvent(GitHubDiscussionCommentEventDTO event) {
        GitHubDiscussionCommentDTO commentDto = event.comment();
        GitHubDiscussionDTO discussionDto = event.discussion();

        if (commentDto == null || discussionDto == null) {
            log.warn("Received discussion_comment event with missing data: action={}", event.action());
            return;
        }

        log.info(
            "Received discussion_comment event: action={}, discussionNumber={}, commentId={}, repoName={}",
            event.action(),
            discussionDto.number(),
            commentDto.getDatabaseId(),
            event.repository() != null ? sanitizeForLog(event.repository().fullName()) : "unknown"
        );

        ProcessingContext context = contextFactory.forWebhookEvent(event).orElse(null);
        if (context == null) {
            return;
        }

        // Handle comment action
        if (event.actionType() == GitHubEventAction.DiscussionComment.DELETED) {
            commentProcessor.processDeleted(commentDto, context);
        } else {
            // Process the discussion first to ensure it exists
            Discussion discussion = discussionProcessor.process(discussionDto, context);
            if (discussion == null) {
                log.warn(
                    "Failed to process parent discussion: discussionNumber={}, skipping comment",
                    discussionDto.number()
                );
                return;
            }

            // Process the comment
            commentProcessor.process(commentDto, discussion, context);
        }
    }
}
