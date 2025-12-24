package de.tum.in.www1.hephaestus.gitprovider.subissue.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubWebhookAction;
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

    private static final Logger logger = LoggerFactory.getLogger(GitHubSubIssuesMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubIssueProcessor issueProcessor;
    private final GitHubSubIssueSyncService subIssueSyncService;

    GitHubSubIssuesMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubIssueProcessor issueProcessor,
        GitHubSubIssueSyncService subIssueSyncService
    ) {
        super(GitHubSubIssuesEventDTO.class);
        this.contextFactory = contextFactory;
        this.issueProcessor = issueProcessor;
        this.subIssueSyncService = subIssueSyncService;
    }

    @Override
    protected String getEventKey() {
        return "sub_issues";
    }

    @Override
    @Transactional
    protected void handleEvent(GitHubSubIssuesEventDTO event) {
        var subIssueDto = event.subIssue();
        var parentIssueDto = event.parentIssue();

        if (subIssueDto == null || parentIssueDto == null) {
            logger.warn("Received sub_issues event with missing data");
            return;
        }

        logger.info(
            "Received sub_issues event: action={}, parent=#{}, sub=#{}, repo={}",
            event.action(),
            parentIssueDto.number(),
            subIssueDto.number(),
            event.repository() != null ? event.repository().fullName() : "unknown"
        );

        ProcessingContext context = contextFactory.forWebhookEvent(event).orElse(null);
        if (context == null) {
            return;
        }

        // Ensure both issues exist
        issueProcessor.process(parentIssueDto, context);
        issueProcessor.process(subIssueDto, context);

        // Process sub-issue relationship
        Long subIssueId = subIssueDto.getDatabaseId();
        Long parentIssueId = parentIssueDto.getDatabaseId();

        switch (event.actionType()) {
            case SUB_ISSUE_ADDED, PARENT_ISSUE_ADDED -> subIssueSyncService.processSubIssueEvent(
                subIssueId,
                parentIssueId,
                true
            );
            case SUB_ISSUE_REMOVED, PARENT_ISSUE_REMOVED -> subIssueSyncService.processSubIssueEvent(
                subIssueId,
                parentIssueId,
                false
            );
            default -> logger.debug("Unhandled sub_issues action: {}", event.action());
        }
    }
}
