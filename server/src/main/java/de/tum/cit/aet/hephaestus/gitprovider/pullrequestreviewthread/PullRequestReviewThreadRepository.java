package de.tum.cit.aet.hephaestus.gitprovider.pullrequestreviewthread;

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
}
