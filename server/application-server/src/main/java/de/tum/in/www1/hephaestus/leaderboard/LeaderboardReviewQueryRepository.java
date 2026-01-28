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
 *
 * <p><strong>Time range convention:</strong> All timeframe queries use half-open intervals
 * [since, until) - inclusive start, exclusive end. This is the standard convention for
 * time ranges and ensures consistency with {@link ActivityEventRepository}.
 */
@Repository
public interface LeaderboardReviewQueryRepository extends JpaRepository<PullRequestReview, Long> {
    /**
     * Finds all reviews in a timeframe for specific actors, scoped to repositories monitored by the workspace.
     *
     * @param since start of timeframe (inclusive)
     * @param until end of timeframe (exclusive)
     * @param actorIds the user IDs to filter by
     * @param workspaceId the workspace to scope to
     * @return reviews in the timeframe for monitored repositories by the specified actors
     */
    @Query(
        value = """
        SELECT prr
        FROM PullRequestReview prr
        LEFT JOIN FETCH prr.author
        LEFT JOIN FETCH prr.pullRequest pr
        LEFT JOIN FETCH pr.repository r
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = r.nameWithOwner
        WHERE prr.submittedAt >= :since
            AND prr.submittedAt < :until
            AND prr.author.id IN :actorIds
            AND prr.author.type = de.tum.in.www1.hephaestus.gitprovider.user.User$Type.USER
            AND rtm.workspace.id = :workspaceId
        ORDER BY prr.submittedAt DESC
        """
    )
    List<PullRequestReview> findAllInTimeframeByActors(
        @Param("since") Instant since,
        @Param("until") Instant until,
        @Param("actorIds") Collection<Long> actorIds,
        @Param("workspaceId") Long workspaceId
    );

    /**
     * Finds all reviews in a timeframe for specific actors and teams, excluding hidden repositories.
     *
     * <p>This query joins with WorkspaceTeamRepositorySettings to exclude repositories
     * that are marked as hidden from contributions for the specified teams. It also ensures
     * that reviews are only counted on repositories the team has access to via TeamRepositoryPermission.
     *
     * <p>Additionally, this query applies label filtering using WorkspaceTeamLabelFilter:
     * <ul>
     *   <li>If no label filters are configured for the teams on a repository, all PRs are included</li>
     *   <li>If label filters exist, only PRs with at least one matching label are included</li>
     * </ul>
     *
     * @param since start of timeframe (inclusive)
     * @param until end of timeframe (exclusive)
     * @param actorIds the user IDs to filter by
     * @param teamIds the team IDs to filter by (for repository visibility)
     * @param workspaceId the workspace to scope to
     * @return reviews in the timeframe for the specified actors, excluding hidden repositories
     */
    @Query(
        value = """
        SELECT prr
        FROM PullRequestReview prr
        LEFT JOIN FETCH prr.author
        LEFT JOIN FETCH prr.pullRequest pr
        LEFT JOIN FETCH pr.repository r
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = r.nameWithOwner
        WHERE prr.submittedAt >= :since
            AND prr.submittedAt < :until
            AND prr.author.id IN :actorIds
            AND prr.author.type = de.tum.in.www1.hephaestus.gitprovider.user.User$Type.USER
            AND rtm.workspace.id = :workspaceId
            AND EXISTS (
                SELECT 1 FROM TeamRepositoryPermission trp
                WHERE trp.repository = pr.repository
                AND trp.team.id IN :teamIds
            )
            AND NOT EXISTS (
                SELECT 1
                FROM WorkspaceTeamRepositorySettings wtrs
                WHERE wtrs.repository = r
                AND wtrs.workspace.id = :workspaceId
                AND wtrs.team.id IN :teamIds
                AND wtrs.hiddenFromContributions = true
            )
            AND (
                NOT EXISTS (
                    SELECT 1 FROM WorkspaceTeamLabelFilter wtlf
                    JOIN wtlf.label l
                    WHERE wtlf.workspace.id = :workspaceId
                    AND wtlf.team.id IN :teamIds
                    AND l.repository = pr.repository
                )
                OR
                EXISTS (
                    SELECT 1 FROM WorkspaceTeamLabelFilter wtlf
                    JOIN wtlf.label l
                    WHERE wtlf.workspace.id = :workspaceId
                    AND wtlf.team.id IN :teamIds
                    AND l.repository = pr.repository
                    AND l MEMBER OF pr.labels
                )
            )
        ORDER BY prr.submittedAt DESC
        """
    )
    List<PullRequestReview> findAllInTimeframeByActorsOfTeams(
        @Param("since") Instant since,
        @Param("until") Instant until,
        @Param("actorIds") Collection<Long> actorIds,
        @Param("teamIds") Collection<Long> teamIds,
        @Param("workspaceId") Long workspaceId
    );
}
