package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for pull request review comment entities.
 *
 * <p>Comments are scoped through their thread which has scope through
 * the Thread -> PullRequest -> Repository -> Organization chain.
 */
public interface PullRequestReviewCommentRepository extends JpaRepository<PullRequestReviewComment, Long> {
    java.util.Optional<PullRequestReviewComment> findByNativeIdAndProviderId(Long nativeId, Long providerId);

    boolean existsByNativeIdAndProviderId(Long nativeId, Long providerId);
}
