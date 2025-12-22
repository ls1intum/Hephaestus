package de.tum.in.www1.hephaestus.gitprovider.subissue.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.GitHubIssueProcessor;
import de.tum.in.www1.hephaestus.gitprovider.subissue.github.dto.GitHubSubIssuesEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GitHub sub_issues webhook events.
 * <p>
 * Uses DTOs directly (no hub4j) for complete field coverage.
 */
@Component
public class GitHubSubIssuesMessageHandler extends GitHubMessageHandler<GitHubSubIssuesEventDTO> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubSubIssuesMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubIssueProcessor issueProcessor;
    private final IssueRepository issueRepository;

    GitHubSubIssuesMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubIssueProcessor issueProcessor,
        IssueRepository issueRepository
    ) {
        super(GitHubSubIssuesEventDTO.class);
        this.contextFactory = contextFactory;
        this.issueProcessor = issueProcessor;
        this.issueRepository = issueRepository;
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

        // Handle sub-issue relationship
        Issue parentIssue = issueRepository.findById(parentIssueDto.getDatabaseId()).orElse(null);
        Issue subIssue = issueRepository.findById(subIssueDto.getDatabaseId()).orElse(null);

        if (parentIssue != null && subIssue != null) {
            switch (event.action()) {
                case "sub_issue_added", "parent_issue_added" -> {
                    subIssue.setParentIssue(parentIssue);
                    issueRepository.save(subIssue);
                    logger.info("Linked sub-issue #{} to parent #{}", subIssueDto.number(), parentIssueDto.number());
                }
                case "sub_issue_removed", "parent_issue_removed" -> {
                    subIssue.setParentIssue(null);
                    issueRepository.save(subIssue);
                    logger.info(
                        "Unlinked sub-issue #{} from parent #{}",
                        subIssueDto.number(),
                        parentIssueDto.number()
                    );
                }
                default -> logger.debug("Unhandled sub_issues action: {}", event.action());
            }
        }
    }
}
