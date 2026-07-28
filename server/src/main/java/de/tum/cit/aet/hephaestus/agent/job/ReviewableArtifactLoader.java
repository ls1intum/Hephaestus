package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Loads the reviewable SCM artifact a job targets, each with the eager-fetch graph its review path
 * needs. Thin on purpose: it exists so {@link DevTriggerController} does not depend on a
 * {@code @Repository}, which the architecture rules forbid a controller to do.
 */
@Component
class ReviewableArtifactLoader {

    private final PullRequestRepository pullRequestRepository;
    private final IssueRepository issueRepository;

    ReviewableArtifactLoader(PullRequestRepository pullRequestRepository, IssueRepository issueRepository) {
        this.pullRequestRepository = pullRequestRepository;
        this.issueRepository = issueRepository;
    }

    Optional<PullRequest> findPullRequestForGate(long pullRequestId) {
        return pullRequestRepository.findByIdWithAllForGate(pullRequestId);
    }

    /** Assignees must be fetched too: the gate's role check iterates them. */
    Optional<Issue> findIssueForGate(long issueId) {
        return issueRepository.findByIdWithRepositoryAndAssignees(issueId);
    }
}
