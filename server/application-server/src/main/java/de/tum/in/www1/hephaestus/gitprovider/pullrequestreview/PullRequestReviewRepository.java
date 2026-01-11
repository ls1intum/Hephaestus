package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for PullRequestReview entities.
 *
 * <p>This repository contains only workspace-agnostic queries for the gitprovider domain.
 * Workspace-scoped queries (those that join with RepositoryToMonitor, WorkspaceTeamRepositorySettings,
 * or other workspace entities) belong in the consuming packages (leaderboard, profile, etc.)
 * to maintain clean architecture boundaries.
 *
 * @see de.tum.in.www1.hephaestus.leaderboard.LeaderboardReviewQueryRepository
 * @see de.tum.in.www1.hephaestus.profile.ProfileReviewQueryRepository
 */
@Repository
public interface PullRequestReviewRepository extends JpaRepository<PullRequestReview, Long> {

    /**
     * Find all reviews for a specific pull request by a specific author.
     * Used for calculating per-PR XP (aggregating all reviews together for harmonic mean).
     * PullRequest ID inherently has workspace through repository.
     *
     * @param pullRequestId the pull request ID
     * @param authorId the author ID
     * @return list of reviews by this author on this PR
     */
    @Query(
        value = """
        SELECT prr
        FROM PullRequestReview prr
        LEFT JOIN FETCH prr.author
        LEFT JOIN FETCH prr.pullRequest
        LEFT JOIN FETCH prr.comments
        WHERE prr.pullRequest.id = :pullRequestId
            AND prr.author.id = :authorId
        ORDER BY prr.submittedAt ASC
        """
    )
    List<PullRequestReview> findByPullRequestIdAndAuthorId(
        @Param("pullRequestId") Long pullRequestId,
        @Param("authorId") Long authorId
    );
}
