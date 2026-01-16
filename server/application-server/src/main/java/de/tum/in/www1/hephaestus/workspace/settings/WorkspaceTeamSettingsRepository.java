package de.tum.in.www1.hephaestus.workspace.settings;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link WorkspaceTeamSettings} entities.
 *
 * <p>Manages workspace-scoped team settings, providing queries for:
 * <ul>
 *   <li>Fetching settings by workspace and/or team</li>
 *   <li>Finding hidden teams within a workspace</li>
 * </ul>
 */
@Repository
@WorkspaceAgnostic("Queried by explicit workspace ID - settings queries always workspace-scoped")
public interface WorkspaceTeamSettingsRepository
    extends JpaRepository<WorkspaceTeamSettings, WorkspaceTeamSettings.Id> {
    /**
     * Finds all team settings for a given workspace.
     *
     * @param workspaceId the workspace ID
     * @return list of team settings for the workspace
     */
    List<WorkspaceTeamSettings> findByWorkspaceId(Long workspaceId);

    /**
     * Finds team settings for a specific workspace and team combination.
     *
     * @param workspaceId the workspace ID
     * @param teamId the team ID
     * @return the team settings if they exist
     */
    Optional<WorkspaceTeamSettings> findByWorkspaceIdAndTeamId(Long workspaceId, Long teamId);

    /**
     * Finds all team settings where the team is hidden for a given workspace.
     *
     * @param workspaceId the workspace ID
     * @return list of team settings for hidden teams
     */
    @Query(
        """
        SELECT wts FROM WorkspaceTeamSettings wts
        WHERE wts.workspace.id = :workspaceId AND wts.hidden = true
        """
    )
    List<WorkspaceTeamSettings> findHiddenTeamsByWorkspace(@Param("workspaceId") Long workspaceId);

    /**
     * Finds the IDs of all hidden teams for a given workspace.
     *
     * <p>This is a more efficient query when only team IDs are needed,
     * for example when filtering teams in other queries.
     *
     * @param workspaceId the workspace ID
     * @return set of team IDs that are hidden in the workspace
     */
    @Query(
        """
        SELECT wts.team.id FROM WorkspaceTeamSettings wts
        WHERE wts.workspace.id = :workspaceId AND wts.hidden = true
        """
    )
    Set<Long> findHiddenTeamIdsByWorkspace(@Param("workspaceId") Long workspaceId);

    /**
     * Deletes all team settings for a workspace.
     * Used during workspace purge to clean up settings data.
     *
     * @param workspaceId the workspace ID
     */
    @Modifying
    @Query("DELETE FROM WorkspaceTeamSettings wts WHERE wts.workspace.id = :workspaceId")
    void deleteAllByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
