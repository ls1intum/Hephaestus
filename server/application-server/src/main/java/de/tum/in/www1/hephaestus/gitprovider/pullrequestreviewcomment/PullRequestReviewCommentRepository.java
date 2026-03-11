package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for pull request review comment entities.
 *
 * <p>Comments are scoped through their thread which has scope through
 * the Thread -> PullRequest -> Repository -> Organization chain.
 */
public interface PullRequestReviewCommentRepository extends JpaRepository<PullRequestReviewComment, Long> {
    Optional<PullRequestReviewComment> findByNativeIdAndProviderId(Long nativeId, Long providerId);

    boolean existsByNativeIdAndProviderId(Long nativeId, Long providerId);

    /**
     * Fetch a single review comment with its pull request and PR author eagerly loaded.
     *
     * <p>Used by the achievement evaluator to check PR authorship without N+1 lazy loads.
     *
     * @param id the comment ID
     * @return comment with pullRequest and pullRequest.author eagerly loaded
     */
    @Query(
        """
        SELECT prrc
        FROM PullRequestReviewComment prrc
        LEFT JOIN FETCH prrc.pullRequest pr
        LEFT JOIN FETCH pr.author
        WHERE prrc.id = :id
        """
    )
    Optional<PullRequestReviewComment> findByIdWithPullRequestAuthor(@Param("id") Long id);
}
