package de.tum.in.www1.hephaestus.gitprovider.issue.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.dto.GitHubIssueDTO;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.dto.GitHubIssueEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles all GitHub issue webhook events.
 */
@Component
public class GitHubIssueMessageHandler extends GitHubMessageHandler<GitHubIssueEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitHubIssueMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubIssueProcessor issueProcessor;

    public GitHubIssueMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubIssueProcessor issueProcessor,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitHubIssueEventDTO.class, deserializer, transactionTemplate);
        this.contextFactory = contextFactory;
        this.issueProcessor = issueProcessor;
    }

    @Override
    public GitHubEventType getEventType() {
        return GitHubEventType.ISSUES;
    }

    @Override
    protected void handleEvent(GitHubIssueEventDTO event) {
        GitHubIssueDTO issueDto = event.issue();

        if (issueDto == null) {
            log.warn("Received issue event with missing data: action={}", event.action());
            return;
        }

        log.info(
            "Received issue event: action={}, issueNumber={}, repoName={}",
            event.action(),
            issueDto.number(),
            event.repository() != null ? sanitizeForLog(event.repository().fullName()) : "unknown"
        );

        ProcessingContext context = contextFactory.forWebhookEvent(event).orElse(null);
        if (context == null) {
            return;
        }

        routeToProcessor(event, issueDto, context);
    }

    private void routeToProcessor(GitHubIssueEventDTO event, GitHubIssueDTO issueDto, ProcessingContext context) {
        switch (event.actionType()) {
            case
                GitHubEventAction.Issue.OPENED,
                GitHubEventAction.Issue.EDITED,
                GitHubEventAction.Issue.ASSIGNED,
                GitHubEventAction.Issue.UNASSIGNED,
                GitHubEventAction.Issue.MILESTONED,
                GitHubEventAction.Issue.DEMILESTONED,
                GitHubEventAction.Issue.PINNED,
                GitHubEventAction.Issue.UNPINNED,
                GitHubEventAction.Issue.LOCKED,
                GitHubEventAction.Issue.UNLOCKED,
                GitHubEventAction.Issue.TRANSFERRED -> issueProcessor.process(issueDto, context);
            case GitHubEventAction.Issue.CLOSED -> issueProcessor.processClosed(issueDto, context);
            case GitHubEventAction.Issue.REOPENED -> issueProcessor.processReopened(issueDto, context);
            case GitHubEventAction.Issue.DELETED -> issueProcessor.processDeleted(issueDto, context);
            case GitHubEventAction.Issue.LABELED -> {
                if (event.label() != null) {
                    issueProcessor.processLabeled(issueDto, event.label(), context);
                } else {
                    issueProcessor.process(issueDto, context);
                }
            }
            case GitHubEventAction.Issue.UNLABELED -> {
                if (event.label() != null) {
                    issueProcessor.processUnlabeled(issueDto, event.label(), context);
                } else {
                    issueProcessor.process(issueDto, context);
                }
            }
            case GitHubEventAction.Issue.TYPED -> {
                String orgLogin = event.organization() != null ? event.organization().login() : null;
                issueProcessor.processTyped(issueDto, event.issueType(), orgLogin, context);
            }
            case GitHubEventAction.Issue.UNTYPED -> issueProcessor.processUntyped(issueDto, context);
            default -> {
                log.debug("Skipped issue event: reason=unhandledAction, action={}", event.action());
                issueProcessor.process(issueDto, context);
            }
        }
    }
}
