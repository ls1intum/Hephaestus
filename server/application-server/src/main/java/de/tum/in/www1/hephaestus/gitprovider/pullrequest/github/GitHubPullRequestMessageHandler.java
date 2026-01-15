package de.tum.in.www1.hephaestus.gitprovider.pullrequest.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto.GitHubPullRequestDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto.GitHubPullRequestEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles all GitHub pull request webhook events.
 */
@Component
public class GitHubPullRequestMessageHandler extends GitHubMessageHandler<GitHubPullRequestEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitHubPullRequestMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubPullRequestProcessor prProcessor;

    public GitHubPullRequestMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubPullRequestProcessor prProcessor,
        NatsMessageDeserializer deserializer
    ) {
        super(GitHubPullRequestEventDTO.class, deserializer);
        this.contextFactory = contextFactory;
        this.prProcessor = prProcessor;
    }

    @Override
    public GitHubEventType getEventType() {
        return GitHubEventType.PULL_REQUEST;
    }

    @Override
    @Transactional
    protected void handleEvent(GitHubPullRequestEventDTO event) {
        GitHubPullRequestDTO prDto = event.pullRequest();

        if (prDto == null) {
            log.warn("Received pull_request event with missing data: action={}", event.action());
            return;
        }

        log.info(
            "Received pull_request event: action={}, prNumber={}, repoName={}",
            event.action(),
            prDto.number(),
            event.repository() != null ? sanitizeForLog(event.repository().fullName()) : "unknown"
        );

        ProcessingContext context = contextFactory.forWebhookEvent(event).orElse(null);
        if (context == null) {
            return;
        }

        routeToProcessor(event, prDto, context);
    }

    private void routeToProcessor(
        GitHubPullRequestEventDTO event,
        GitHubPullRequestDTO prDto,
        ProcessingContext context
    ) {
        switch (event.actionType()) {
            case
                GitHubEventAction.PullRequest.OPENED,
                GitHubEventAction.PullRequest.EDITED,
                GitHubEventAction.PullRequest.ASSIGNED,
                GitHubEventAction.PullRequest.UNASSIGNED,
                GitHubEventAction.PullRequest.MILESTONED,
                GitHubEventAction.PullRequest.DEMILESTONED,
                GitHubEventAction.PullRequest.AUTO_MERGE_ENABLED,
                GitHubEventAction.PullRequest.AUTO_MERGE_DISABLED,
                GitHubEventAction.PullRequest.REVIEW_REQUEST_REMOVED,
                GitHubEventAction.PullRequest.ENQUEUED,
                GitHubEventAction.PullRequest.DEQUEUED,
                GitHubEventAction.PullRequest.REVIEW_REQUESTED -> prProcessor.process(prDto, context);
            case GitHubEventAction.PullRequest.REOPENED -> prProcessor.processReopened(prDto, context);
            case GitHubEventAction.PullRequest.CLOSED -> prProcessor.processClosed(prDto, context);
            case GitHubEventAction.PullRequest.READY_FOR_REVIEW -> prProcessor.processReadyForReview(prDto, context);
            case GitHubEventAction.PullRequest.CONVERTED_TO_DRAFT -> prProcessor.processConvertedToDraft(
                prDto,
                context
            );
            case GitHubEventAction.PullRequest.SYNCHRONIZE -> prProcessor.processSynchronize(prDto, context);
            case GitHubEventAction.PullRequest.LABELED -> {
                if (event.label() != null) {
                    prProcessor.processLabeled(prDto, event.label(), context);
                } else {
                    prProcessor.process(prDto, context);
                }
            }
            case GitHubEventAction.PullRequest.UNLABELED -> {
                if (event.label() != null) {
                    prProcessor.processUnlabeled(prDto, event.label(), context);
                } else {
                    prProcessor.process(prDto, context);
                }
            }
            default -> {
                log.debug("Skipped pull_request event: reason=unhandledAction, action={}", event.action());
                prProcessor.process(prDto, context);
            }
        }
    }
}
