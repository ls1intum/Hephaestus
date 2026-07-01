package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Loads the reviewable SCM artifact (pull request or issue) a job targets, each with the eager-fetch
 * graph its review path needs. Keeping "which artifact is this job about" here gives it one home and
 * keeps {@code AgentJobService}'s dependency surface within the god-class budget.
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

    /** Loads an issue with its repository eagerly fetched (gate-bypass path; no role check). */
    Optional<Issue> findIssueWithRepository(long issueId) {
        return issueRepository.findByIdWithRepository(issueId);
    }

    /**
     * Loads an issue with the association graph the detection gate needs — repository AND assignees, since
     * the gate's role check iterates {@code getAssignees()}. Mirrors what the production issue listener
     * loads, so the dev gate-routed path exercises the same eager-fetch precondition.
     */
    Optional<Issue> findIssueForGate(long issueId) {
        return issueRepository.findByIdWithRepositoryAndAssignees(issueId);
    }
}
