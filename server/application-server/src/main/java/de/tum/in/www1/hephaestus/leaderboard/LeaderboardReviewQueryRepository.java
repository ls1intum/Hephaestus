package de.tum.in.www1.hephaestus.leaderboard;

import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Workspace-scoped queries for pull request reviews in the leaderboard context.
 *
 * <p>This repository lives in the leaderboard package because it joins gitprovider entities
 * with workspace entities (RepositoryToMonitor, WorkspaceTeamRepositorySettings). The gitprovider
 * package should not depend on workspace entities to maintain clean architecture.
 *
 * <p>These queries are used for leaderboard calculations where workspace context is required.
 */
@Repository
public interface LeaderboardReviewQueryRepository extends JpaRepository<PullRequestReview, Long> {

    /**
     * Finds all reviews in a timeframe for a workspace, scoped to repositories monitored by the workspace.
     *
     * @param after start of timeframe (inclusive)
     * @param before end of timeframe (inclusive)
     * @param workspaceId the workspace to scope to
     * @return reviews in the timeframe for monitored repositories
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
        WHERE
            prr.submittedAt BETWEEN :after AND :before
            AND prr.author.type = 'USER'
            AND rtm.workspace.id = :workspaceId
        ORDER BY prr.submittedAt DESC
        """
    )
    List<PullRequestReview> findAllInTimeframe(
        @Param("after") Instant after,
        @Param("before") Instant before,
        @Param("workspaceId") Long workspaceId
    );

    /**
     * Finds all reviews in a timeframe for specific teams, excluding hidden repositories.
     *
     * <p>This query joins with WorkspaceTeamRepositorySettings to exclude repositories
     * that are marked as hidden from contributions for the specified teams.
     *
     * @param after start of timeframe (inclusive)
     * @param before end of timeframe (inclusive)
     * @param teamIds the team IDs to filter by
     * @param workspaceId the workspace to scope to
     * @return reviews in the timeframe for team members, excluding hidden repositories
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
        WHERE
            prr.submittedAt BETWEEN :after AND :before
            AND prr.author.type = 'USER'
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
                AND tm.user = prr.author
            )
        ORDER BY prr.submittedAt DESC
        """
    )
    List<PullRequestReview> findAllInTimeframeOfTeams(
        @Param("after") Instant after,
        @Param("before") Instant before,
        @Param("teamIds") Collection<Long> teamIds,
        @Param("workspaceId") Long workspaceId
    );
}
