package de.tum.cit.aet.hephaestus.integration.scm.github.discussion;

import static de.tum.cit.aet.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.cit.aet.hephaestus.integration.core.framework.SyncSchedulerProperties;
import de.tum.cit.aet.hephaestus.integration.core.handler.AbstractIntegrationMessageHandler;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.NatsMessageDeserializer;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.ProcessingContext;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubEventAction;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubEventType;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.ProcessingContextFactory;
import de.tum.cit.aet.hephaestus.integration.scm.github.discussion.dto.GitHubDiscussionDTO;
import de.tum.cit.aet.hephaestus.integration.scm.github.discussion.dto.GitHubDiscussionEventDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles GitHub discussion webhook events.
 */
@Slf4j
@Component
public class GitHubDiscussionMessageHandler extends AbstractIntegrationMessageHandler<GitHubDiscussionEventDTO> {

    private final ProcessingContextFactory contextFactory;
    private final GitHubDiscussionProcessor discussionProcessor;
    private final SyncSchedulerProperties syncSchedulerProperties;

    public GitHubDiscussionMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubDiscussionProcessor discussionProcessor,
        SyncSchedulerProperties syncSchedulerProperties,
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
        this.syncSchedulerProperties = syncSchedulerProperties;
    }

    /**
     * Discussion webhook events are skipped when {@code hephaestus.sync.discussions.enabled=false}.
     */
    @Override
    public boolean isEnabled() {
        return syncSchedulerProperties.discussions().enabled();
    }

    @Override
    protected void handleEvent(GitHubDiscussionEventDTO event) {
        GitHubDiscussionDTO discussionDto = event.discussion();

        if (discussionDto == null) {
            log.warn("Received discussion event with missing data: action={}", event.action());
            return;
        }

        log.debug(
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
            // A transfer moves the discussion OUT of this repository, and the payload's discussion is
            // the source-side row. Upserting it here would re-create the very phantom the removal is
            // meant to retire. Unlike issues, discussions have no reconciliation sweep to heal it, so
            // the phantom would be permanent — route the transfer to the removal path instead, which
            // hard-deletes the same as DELETED.
            case
                GitHubEventAction.Discussion.DELETED,
                GitHubEventAction.Discussion.TRANSFERRED -> discussionProcessor.processDeleted(discussionDto, context);
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
                GitHubEventAction.Discussion.CATEGORY_CHANGED,
                GitHubEventAction.Discussion.UNANSWERED,
                GitHubEventAction.Discussion.LABELED,
                GitHubEventAction.Discussion.UNLABELED -> discussionProcessor.process(discussionDto, context);
            // Unknown/unmapped actions SKIP rather than upsert. A future action that means "removed"
            // must not fall through to an upsert that re-creates a phantom — the explicit cases above
            // already cover every real upsert action, so the safe default is to ack and ignore.
            default -> log.debug("Skipped discussion event: reason=unhandledAction, action={}", event.action());
        }
    }
}
