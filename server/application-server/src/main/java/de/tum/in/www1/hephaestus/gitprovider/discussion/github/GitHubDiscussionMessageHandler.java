package de.tum.in.www1.hephaestus.gitprovider.discussion.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.discussion.DiscussionRepository;
import de.tum.in.www1.hephaestus.gitprovider.discussion.github.dto.GitHubDiscussionDTO;
import de.tum.in.www1.hephaestus.gitprovider.discussion.github.dto.GitHubDiscussionEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles GitHub discussion webhook events.
 * <p>
 * Processes: created, edited, deleted, pinned, unpinned, locked, unlocked,
 * transferred, category_changed, answered, unanswered, labeled, unlabeled,
 * closed, reopened
 */
@Component
public class GitHubDiscussionMessageHandler extends GitHubMessageHandler<GitHubDiscussionEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitHubDiscussionMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubDiscussionProcessor discussionProcessor;
    private final DiscussionRepository discussionRepository;

    public GitHubDiscussionMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubDiscussionProcessor discussionProcessor,
        DiscussionRepository discussionRepository,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitHubDiscussionEventDTO.class, deserializer, transactionTemplate);
        this.contextFactory = contextFactory;
        this.discussionProcessor = discussionProcessor;
        this.discussionRepository = discussionRepository;
    }

    @Override
    public GitHubEventType getEventType() {
        return GitHubEventType.DISCUSSION;
    }

    @Override
    protected void handleEvent(GitHubDiscussionEventDTO event) {
        GitHubDiscussionDTO discussionDto = event.discussion();

        if (discussionDto == null) {
            log.warn("Received discussion event with missing data: action={}", event.action());
            return;
        }

        log.info(
            "Received discussion event: action={}, discussionNumber={}, repoName={}",
            event.action(),
            discussionDto.number(),
            event.repository() != null ? sanitizeForLog(event.repository().fullName()) : "unknown"
        );

        ProcessingContext context = contextFactory.forWebhookEvent(event).orElse(null);
        if (context == null) {
            return;
        }

        routeToProcessor(event, discussionDto, context);
    }

    private void routeToProcessor(
        GitHubDiscussionEventDTO event,
        GitHubDiscussionDTO discussionDto,
        ProcessingContext context
    ) {
        switch (event.actionType()) {
            case GitHubEventAction.Discussion.DELETED -> processDeleted(discussionDto, context);
            case
                GitHubEventAction.Discussion.CREATED,
                GitHubEventAction.Discussion.EDITED,
                GitHubEventAction.Discussion.PINNED,
                GitHubEventAction.Discussion.UNPINNED,
                GitHubEventAction.Discussion.LOCKED,
                GitHubEventAction.Discussion.UNLOCKED,
                GitHubEventAction.Discussion.TRANSFERRED,
                GitHubEventAction.Discussion.CATEGORY_CHANGED,
                GitHubEventAction.Discussion.ANSWERED,
                GitHubEventAction.Discussion.UNANSWERED,
                GitHubEventAction.Discussion.LABELED,
                GitHubEventAction.Discussion.UNLABELED,
                GitHubEventAction.Discussion.CLOSED,
                GitHubEventAction.Discussion.REOPENED -> discussionProcessor.process(discussionDto, context);
            default -> {
                log.debug("Skipped discussion event: reason=unhandledAction, action={}", event.action());
                discussionProcessor.process(discussionDto, context);
            }
        }
    }

    private void processDeleted(GitHubDiscussionDTO discussionDto, ProcessingContext context) {
        Long dbId = discussionDto.getDatabaseId();
        if (dbId != null) {
            discussionRepository.deleteById(dbId);
            log.info("Deleted discussion: discussionId={}, discussionNumber={}", dbId, discussionDto.number());
        } else {
            // Try to find by repository ID and number if database ID is not available
            discussionRepository
                .findByRepositoryIdAndNumber(context.repository().getId(), discussionDto.number())
                .ifPresent(discussion -> {
                    discussionRepository.delete(discussion);
                    log.info(
                        "Deleted discussion: discussionId={}, discussionNumber={}",
                        discussion.getId(),
                        discussionDto.number()
                    );
                });
        }
    }
}
