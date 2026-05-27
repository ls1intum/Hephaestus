package de.tum.cit.aet.hephaestus.integration.github.discussion;

import static de.tum.cit.aet.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.cit.aet.hephaestus.integration.github.common.GitHubEventAction;
import de.tum.cit.aet.hephaestus.integration.github.common.GitHubEventType;
import de.tum.cit.aet.hephaestus.integration.github.discussion.dto.GitHubDiscussionDTO;
import de.tum.cit.aet.hephaestus.integration.github.discussion.dto.GitHubDiscussionEventDTO;
import de.tum.cit.aet.hephaestus.integration.core.handler.AbstractIntegrationMessageHandler;
import de.tum.cit.aet.hephaestus.integration.scm.common.NatsMessageDeserializer;
import de.tum.cit.aet.hephaestus.integration.scm.common.ProcessingContext;
import de.tum.cit.aet.hephaestus.integration.scm.common.ProcessingContextFactory;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
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
public class GitHubDiscussionMessageHandler extends AbstractIntegrationMessageHandler<GitHubDiscussionEventDTO> {

    private final ProcessingContextFactory contextFactory;
    private final GitHubDiscussionProcessor discussionProcessor;

    public GitHubDiscussionMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubDiscussionProcessor discussionProcessor,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(
            IntegrationKind.GITHUB,
            "repository." + GitHubEventType.DISCUSSION.getValue(),
            GitHubDiscussionEventDTO.class,
            deserializer,
            transactionTemplate
        );
        this.contextFactory = contextFactory;
        this.discussionProcessor = discussionProcessor;
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
