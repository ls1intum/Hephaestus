package de.tum.in.www1.hephaestus.gitprovider.issue.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.dto.GitHubIssueDTO;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.dto.GitHubIssueEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles all GitHub issue webhook events.
 * <p>
 * This handler uses DTOs directly to ensure all fields are captured,
 * including issue types which require direct JSON parsing.
 * <p>
 * Processing is delegated to {@link GitHubIssueProcessor} which handles:
 * <ul>
 * <li>DTO to entity conversion</li>
 * <li>Persistence</li>
 * <li>Domain event publishing</li>
 * </ul>
 */
@Component
public class GitHubIssueMessageHandler extends GitHubMessageHandler<GitHubIssueEventDTO> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubIssueMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubIssueProcessor issueProcessor;

    public GitHubIssueMessageHandler(ProcessingContextFactory contextFactory, GitHubIssueProcessor issueProcessor) {
        super(GitHubIssueEventDTO.class);
        this.contextFactory = contextFactory;
        this.issueProcessor = issueProcessor;
    }

    @Override
    protected String getEventKey() {
        return "issues";
    }

    @Override
    protected void handleEvent(GitHubIssueEventDTO event) {
        GitHubIssueDTO issueDto = event.issue();

        if (issueDto == null) {
            logger.warn("Received issue event with missing issue data");
            return;
        }

        logger.info(
            "Received issue event: action={}, issue=#{}, repo={}",
            event.action(),
            issueDto.number(),
            event.repository() != null ? event.repository().fullName() : "unknown"
        );

        ProcessingContext context = contextFactory.forWebhookEvent(event).orElse(null);
        if (context == null) {
            return;
        }

        routeToProcessor(event, issueDto, context);
    }

    private void routeToProcessor(GitHubIssueEventDTO event, GitHubIssueDTO issueDto, ProcessingContext context) {
        switch (event.action()) {
            case "opened",
                "edited",
                "assigned",
                "unassigned",
                "milestoned",
                "demilestoned",
                "pinned",
                "unpinned",
                "locked",
                "unlocked",
                "transferred" -> issueProcessor.process(issueDto, context);
            case "closed" -> issueProcessor.processClosed(issueDto, context);
            case "reopened" -> issueProcessor.processReopened(issueDto, context);
            case "deleted" -> issueProcessor.processDeleted(issueDto);
            case "labeled" -> {
                if (event.label() != null) {
                    issueProcessor.processLabeled(issueDto, event.label(), context);
                } else {
                    issueProcessor.process(issueDto, context);
                }
            }
            case "unlabeled" -> {
                if (event.label() != null) {
                    issueProcessor.processUnlabeled(issueDto, event.label(), context);
                } else {
                    issueProcessor.process(issueDto, context);
                }
            }
            case "typed" -> {
                String orgLogin = event.repository().fullName().split("/")[0];
                issueProcessor.processTyped(issueDto, event.issueType(), orgLogin, context);
            }
            case "untyped" -> issueProcessor.processUntyped(issueDto, context);
            default -> {
                logger.debug("Unhandled issue action: {}", event.action());
                issueProcessor.process(issueDto, context);
            }
        }
    }
}
