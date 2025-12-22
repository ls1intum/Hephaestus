package de.tum.in.www1.hephaestus.gitprovider.issuedependency.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.GitHubIssueProcessor;
import de.tum.in.www1.hephaestus.gitprovider.issuedependency.github.dto.GitHubIssueDependenciesEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GitHub issue_dependencies webhook events.
 * <p>
 * Uses DTOs directly (no hub4j) for complete field coverage.
 * <p>
 * TODO: Full dependency tracking when IssueDependency entity is created.
 */
@Component
public class GitHubIssueDependenciesMessageHandler extends GitHubMessageHandler<GitHubIssueDependenciesEventDTO> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubIssueDependenciesMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubIssueProcessor issueProcessor;

    GitHubIssueDependenciesMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubIssueProcessor issueProcessor
    ) {
        super(GitHubIssueDependenciesEventDTO.class);
        this.contextFactory = contextFactory;
        this.issueProcessor = issueProcessor;
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

        // Log the action - full dependency tracking can be added when entity exists
        switch (event.action()) {
            case "added" -> logger.info(
                "Dependency added: #{} blocked by #{}",
                blockedIssueDto.number(),
                blockingIssueDto.number()
            );
            case "removed" -> logger.info(
                "Dependency removed: #{} blocked by #{}",
                blockedIssueDto.number(),
                blockingIssueDto.number()
            );
            default -> logger.debug("Unhandled issue_dependencies action: {}", event.action());
        }
    }
}
