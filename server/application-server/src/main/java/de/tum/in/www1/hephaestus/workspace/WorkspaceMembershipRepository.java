package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for {@link WorkspaceMembership} entities.
 * Manages the relationship between users and workspaces, including role
 * assignments.
 */
@WorkspaceAgnostic("Queried by explicit workspace ID - membership queries always workspace-scoped")
public interface WorkspaceMembershipRepository extends JpaRepository<WorkspaceMembership, WorkspaceMembership.Id> {
    List<WorkspaceMembership> findByWorkspace_Id(Long workspaceId);

    @Query(
        """
            SELECT wm
            FROM WorkspaceMembership wm
            JOIN FETCH wm.user
            WHERE wm.workspace.id = :workspaceId
        """
    )
    List<WorkspaceMembership> findAllWithUserByWorkspaceId(@Param("workspaceId") Long workspaceId);

    @Query(
        """
            SELECT wm FROM WorkspaceMembership wm
            JOIN FETCH wm.user
            WHERE wm.workspace.id = :workspaceId
        """
    )
    Page<WorkspaceMembership> findAllByWorkspace_Id(@Param("workspaceId") Long workspaceId, Pageable pageable);

    @Query(
        """
            SELECT wm FROM WorkspaceMembership wm
            JOIN FETCH wm.user
            WHERE wm.workspace.id = :workspaceId AND wm.user.id = :userId
        """
    )
    Optional<WorkspaceMembership> findByWorkspace_IdAndUser_Id(
        @Param("workspaceId") Long workspaceId,
        @Param("userId") Long userId
    );

    List<WorkspaceMembership> findAllByWorkspace_IdAndUser_IdIn(Long workspaceId, Collection<Long> userIds);

    @Query(
        """
            SELECT DISTINCT u
            FROM WorkspaceMembership wm
            JOIN wm.user u
            LEFT JOIN FETCH u.teamMemberships tm
            LEFT JOIN FETCH tm.team t
            WHERE wm.workspace.id = :workspaceId
            AND u.type = 'USER'
        """
    )
    List<User> findHumanUsersWithTeamsByWorkspaceId(@Param("workspaceId") Long workspaceId);

    List<WorkspaceMembership> findByUser_Id(Long userId);

    long countByWorkspace_IdAndRole(Long workspaceId, WorkspaceRole role);

    /**
     * Atomically inserts a membership if absent (race-condition safe).
     */
    @Modifying
    @Transactional
    @Query(
        value = """
        INSERT INTO workspace_membership (workspace_id, user_id, role, league_points, created_at)
        VALUES (:workspaceId, :userId, :role, :leaguePoints, CURRENT_TIMESTAMP)
        ON CONFLICT (workspace_id, user_id) DO NOTHING
        """,
        nativeQuery = true
    )
    int insertIfAbsent(
        @Param("workspaceId") Long workspaceId,
        @Param("userId") Long userId,
        @Param("role") String role,
        @Param("leaguePoints") int leaguePoints
    );

    /**
     * Deletes all memberships for a workspace.
     * Used during workspace purge to clean up membership data.
     *
     * @param workspaceId the workspace ID
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM WorkspaceMembership wm WHERE wm.workspace.id = :workspaceId")
    void deleteAllByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
