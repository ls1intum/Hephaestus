package de.tum.cit.aet.hephaestus.gitprovider.team.permission;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for team repository permission records.
 *
 * <p>Permissions are scoped through their team which carries scope through
 * the Team.organization relationship.
 */
@Repository
@WorkspaceAgnostic("Permissions scoped through team_id -> team.workspace_id")
public interface TeamRepositoryPermissionRepository
    extends JpaRepository<TeamRepositoryPermission, TeamRepositoryPermission.Id>
{
    Optional<TeamRepositoryPermission> findByTeam_IdAndRepository_Id(Long teamId, Long repositoryId);

    /**
     * Find permission by team and repository IDs with the team eagerly loaded.
     * This is needed for authorization checks that access the team's organization.
     */
    @EntityGraph(attributePaths = { "team" })
    Optional<TeamRepositoryPermission> findWithTeamByTeam_IdAndRepository_Id(Long teamId, Long repositoryId);
}
