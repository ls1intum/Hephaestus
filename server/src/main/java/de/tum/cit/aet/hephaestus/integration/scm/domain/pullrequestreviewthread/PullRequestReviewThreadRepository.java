package de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreviewthread;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for pull request review thread entities.
 *
 * <p>Threads are scoped through their pull request which has scope through
 * the PullRequest -> Repository -> Organization chain.
 */
@WorkspaceAgnostic("Threads scoped through pull_request_id -> repository.workspace_id")
public interface PullRequestReviewThreadRepository extends JpaRepository<PullRequestReviewThread, Long> {
    java.util.Optional<PullRequestReviewThread> findByNativeIdAndProviderId(Long nativeId, Long providerId);

    java.util.Optional<PullRequestReviewThread> findByNodeIdAndProviderId(String nodeId, Long providerId);

    /**
     * All review threads of a pull request, with {@code resolvedBy} eagerly fetched so the
     * cross-context provider can attribute who resolved each thread without an N+1 lazy load.
     * Read-only context materialisation; the caller establishes workspace scope.
     */
    @org.springframework.data.jpa.repository.Query(
        """
        SELECT t
        FROM PullRequestReviewThread t
        LEFT JOIN FETCH t.resolvedBy
        WHERE t.pullRequest.id = :pullRequestId
        """
    )
    java.util.List<PullRequestReviewThread> findAllByPullRequestIdWithResolvedBy(
        @org.springframework.data.repository.query.Param("pullRequestId") Long pullRequestId
    );
}
