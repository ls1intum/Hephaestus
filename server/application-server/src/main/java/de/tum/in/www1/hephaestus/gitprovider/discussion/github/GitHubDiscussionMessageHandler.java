package de.tum.in.www1.hephaestus.gitprovider.discussion.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.discussion.github.dto.GitHubDiscussionDTO;
import de.tum.in.www1.hephaestus.gitprovider.discussion.github.dto.GitHubDiscussionEventDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles GitHub discussion webhook events.
 * <p>
 * Processes: created, edited, deleted, pinned, unpinned, locked, unlocked,
 * transferred, category_changed, answered, unanswered, labeled, unlabeled,
 * closed, reopened
 */
@Slf4j
@Component
public class GitHubDiscussionMessageHandler extends GitHubMessageHandler<GitHubDiscussionEventDTO> {

    private final ProcessingContextFactory contextFactory;
    private final GitHubDiscussionProcessor discussionProcessor;

    public GitHubDiscussionMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubDiscussionProcessor discussionProcessor,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitHubDiscussionEventDTO.class, deserializer, transactionTemplate);
        this.contextFactory = contextFactory;
        this.discussionProcessor = discussionProcessor;
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
            case GitHubEventAction.Discussion.DELETED -> discussionProcessor.processDeleted(discussionDto, context);
            case GitHubEventAction.Discussion.CLOSED -> discussionProcessor.processClosed(discussionDto, context);
            case GitHubEventAction.Discussion.REOPENED -> discussionProcessor.processReopened(discussionDto, context);
            case GitHubEventAction.Discussion.ANSWERED -> discussionProcessor.processAnswered(discussionDto, context);
            case
                GitHubEventAction.Discussion.CREATED,
                GitHubEventAction.Discussion.EDITED,
                GitHubEventAction.Discussion.PINNED,
                GitHubEventAction.Discussion.UNPINNED,
                GitHubEventAction.Discussion.LOCKED,
                GitHubEventAction.Discussion.UNLOCKED,
                GitHubEventAction.Discussion.TRANSFERRED,
                GitHubEventAction.Discussion.CATEGORY_CHANGED,
                GitHubEventAction.Discussion.UNANSWERED,
                GitHubEventAction.Discussion.LABELED,
                GitHubEventAction.Discussion.UNLABELED -> discussionProcessor.process(discussionDto, context);
            default -> {
                log.debug("Skipped discussion event: reason=unhandledAction, action={}", event.action());
                discussionProcessor.process(discussionDto, context);
            }
        }
    }
}
