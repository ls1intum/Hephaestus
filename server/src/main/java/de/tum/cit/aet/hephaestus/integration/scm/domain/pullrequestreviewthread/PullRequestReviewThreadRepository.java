package de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreviewthread;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Threads are scoped through their pull request via the
 * pull_request -> repository -> workspace chain.
 */
@WorkspaceAgnostic("Threads scoped through pull_request_id -> repository.workspace_id")
public interface PullRequestReviewThreadRepository extends JpaRepository<PullRequestReviewThread, Long> {
    java.util.Optional<PullRequestReviewThread> findByNativeIdAndProviderId(Long nativeId, Long providerId);

    java.util.Optional<PullRequestReviewThread> findByNodeIdAndProviderId(String nodeId, Long providerId);

    /**
     * All review threads of a pull request, with {@code resolvedBy} and the thread's {@code comments}
     * (plus their authors) eagerly fetched so the cross-context provider can attribute who resolved each
     * thread AND tell whether the thread is Hephaestus's own posted note — without an N+1 lazy load.
     * (The {@code rootComment} FK is not populated by the sync, so the comment set is the reliable source
     * for the marker check.) Read-only context materialisation; the caller establishes workspace scope.
     */
    @org.springframework.data.jpa.repository.Query(
        """
        SELECT DISTINCT t
        FROM PullRequestReviewThread t
        LEFT JOIN FETCH t.resolvedBy
        LEFT JOIN FETCH t.comments c
        LEFT JOIN FETCH c.author
        WHERE t.pullRequest.id = :pullRequestId
        """
    )
    java.util.List<PullRequestReviewThread> findAllByPullRequestIdWithResolvedBy(
        @org.springframework.data.repository.query.Param("pullRequestId") Long pullRequestId
    );
}
