package de.tum.in.www1.hephaestus.workspace.settings;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for {@link WorkspaceTeamLabelFilter} entities.
 *
 * <p>Manages workspace-scoped team label filters, providing queries for:
 * <ul>
 *   <li>Fetching label filters by workspace and/or team</li>
 *   <li>Finding labels associated with a team in a workspace</li>
 *   <li>Deleting specific label filter associations</li>
 * </ul>
 */
@Repository
@WorkspaceAgnostic("Queried by explicit workspace ID - settings queries always workspace-scoped")
public interface WorkspaceTeamLabelFilterRepository
    extends JpaRepository<WorkspaceTeamLabelFilter, WorkspaceTeamLabelFilter.Id>
{
    /**
     * Finds all label filters for a given workspace.
     *
     * @param workspaceId the workspace ID
     * @return list of label filters for the workspace
     */
    List<WorkspaceTeamLabelFilter> findByWorkspaceId(Long workspaceId);

    /**
     * Finds all label filters for a specific workspace and team combination.
     *
     * @param workspaceId the workspace ID
     * @param teamId the team ID
     * @return list of label filters for the workspace and team
     */
    List<WorkspaceTeamLabelFilter> findByWorkspaceIdAndTeamId(Long workspaceId, Long teamId);

    /**
     * Finds all labels associated with a team in a specific workspace.
     *
     * @param workspaceId the workspace ID
     * @param teamId the team ID
     * @return set of labels configured as filters for the team in the workspace
     */
    @Query(
        """
        SELECT wtlf.label
        FROM WorkspaceTeamLabelFilter wtlf
        WHERE wtlf.workspace.id = :workspaceId
          AND wtlf.team.id = :teamId
        """
    )
    Set<Label> findLabelsByWorkspaceAndTeam(@Param("workspaceId") Long workspaceId, @Param("teamId") Long teamId);

    /**
     * Finds all team IDs that have at least one label filter configured in a workspace.
     *
     * @param workspaceId the workspace ID
     * @return set of team IDs with label filters
     */
    @Query(
        """
        SELECT DISTINCT wtlf.team.id
        FROM WorkspaceTeamLabelFilter wtlf
        WHERE wtlf.workspace.id = :workspaceId
        """
    )
    Set<Long> findTeamIdsWithLabelFilters(@Param("workspaceId") Long workspaceId);

    /**
     * Finds all label filters for multiple teams in a workspace.
     *
     * <p>This batch query retrieves all label filter associations for the specified teams
     * in a single database query, avoiding N+1 query patterns when processing multiple teams.
     *
     * @param workspaceId the workspace ID
     * @param teamIds the set of team IDs to fetch label filters for
     * @return list of label filters for the specified teams in the workspace
     */
    @Query(
        """
        SELECT wtlf
        FROM WorkspaceTeamLabelFilter wtlf
        JOIN FETCH wtlf.label
        WHERE wtlf.workspace.id = :workspaceId
          AND wtlf.team.id IN :teamIds
        """
    )
    List<WorkspaceTeamLabelFilter> findByWorkspaceIdAndTeamIds(
        @Param("workspaceId") Long workspaceId,
        @Param("teamIds") Set<Long> teamIds
    );

    /**
     * Deletes a specific label filter by its composite key components.
     *
     * @param workspaceId the workspace ID
     * @param teamId the team ID
     * @param labelId the label ID
     */
    @Modifying
    @Transactional
    @Query(
        """
        DELETE FROM WorkspaceTeamLabelFilter wtlf
        WHERE wtlf.workspace.id = :workspaceId
          AND wtlf.team.id = :teamId
          AND wtlf.label.id = :labelId
        """
    )
    void deleteByWorkspaceIdAndTeamIdAndLabelId(
        @Param("workspaceId") Long workspaceId,
        @Param("teamId") Long teamId,
        @Param("labelId") Long labelId
    );

    /**
     * Deletes all label filters for a specific team in a workspace.
     *
     * @param workspaceId the workspace ID
     * @param teamId the team ID
     */
    @Modifying
    @Transactional
    @Query(
        """
        DELETE FROM WorkspaceTeamLabelFilter wtlf
        WHERE wtlf.workspace.id = :workspaceId
          AND wtlf.team.id = :teamId
        """
    )
    void deleteAllByWorkspaceIdAndTeamId(@Param("workspaceId") Long workspaceId, @Param("teamId") Long teamId);

    /**
     * Deletes all label filters for a workspace.
     * Used during workspace purge to clean up settings data.
     *
     * @param workspaceId the workspace ID
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM WorkspaceTeamLabelFilter wtlf WHERE wtlf.workspace.id = :workspaceId")
    void deleteAllByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
