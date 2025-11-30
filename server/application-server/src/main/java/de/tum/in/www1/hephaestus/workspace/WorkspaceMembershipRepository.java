package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for {@link WorkspaceMembership} entities.
 * Manages the relationship between users and workspaces, including role assignments.
 */
public interface WorkspaceMembershipRepository extends JpaRepository<WorkspaceMembership, WorkspaceMembership.Id> {
    List<WorkspaceMembership> findByWorkspace_Id(Long workspaceId);

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

    long countByWorkspace_IdAndRole(Long workspaceId, WorkspaceRole role);
}
