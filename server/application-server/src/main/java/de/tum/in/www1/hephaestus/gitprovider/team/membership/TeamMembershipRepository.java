package de.tum.in.www1.hephaestus.gitprovider.team.membership;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for team membership records.
 *
 * <p>Workspace-agnostic: Memberships are scoped through their team which has
 * organization context containing {@code workspaceId}. All queries filter by
 * team ID which inherently carries workspace scope.
 */
@Repository
@WorkspaceAgnostic("Scoped through team.organization which contains workspaceId")
public interface TeamMembershipRepository extends JpaRepository<TeamMembership, TeamMembership.Id> {
    /**
     * Find a membership by team and user IDs.
     *
     * @param teamId the team ID
     * @param userId the user ID
     * @return the membership, if it exists
     */
    Optional<TeamMembership> findByTeam_IdAndUser_Id(Long teamId, Long userId);

    /**
     * Delete a membership by team and user IDs.
     *
     * @param teamId the team ID
     * @param userId the user ID
     */
    void deleteByTeam_IdAndUser_Id(Long teamId, Long userId);

    /**
     * Check if a membership exists by team and user IDs.
     *
     * @param teamId the team ID
     * @param userId the user ID
     * @return true if the membership exists
     */
    boolean existsByTeam_IdAndUser_Id(Long teamId, Long userId);
}
