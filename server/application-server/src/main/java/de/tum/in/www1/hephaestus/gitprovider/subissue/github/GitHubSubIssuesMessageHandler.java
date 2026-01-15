package de.tum.in.www1.hephaestus.gitprovider.subissue.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.GitHubIssueProcessor;
import de.tum.in.www1.hephaestus.gitprovider.subissue.github.dto.GitHubSubIssuesEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GitHub sub_issues webhook events.
 */
@Component
public class GitHubSubIssuesMessageHandler extends GitHubMessageHandler<GitHubSubIssuesEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitHubSubIssuesMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubIssueProcessor issueProcessor;
    private final GitHubSubIssueSyncService subIssueSyncService;

    GitHubSubIssuesMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubIssueProcessor issueProcessor,
        GitHubSubIssueSyncService subIssueSyncService,
        NatsMessageDeserializer deserializer
    ) {
        super(GitHubSubIssuesEventDTO.class, deserializer);
        this.contextFactory = contextFactory;
        this.issueProcessor = issueProcessor;
        this.subIssueSyncService = subIssueSyncService;
    }

    @Override
    public GitHubEventType getEventType() {
        return GitHubEventType.SUB_ISSUES;
    }

    @Override
    @Transactional
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
