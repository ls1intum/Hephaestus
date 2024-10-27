package de.tum.in.www1.hephaestus.gitprovider.issue.github;

import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;

@Component
public class GitHubIssueMessageHandler extends GitHubMessageHandler<GHEventPayload.Issue> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubIssueMessageHandler.class);

    private final IssueRepository issueRepository;
    private final GitHubIssueSyncService issueSyncService;
    private final GitHubRepositorySyncService repositorySyncService;

    private GitHubIssueMessageHandler(
            IssueRepository issueRepository,
            GitHubIssueSyncService issueSyncService,
            GitHubRepositorySyncService repositorySyncService) {
        super(GHEventPayload.Issue.class);
        this.issueRepository = issueRepository;
        this.issueSyncService = issueSyncService;
        this.repositorySyncService = repositorySyncService;
    }

    @Override
    protected void handleEvent(GHEventPayload.Issue eventPayload) {
        var action = eventPayload.getAction();
        var repository = eventPayload.getRepository();
        var issue = eventPayload.getIssue();
        logger.info("Received issue event for repository: {}, issue: {}, action: {}",
                repository.getFullName(),
                issue.getNumber(),
                action);
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
