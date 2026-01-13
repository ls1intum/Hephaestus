package de.tum.in.www1.hephaestus.profile;

import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Workspace-scoped queries for issue comments in the profile context.
 *
 * <p>This repository lives in the profile package because it joins gitprovider entities
 * with workspace entities (RepositoryToMonitor). The gitprovider package should not
 * depend on workspace entities to maintain clean architecture.
 *
 * <p>These queries are used for user profile display where workspace context is required.
 */
@Repository
public interface ProfileCommentQueryRepository extends JpaRepository<IssueComment, Long> {
    /**
     * Finds all comments by an author since a given time, scoped to a workspace's monitored repositories.
     *
     * @param authorLogin the author's login (case-insensitive)
     * @param activitySince the start time (inclusive)
     * @param onlyFromPullRequests if true, only include comments on pull requests
     * @param workspaceId the workspace to scope to
     * @return comments by the author in monitored repositories
     */
    @Query(
        """
        SELECT ic
        FROM IssueComment ic
        LEFT JOIN FETCH ic.author
        LEFT JOIN FETCH ic.issue i
        LEFT JOIN FETCH i.repository r
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = r.nameWithOwner
        WHERE
            ic.author.login ILIKE :authorLogin
            AND ic.createdAt >= :activitySince
            AND rtm.workspace.id = :workspaceId
            AND (:onlyFromPullRequests = false OR i.htmlUrl LIKE '%/pull/%')
        ORDER BY ic.createdAt DESC
        """
    )
    List<IssueComment> findAllByAuthorLoginSince(
        @Param("authorLogin") String authorLogin,
        @Param("activitySince") Instant activitySince,
        @Param("onlyFromPullRequests") boolean onlyFromPullRequests,
        @Param("workspaceId") Long workspaceId
    );

    /**
     * Finds all comments by an author in a timeframe, scoped to a workspace's monitored repositories.
     *
     * @param authorLogin the author's login (case-insensitive)
     * @param after start of timeframe (inclusive)
     * @param before end of timeframe (inclusive)
     * @param onlyFromPullRequests if true, only include comments on pull requests
     * @param workspaceId the workspace to scope to
     * @return comments by the author in monitored repositories
     */
    @Query(
        """
        SELECT ic
        FROM IssueComment ic
        LEFT JOIN FETCH ic.author
        LEFT JOIN FETCH ic.issue i
        LEFT JOIN FETCH i.repository r
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = r.nameWithOwner
        WHERE
            ic.author.login ILIKE :authorLogin
            AND ic.createdAt BETWEEN :after AND :before
            AND rtm.workspace.id = :workspaceId
            AND (:onlyFromPullRequests = false OR i.htmlUrl LIKE '%/pull/%')
        ORDER BY ic.createdAt DESC
        """
    )
    List<IssueComment> findAllByAuthorLoginInTimeframe(
        @Param("authorLogin") String authorLogin,
        @Param("after") Instant after,
        @Param("before") Instant before,
        @Param("onlyFromPullRequests") boolean onlyFromPullRequests,
        @Param("workspaceId") Long workspaceId
    );

    /**
     * Finds the earliest comment date for a user in a workspace.
     *
     * @param workspaceId the workspace to scope to
     * @param userId the user ID
     * @return the earliest creation instant, or null if no comments exist
     */
    @Query(
        """
        SELECT MIN(ic.createdAt)
        FROM IssueComment ic
        JOIN ic.issue i
        JOIN i.repository r
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = r.nameWithOwner
        WHERE ic.author.id = :userId
            AND rtm.workspace.id = :workspaceId
        """
    )
    Instant findEarliestCreatedAt(@Param("workspaceId") Long workspaceId, @Param("userId") Long userId);
}
