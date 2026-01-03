package de.tum.in.www1.hephaestus.gitprovider.team.permission;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for team repository permission records.
 *
 * <p>Workspace-agnostic: Permissions are scoped through their team which has
 * organization context containing {@code workspaceId}. All queries filter by
 * team and repository IDs which inherently carry workspace scope.
 */
@Repository
@WorkspaceAgnostic("Scoped through team.organization which contains workspaceId")
public interface TeamRepositoryPermissionRepository
    extends JpaRepository<TeamRepositoryPermission, TeamRepositoryPermission.Id> {
    Optional<TeamRepositoryPermission> findByTeam_IdAndRepository_Id(Long teamId, Long repositoryId);

    /**
     * Find permission by team and repository IDs with the team eagerly loaded.
     * This is needed for authorization checks that access the team's organization.
     */
    @EntityGraph(attributePaths = { "team" })
    Optional<TeamRepositoryPermission> findWithTeamByTeam_IdAndRepository_Id(Long teamId, Long repositoryId);
}
