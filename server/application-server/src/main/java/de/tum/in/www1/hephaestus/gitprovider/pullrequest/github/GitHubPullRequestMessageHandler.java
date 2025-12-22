package de.tum.in.www1.hephaestus.gitprovider.pullrequest.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto.GitHubPullRequestDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto.GitHubPullRequestEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles all GitHub pull request webhook events.
 * <p>
 * This handler uses DTOs directly (no hub4j) for complete field coverage.
 * Processing is delegated to {@link GitHubPullRequestProcessor}.
 */
@Component
public class GitHubPullRequestMessageHandler extends GitHubMessageHandler<GitHubPullRequestEventDTO> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubPullRequestProcessor prProcessor;

    public GitHubPullRequestMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubPullRequestProcessor prProcessor
    ) {
        super(GitHubPullRequestEventDTO.class);
        this.contextFactory = contextFactory;
        this.prProcessor = prProcessor;
    }

    @Override
    protected String getEventKey() {
        return "pull_request";
    }

    @Override
    protected void handleEvent(GitHubPullRequestEventDTO event) {
        GitHubPullRequestDTO prDto = event.pullRequest();

        if (prDto == null) {
            logger.warn("Received pull_request event with missing PR data");
            return;
        }

        logger.info(
            "Received pull_request event: action={}, pr=#{}, repo={}",
            event.action(),
            prDto.number(),
            event.repository() != null ? event.repository().fullName() : "unknown"
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
        switch (event.action()) {
            case "opened",
                "edited",
                "assigned",
                "unassigned",
                "milestoned",
                "demilestoned",
                "auto_merge_enabled",
                "auto_merge_disabled",
                "reopened",
                "review_request_removed",
                "enqueued",
                "dequeued" -> prProcessor.process(prDto, context);
            case "closed" -> prProcessor.processClosed(prDto, context);
            case "ready_for_review" -> prProcessor.processReadyForReview(prDto, context);
            case "converted_to_draft" -> prProcessor.processConvertedToDraft(prDto, context);
            case "synchronize" -> prProcessor.processSynchronize(prDto, context);
            case "labeled" -> {
                if (event.label() != null) {
                    prProcessor.processLabeled(prDto, event.label(), context);
                } else {
                    prProcessor.process(prDto, context);
                }
            }
            case "unlabeled" -> {
                if (event.label() != null) {
                    prProcessor.processUnlabeled(prDto, event.label(), context);
                } else {
                    prProcessor.process(prDto, context);
                }
            }
            case "review_requested" -> prProcessor.process(prDto, context);
            default -> {
                logger.debug("Unhandled pull_request action: {}", event.action());
                prProcessor.process(prDto, context);
            }
        }
    }
}
