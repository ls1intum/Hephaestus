package de.tum.in.www1.hephaestus.gitprovider.issue.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayloadIssueWithType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles all GitHub issue webhook events including typed/untyped actions.
 * <p>
 * Uses {@link GHEventPayloadIssueWithType} which extends hub4j's Issue payload
 * to include the "type" field for typed/untyped events.
 * <p>
 * Issue type processing is delegated to {@link GitHubIssueSyncService} to
 * ensure
 * consistent behavior between webhook and REST API sync.
 */
@Component
public class GitHubIssueMessageHandler extends GitHubMessageHandler<GHEventPayloadIssueWithType> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubIssueMessageHandler.class);

    private final IssueRepository issueRepository;
    private final GitHubIssueSyncService issueSyncService;
    private final GitHubRepositorySyncService repositorySyncService;

    public GitHubIssueMessageHandler(
        IssueRepository issueRepository,
        GitHubIssueSyncService issueSyncService,
        GitHubRepositorySyncService repositorySyncService
    ) {
        super(GHEventPayloadIssueWithType.class);
        this.issueRepository = issueRepository;
        this.issueSyncService = issueSyncService;
        this.repositorySyncService = repositorySyncService;
    }

    @Override
    protected void handleEvent(GHEventPayloadIssueWithType eventPayload) {
        var action = eventPayload.getAction();
        var repository = eventPayload.getRepository();
        var ghIssue = eventPayload.getIssue();

        logger.info(
            "Received issue event for repository: {}, issue: {}, action: {}",
            repository.getFullName(),
            ghIssue.getNumber(),
            action
        );

        repositorySyncService.processRepository(repository);

        if ("deleted".equals(action)) {
            issueRepository.deleteById(ghIssue.getId());
            return;
        }

        Issue issue;

        // For typed events, pass the issue type to the sync service for linking
        if (eventPayload.isTypedAction() && eventPayload.getIssueType() != null) {
            issue = issueSyncService.processIssue(ghIssue, eventPayload.getIssueType(), repository.getOwnerName());
            if (issue != null) {
                logger.info("Set issue type '{}' for issue #{}", issue.getIssueType().getName(), issue.getNumber());
            }
        } else {
            // Standard issue processing
            issue = issueSyncService.processIssue(ghIssue);
        }

        if (issue == null) {
            return;
        }

        // Handle untyped action - clear the issue type
        if (eventPayload.isUntypedAction()) {
            issueSyncService.clearIssueType(issue);
        }
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.ISSUES;
    }
}
