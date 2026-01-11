package de.tum.in.www1.hephaestus.leaderboard;

import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Workspace-scoped queries for issue comments in the leaderboard context.
 *
 * <p>This repository lives in the leaderboard package because it joins gitprovider entities
 * with workspace entities (RepositoryToMonitor, WorkspaceTeamRepositorySettings). The gitprovider
 * package should not depend on workspace entities to maintain clean architecture.
 *
 * <p>These queries are used for leaderboard calculations where workspace context is required.
 */
@Repository
public interface LeaderboardCommentQueryRepository extends JpaRepository<IssueComment, Long> {

    /**
     * Finds all issue comments in a timeframe for a workspace, scoped to repositories monitored by the workspace.
     *
     * @param after start of timeframe (inclusive)
     * @param before end of timeframe (inclusive)
     * @param onlyFromPullRequests if true, only include comments on pull requests
     * @param workspaceId the workspace to scope to
     * @return comments in the timeframe for monitored repositories
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
            ic.createdAt BETWEEN :after AND :before
            AND ic.author.type = 'USER'
            AND rtm.workspace.id = :workspaceId
            AND (:onlyFromPullRequests = false OR i.htmlUrl LIKE '%/pull/%')
        ORDER BY ic.createdAt DESC
        """
    )
    List<IssueComment> findAllInTimeframe(
        @Param("after") Instant after,
        @Param("before") Instant before,
        @Param("onlyFromPullRequests") boolean onlyFromPullRequests,
        @Param("workspaceId") Long workspaceId
    );

    /**
     * Finds all issue comments in a timeframe for specific teams, excluding hidden repositories.
     *
     * <p>This query joins with WorkspaceTeamRepositorySettings to exclude repositories
     * that are marked as hidden from contributions for the specified teams.
     *
     * @param after start of timeframe (inclusive)
     * @param before end of timeframe (inclusive)
     * @param teamIds the team IDs to filter by
     * @param onlyFromPullRequests if true, only include comments on pull requests
     * @param workspaceId the workspace to scope to
     * @return comments in the timeframe for team members, excluding hidden repositories
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
            ic.createdAt BETWEEN :after AND :before
            AND ic.author.type = 'USER'
            AND rtm.workspace.id = :workspaceId
            AND NOT EXISTS (
                SELECT 1
                FROM WorkspaceTeamRepositorySettings wtrs
                WHERE wtrs.repository = r
                AND wtrs.workspace.id = :workspaceId
                AND wtrs.team.id IN :teamIds
                AND wtrs.hiddenFromContributions = true
            )
            AND EXISTS (
                SELECT 1
                FROM TeamMembership tm
                WHERE tm.team.id IN :teamIds
                AND tm.user = ic.author
            )
            AND (:onlyFromPullRequests = false OR i.htmlUrl LIKE '%/pull/%')
        ORDER BY ic.createdAt DESC
        """
    )
    List<IssueComment> findAllInTimeframeOfTeams(
        @Param("after") Instant after,
        @Param("before") Instant before,
        @Param("teamIds") Collection<Long> teamIds,
        @Param("onlyFromPullRequests") boolean onlyFromPullRequests,
        @Param("workspaceId") Long workspaceId
    );
}
