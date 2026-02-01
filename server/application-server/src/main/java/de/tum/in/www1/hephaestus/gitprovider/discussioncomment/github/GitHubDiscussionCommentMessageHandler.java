package de.tum.in.www1.hephaestus.gitprovider.discussioncomment.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.discussion.Discussion;
import de.tum.in.www1.hephaestus.gitprovider.discussion.DiscussionRepository;
import de.tum.in.www1.hephaestus.gitprovider.discussion.github.GitHubDiscussionProcessor;
import de.tum.in.www1.hephaestus.gitprovider.discussion.github.dto.GitHubDiscussionDTO;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.DiscussionCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.github.dto.GitHubDiscussionCommentDTO;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.github.dto.GitHubDiscussionCommentEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@Component
public class GitHubDiscussionCommentMessageHandler extends GitHubMessageHandler<GitHubDiscussionCommentEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitHubDiscussionCommentMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubDiscussionProcessor discussionProcessor;
    private final GitHubDiscussionCommentProcessor commentProcessor;
    private final DiscussionRepository discussionRepository;
    private final DiscussionCommentRepository commentRepository;

    public GitHubDiscussionCommentMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubDiscussionProcessor discussionProcessor,
        GitHubDiscussionCommentProcessor commentProcessor,
        DiscussionRepository discussionRepository,
        DiscussionCommentRepository commentRepository,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitHubDiscussionCommentEventDTO.class, deserializer, transactionTemplate);
        this.contextFactory = contextFactory;
        this.discussionProcessor = discussionProcessor;
        this.commentProcessor = commentProcessor;
        this.discussionRepository = discussionRepository;
        this.commentRepository = commentRepository;
    }

    @Override
    public GitHubEventType getEventType() {
        return GitHubEventType.DISCUSSION_COMMENT;
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
            processDeleted(commentDto, context);
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

    private void processDeleted(GitHubDiscussionCommentDTO commentDto, ProcessingContext context) {
        Long dbId = commentDto.getDatabaseId();
        if (dbId != null) {
            commentRepository.deleteById(dbId);
            log.info("Deleted discussion comment: commentId={}", dbId);
        } else {
            log.warn("Cannot delete discussion comment: reason=missingDatabaseId");
        }
    }
}
