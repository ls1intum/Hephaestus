package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Loads the reviewable SCM artifact a job targets — for the workspace that is paying for it, and with the
 * eager-fetch graph its review path needs. Thin on purpose: it exists so {@link DevTriggerController} does
 * not depend on a {@code @Repository}, which the architecture rules forbid a controller to do.
 *
 * <p>The workspace is a parameter rather than an afterthought. The caller names an artifact by surrogate
 * id and a workspace separately, and nothing in the ids themselves relates the two — so loading the
 * artifact without the ownership check and then submitting it under the named workspace bills that
 * workspace's {@code agent_job} and LLM usage ledger for another one's work. That is not privilege
 * escalation, since only an instance admin gets this far, but the cost estimator for backfill campaigns
 * reads exactly that ledger, so the misattribution outlives the request and quietly skews what a
 * different workspace is later told a campaign will cost.
 */
@Component
class ReviewableArtifactLoader {

    private final PullRequestRepository pullRequestRepository;
    private final IssueRepository issueRepository;
    private final ReviewableArtifactOwnershipRepository ownership;

    ReviewableArtifactLoader(
        PullRequestRepository pullRequestRepository,
        IssueRepository issueRepository,
        ReviewableArtifactOwnershipRepository ownership
    ) {
        this.pullRequestRepository = pullRequestRepository;
        this.issueRepository = issueRepository;
        this.ownership = ownership;
    }

    /** Empty both when no such pull request exists and when this workspace does not monitor it. */
    Optional<PullRequest> findPullRequestForGate(long workspaceId, long pullRequestId) {
        if (!ownership.pullRequestBelongsToWorkspace(workspaceId, pullRequestId)) {
            return Optional.empty();
        }
        return pullRequestRepository.findByIdWithAllForGate(pullRequestId);
    }

    /**
     * Empty both when no such issue exists and when this workspace does not monitor it.
     *
     * <p>Assignees must be fetched too: the gate's role check iterates them.
     */
    Optional<Issue> findIssueForGate(long workspaceId, long issueId) {
        if (!ownership.issueBelongsToWorkspace(workspaceId, issueId)) {
            return Optional.empty();
        }
        return issueRepository.findByIdWithRepositoryAndAssignees(issueId);
    }
}
