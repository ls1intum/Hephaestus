package de.tum.cit.aet.hephaestus.integration.scm.github.issue;

import static de.tum.cit.aet.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.cit.aet.hephaestus.integration.core.handler.AbstractIntegrationMessageHandler;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.NatsMessageDeserializer;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.ProcessingContext;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubEventAction;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubEventType;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.ProcessingContextFactory;
import de.tum.cit.aet.hephaestus.integration.scm.github.issue.dto.GitHubIssueDTO;
import de.tum.cit.aet.hephaestus.integration.scm.github.issue.dto.GitHubIssueEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles all GitHub issue webhook events.
 */
@Component
public class GitHubIssueMessageHandler extends AbstractIntegrationMessageHandler<GitHubIssueEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitHubIssueMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubIssueProcessor issueProcessor;

    public GitHubIssueMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubIssueProcessor issueProcessor,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(
            IntegrationKind.GITHUB,
            "repository." + GitHubEventType.ISSUES.getValue(),
            GitHubIssueEventDTO.class,
            deserializer,
            transactionTemplate
        );
        this.contextFactory = contextFactory;
        this.issueProcessor = issueProcessor;
    }

    @Override
    protected void handleEvent(GitHubIssueEventDTO event) {
        GitHubIssueDTO issueDto = event.issue();

        if (issueDto == null) {
            log.warn("Received issue event with missing data: action={}", event.action());
            return;
        }

        log.debug(
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
                GitHubEventAction.Issue.UNLOCKED -> issueProcessor.process(issueDto, context);
            // A transfer moves the issue OUT of this repository. Upserting it here (as this case used
            // to, sharing the branch above) recreated the very phantom the deletion sweep exists to
            // retire.
            case GitHubEventAction.Issue.TRANSFERRED -> issueProcessor.processTransferred(issueDto, context);
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
