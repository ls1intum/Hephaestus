package de.tum.in.www1.hephaestus.profile;

import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Workspace-scoped queries for pull request reviews in the profile context.
 *
 * <p>This repository lives in the profile package because it joins gitprovider entities
 * with workspace entities (RepositoryToMonitor). The gitprovider package should not
 * depend on workspace entities to maintain clean architecture.
 *
 * <p>These queries are used for user profile display where workspace context is required.
 */
@Repository
public interface ProfileReviewQueryRepository extends JpaRepository<PullRequestReview, Long> {
    /**
     * Finds all reviews by an author since a given time, scoped to a workspace's monitored repositories.
     *
     * @param authorLogin the author's login (case-insensitive)
     * @param activitySince the start time (inclusive)
     * @param workspaceId the workspace to scope to
     * @return reviews by the author in monitored repositories
     */
    @Query(
        value = """
        SELECT prr
        FROM PullRequestReview prr
        LEFT JOIN FETCH prr.author
        LEFT JOIN FETCH prr.pullRequest pr
        LEFT JOIN FETCH pr.repository r
        LEFT JOIN FETCH prr.comments
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = r.nameWithOwner
        WHERE prr.author.login ILIKE :authorLogin
            AND prr.submittedAt >= :activitySince
            AND rtm.workspace.id = :workspaceId
        ORDER BY prr.submittedAt DESC
        """
    )
    List<PullRequestReview> findAllByAuthorLoginSince(
        @Param("authorLogin") String authorLogin,
        @Param("activitySince") Instant activitySince,
        @Param("workspaceId") Long workspaceId
    );

    /**
     * Finds all reviews by an author in a timeframe, scoped to a workspace's monitored repositories.
     *
     * @param authorLogin the author's login (case-insensitive)
     * @param after start of timeframe (inclusive)
     * @param before end of timeframe (inclusive)
     * @param workspaceId the workspace to scope to
     * @return reviews by the author in monitored repositories
     */
    @Query(
        value = """
        SELECT prr
        FROM PullRequestReview prr
        LEFT JOIN FETCH prr.author
        LEFT JOIN FETCH prr.pullRequest pr
        LEFT JOIN FETCH pr.repository r
        LEFT JOIN FETCH prr.comments
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = r.nameWithOwner
        WHERE prr.author.login ILIKE :authorLogin
            AND prr.submittedAt BETWEEN :after AND :before
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

    /**
     * Finds the earliest submission date for a user's reviews in a workspace.
     *
     * @param workspaceId the workspace to scope to
     * @param userId the user ID
     * @return the earliest submission instant, or null if no reviews exist
     */
    @Query(
        value = """
        SELECT MIN(prr.submittedAt)
        FROM PullRequestReview prr
        JOIN prr.pullRequest pr
        JOIN pr.repository r
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = r.nameWithOwner
        WHERE prr.author.id = :userId
            AND rtm.workspace.id = :workspaceId
        """
    )
    Instant findEarliestSubmissionInstant(@Param("workspaceId") Long workspaceId, @Param("userId") Long userId);
}
