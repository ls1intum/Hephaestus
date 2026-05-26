package de.tum.cit.aet.hephaestus.integration.github.subissue;

import static de.tum.cit.aet.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.cit.aet.hephaestus.integration.github.common.GitHubEventAction;
import de.tum.cit.aet.hephaestus.integration.github.common.GitHubEventType;
import de.tum.cit.aet.hephaestus.integration.github.issue.GitHubIssueProcessor;
import de.tum.cit.aet.hephaestus.integration.github.subissue.dto.GitHubSubIssuesEventDTO;
import de.tum.cit.aet.hephaestus.integration.handler.AbstractIntegrationMessageHandler;
import de.tum.cit.aet.hephaestus.integration.scm.common.NatsMessageDeserializer;
import de.tum.cit.aet.hephaestus.integration.scm.common.ProcessingContext;
import de.tum.cit.aet.hephaestus.integration.scm.common.ProcessingContextFactory;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles GitHub sub_issues webhook events.
 */
@Component
public class GitHubSubIssuesMessageHandler extends AbstractIntegrationMessageHandler<GitHubSubIssuesEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitHubSubIssuesMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubIssueProcessor issueProcessor;
    private final GitHubSubIssueSyncService subIssueSyncService;

    GitHubSubIssuesMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubIssueProcessor issueProcessor,
        GitHubSubIssueSyncService subIssueSyncService,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(
            IntegrationKind.GITHUB,
            "repository." + GitHubEventType.SUB_ISSUES.getValue(),
            GitHubSubIssuesEventDTO.class,
            deserializer,
            transactionTemplate
        );
        this.contextFactory = contextFactory;
        this.issueProcessor = issueProcessor;
        this.subIssueSyncService = subIssueSyncService;
    }

    @Override
    protected void handleEvent(GitHubSubIssuesEventDTO event) {
        var subIssueDto = event.subIssue();
        var parentIssueDto = event.parentIssue();

        if (subIssueDto == null || parentIssueDto == null) {
            log.warn("Received sub_issues event with missing data: action={}", event.action());
            return;
        }

        log.info(
            "Received sub_issues event: action={}, parentIssueNumber={}, subIssueNumber={}, repoName={}",
            event.action(),
            parentIssueDto.number(),
            subIssueDto.number(),
            event.repository() != null ? sanitizeForLog(event.repository().fullName()) : "unknown"
        );

        ProcessingContext context = contextFactory.forWebhookEvent(event).orElse(null);
        if (context == null) {
            return;
        }

        // Ensure both issues exist
        issueProcessor.process(parentIssueDto, context);
        issueProcessor.process(subIssueDto, context);

        // Process sub-issue relationship using the SubIssue action enum
        Long subIssueId = subIssueDto.getDatabaseId();
        Long parentIssueId = parentIssueDto.getDatabaseId();
        GitHubEventAction.SubIssue action = event.actionType();

        if (action.isAdded()) {
            subIssueSyncService.processSubIssueEvent(subIssueId, parentIssueId, true);
        } else if (action.isRemoved()) {
            subIssueSyncService.processSubIssueEvent(subIssueId, parentIssueId, false);
        } else {
            log.debug("Skipped sub_issues event: reason=unhandledAction, action={}", event.action());
        }
    }
}
