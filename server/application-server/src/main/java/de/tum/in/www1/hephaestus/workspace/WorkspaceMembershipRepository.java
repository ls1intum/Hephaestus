package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkspaceMembershipRepository extends JpaRepository<WorkspaceMembership, WorkspaceMembership.Id> {
    List<WorkspaceMembership> findByWorkspace_Id(Long workspaceId);
    Optional<WorkspaceMembership> findByWorkspace_IdAndUser_Id(Long workspaceId, Long userId);
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
}
