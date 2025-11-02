package de.tum.in.www1.hephaestus.gitprovider.issue.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayloadIssueExtended;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GitHubIssueMessageHandler extends GitHubMessageHandler<GHEventPayloadIssueExtended> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubIssueMessageHandler.class);

    private final IssueRepository issueRepository;
    private final GitHubIssueSyncService issueSyncService;
    private final GitHubRepositorySyncService repositorySyncService;

    private GitHubIssueMessageHandler(
        IssueRepository issueRepository,
        GitHubIssueSyncService issueSyncService,
        GitHubRepositorySyncService repositorySyncService
    ) {
        super(GHEventPayloadIssueExtended.class);
        this.issueRepository = issueRepository;
        this.issueSyncService = issueSyncService;
        this.repositorySyncService = repositorySyncService;
    }

    @Override
    protected void handleEvent(GHEventPayloadIssueExtended eventPayload) {
        var action = eventPayload.getAction();
        var repository = eventPayload.getRepository();
        var issue = eventPayload.getIssue();
        logger.info(
            "Received issue event for repository: {}, issue: {}, action: {}",
            repository.getFullName(),
            issue.getNumber(),
            action
        );
        repositorySyncService.processRepository(repository);

        if (action.equals("deleted")) {
            issueRepository.deleteById(issue.getId());
        } else {
            issueSyncService.processIssue(issue);
        }
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.ISSUES;
    }
}
