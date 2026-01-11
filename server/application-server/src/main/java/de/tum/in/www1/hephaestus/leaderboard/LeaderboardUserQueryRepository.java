package de.tum.in.www1.hephaestus.leaderboard;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Workspace-scoped queries for users in the leaderboard context.
 *
 * <p>This repository lives in the leaderboard package because it joins gitprovider entities
 * with workspace entities (RepositoryToMonitor, WorkspaceTeamRepositorySettings). The gitprovider
 * package should not depend on workspace entities to maintain clean architecture.
 *
 * <p>These queries are used for leaderboard calculations where workspace context is required.
 */
@Repository
public interface LeaderboardUserQueryRepository extends JpaRepository<User, Long> {

    /**
     * Finds all users who have contributed to a set of teams, excluding contributions from
     * repositories marked as hidden for those teams.
     *
     * <p>A user is considered to have contributed if they have authored a pull request
     * in a monitored repository that is not hidden from contributions for the specified teams.
     *
     * @param teamIds the team IDs to check contributions for
     * @return users who have contributed to the specified teams
     */
    @Query(
        """
        SELECT DISTINCT pr.author
        FROM PullRequest pr
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = pr.repository.nameWithOwner
        JOIN TeamMembership tm ON tm.user = pr.author
        WHERE tm.team.id IN :teamIds
        AND NOT EXISTS (
            SELECT 1
            FROM WorkspaceTeamRepositorySettings wtrs
            WHERE wtrs.repository = pr.repository
            AND wtrs.workspace.id = rtm.workspace.id
            AND wtrs.team.id IN :teamIds
            AND wtrs.hiddenFromContributions = true
        )
        """
    )
    Set<User> findAllContributingToTeams(@Param("teamIds") Collection<Long> teamIds);

    /**
     * Finds all users who have contributed to a single team.
     *
     * @param teamId the team ID to check contributions for
     * @return users who have contributed to the specified team
     */
    default Set<User> findAllContributingToTeam(Long teamId) {
        if (teamId == null) {
            return Set.of();
        }
        return findAllContributingToTeams(List.of(teamId));
    }
}
