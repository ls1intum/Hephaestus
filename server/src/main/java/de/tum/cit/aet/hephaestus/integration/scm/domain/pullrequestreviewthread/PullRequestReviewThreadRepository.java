package de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreviewthread;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Threads are scoped through their pull request via the
 * pull_request -> repository -> workspace chain.
 */
@WorkspaceAgnostic("Threads scoped through pull_request_id -> repository.workspace_id")
public interface PullRequestReviewThreadRepository extends JpaRepository<PullRequestReviewThread, Long> {
    Optional<PullRequestReviewThread> findByNativeIdAndProviderId(Long nativeId, Long providerId);

    Optional<PullRequestReviewThread> findByNodeIdAndProviderId(String nodeId, Long providerId);

    /**
     * All review threads of a pull request, with {@code resolvedBy} and the thread's {@code comments}
     * (plus their authors) eagerly fetched so the cross-context provider can attribute who resolved each
     * thread AND tell whether the thread is Hephaestus's own posted note — without an N+1 lazy load.
     * (The {@code rootComment} FK is not populated by the sync, so the comment set is the reliable source
     * for the marker check.) Read-only context materialisation; the caller establishes workspace scope.
     *
     * <p>Ordered because the caller materialises the result into a CAPPED context file: without it the plan's
     * arbitrary order would vary both which threads the detector sees and in what order — and so would the
     * {@code agent_job.inputs_digest} that claims to identify those inputs.
     */
    @Query(
        """
        SELECT DISTINCT t
        FROM PullRequestReviewThread t
        LEFT JOIN FETCH t.resolvedBy
        LEFT JOIN FETCH t.comments c
        LEFT JOIN FETCH c.author
        WHERE t.pullRequest.id = :pullRequestId
        ORDER BY t.id
        """
    )
    List<PullRequestReviewThread> findAllByPullRequestIdWithResolvedBy(@Param("pullRequestId") Long pullRequestId);
}
