package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for PullRequestReview entities.
 *
 * <p>This repository contains domain-agnostic queries for the gitprovider domain.
 * Scope-filtered queries (those that join with host application entities)
 * belong in the host application to maintain clean architecture boundaries.
 *
 * @see de.tum.in.www1.hephaestus.leaderboard.LeaderboardReviewQueryRepository
 */
@Repository
public interface PullRequestReviewRepository extends JpaRepository<PullRequestReview, Long> {
    /**
     * Batch fetch reviews by IDs with all related entities eagerly loaded.
     *
     * <p>Used by the profile module to hydrate ActivityEvent target entities.
     * Fetches author, pullRequest, repository, and comments in one query to avoid N+1.
     *
     * @param ids the review IDs to fetch
     * @return reviews with related entities eagerly loaded
     */
    @Query(
        """
        SELECT prr
        FROM PullRequestReview prr
        LEFT JOIN FETCH prr.author
        LEFT JOIN FETCH prr.pullRequest pr
        LEFT JOIN FETCH pr.repository
        LEFT JOIN FETCH prr.comments
        WHERE prr.id IN :ids
        """
    )
    List<PullRequestReview> findAllByIdWithRelations(@Param("ids") Collection<Long> ids);

    /**
     * Find all reviews by a specific author within a time range, scoped to a workspace.
     *
     * <p>Used by the profile module to show all review activity directly from the source,
     * independent of ActivityEvent records.
     *
     * @param authorLogin the login of the review author
     * @param after start of time range (inclusive)
     * @param before end of time range (exclusive)
     * @param workspaceId the workspace to scope the query to
     * @return reviews with related entities eagerly loaded, ordered by submittedAt descending
     */
    @Query(
        """
        SELECT prr
        FROM PullRequestReview prr
        LEFT JOIN FETCH prr.author
        LEFT JOIN FETCH prr.pullRequest pr
        LEFT JOIN FETCH pr.repository repo
        LEFT JOIN FETCH prr.comments
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = repo.nameWithOwner
        WHERE prr.author.login = :authorLogin
            AND prr.submittedAt >= :after
            AND prr.submittedAt < :before
            AND prr.author.type = 'USER'
            AND rtm.workspace.id = :workspaceId
        ORDER BY prr.submittedAt DESC
        """
    )
    List<PullRequestReview> findAllByAuthorLoginInTimeframe(
        @Param("authorLogin") String authorLogin,
        @Param("after") Instant after,
        @Param("before") Instant before,
        @Param("workspaceId") Long workspaceId
    );
}
