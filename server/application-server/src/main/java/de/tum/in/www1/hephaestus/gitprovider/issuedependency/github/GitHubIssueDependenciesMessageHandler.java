package de.tum.in.www1.hephaestus.gitprovider.issuedependency.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubWebhookAction;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.GitHubIssueProcessor;
import de.tum.in.www1.hephaestus.gitprovider.issuedependency.github.dto.GitHubIssueDependenciesEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GitHub issue_dependencies webhook events.
 */
@Component
public class GitHubIssueDependenciesMessageHandler extends GitHubMessageHandler<GitHubIssueDependenciesEventDTO> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubIssueDependenciesMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubIssueProcessor issueProcessor;
    private final GitHubIssueDependencySyncService issueDependencySyncService;

    GitHubIssueDependenciesMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubIssueProcessor issueProcessor,
        GitHubIssueDependencySyncService issueDependencySyncService
    ) {
        super(GitHubIssueDependenciesEventDTO.class);
        this.contextFactory = contextFactory;
        this.issueProcessor = issueProcessor;
        this.issueDependencySyncService = issueDependencySyncService;
    }

    @Override
    protected String getEventKey() {
        return "issue_dependencies";
    }

    @Override
    @Transactional
    protected void handleEvent(GitHubIssueDependenciesEventDTO event) {
        var blockedIssueDto = event.blockedIssue();
        var blockingIssueDto = event.blockingIssue();

        if (blockedIssueDto == null || blockingIssueDto == null) {
            logger.warn("Received issue_dependencies event with missing data");
            return;
        }

        logger.info(
            "Received issue_dependencies event: action={}, blocked=#{}, blocking=#{}, repo={}",
            event.action(),
            blockedIssueDto.number(),
            blockingIssueDto.number(),
            event.repository() != null ? event.repository().fullName() : "unknown"
        );

        ProcessingContext context = contextFactory.forWebhookEvent(event).orElse(null);
        if (context == null) {
            return;
        }

        // Ensure both issues exist
        issueProcessor.process(blockedIssueDto, context);
        issueProcessor.process(blockingIssueDto, context);

        // Process dependency relationship
        Long blockedIssueId = blockedIssueDto.getDatabaseId();
        Long blockingIssueId = blockingIssueDto.getDatabaseId();

        switch (event.actionType()) {
            case ADDED -> issueDependencySyncService.processIssueDependencyEvent(blockedIssueId, blockingIssueId, true);
            case REMOVED -> issueDependencySyncService.processIssueDependencyEvent(
                blockedIssueId,
                blockingIssueId,
                false
            );
            default -> logger.debug("Unhandled issue_dependencies action: {}", event.action());
        }
    }
}
