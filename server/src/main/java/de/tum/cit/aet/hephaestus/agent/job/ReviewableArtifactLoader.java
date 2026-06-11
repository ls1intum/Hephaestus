package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Loads the reviewable SCM artifact (pull request or issue) a job targets, each with the eager-fetch
 * graph its review path needs. Extracted from {@code AgentJobService} so "which artifact is this job
 * about" has one home and the service's dependency surface stays within the god-class budget.
 */
@Component
class ReviewableArtifactLoader {

    private final PullRequestRepository pullRequestRepository;
    private final IssueRepository issueRepository;

    ReviewableArtifactLoader(PullRequestRepository pullRequestRepository, IssueRepository issueRepository) {
        this.pullRequestRepository = pullRequestRepository;
        this.issueRepository = issueRepository;
    }

    /** Loads a pull request with the full association graph the detection gate needs. */
    Optional<PullRequest> findPullRequestForGate(long pullRequestId) {
        return pullRequestRepository.findByIdWithAllForGate(pullRequestId);
    }

    /** Loads an issue with its repository eagerly fetched. */
    Optional<Issue> findIssueWithRepository(long issueId) {
        return issueRepository.findByIdWithRepository(issueId);
    }
}
