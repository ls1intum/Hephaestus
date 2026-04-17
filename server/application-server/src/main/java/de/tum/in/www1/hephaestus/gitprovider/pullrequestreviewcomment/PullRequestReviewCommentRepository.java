package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment;

import java.util.Collection;
import java.util.List;
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

    /**
     * Fetch all review comments for a pull request with their authors eagerly loaded.
     *
     * @param pullRequestId the pull request ID
     * @return comments with author eagerly loaded, ordered by creation time
     */
    @Query(
        """
        SELECT prrc
        FROM PullRequestReviewComment prrc
        LEFT JOIN FETCH prrc.author
        WHERE prrc.pullRequest.id = :pullRequestId
        ORDER BY prrc.createdAt ASC
        """
    )
    List<PullRequestReviewComment> findByPullRequestIdWithAuthorOrderByCreatedAt(
        @Param("pullRequestId") Long pullRequestId
    );

    @Query(
        """
        SELECT prrc
        FROM PullRequestReviewComment prrc
        LEFT JOIN FETCH prrc.author
        LEFT JOIN FETCH prrc.pullRequest pr
        LEFT JOIN FETCH pr.author
        LEFT JOIN FETCH pr.repository
        WHERE prrc.id IN :ids
        """
    )
    List<PullRequestReviewComment> findAllByIdWithRelations(@Param("ids") Collection<Long> ids);

    Optional<PullRequestReviewComment> findFirstByThreadIdOrderByCreatedAtAsc(Long threadId);
}
