package de.tum.cit.aet.hephaestus.workspace;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link Workspace} entities.
 * Provides methods for finding workspaces by various identifiers.
 */
@Repository
@WorkspaceAgnostic("Workspace is the tenant root - queries manage workspaces themselves")
public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
    Optional<Workspace> findByInstallationId(Long installationId);
    Optional<Workspace> findByRepositoriesToMonitor_NameWithOwner(String nameWithOwner);
    Optional<Workspace> findByOrganization_Login(String login);
    Optional<Workspace> findByAccountLoginIgnoreCase(String login);
    Optional<Workspace> findByWorkspaceSlug(String workspaceSlug);
    boolean existsByWorkspaceSlug(String workspaceSlug);
    boolean existsByOrganizationId(Long organizationId);
    boolean existsByIdAndOrganizationId(Long id, Long organizationId);

    List<Workspace> findByStatusNot(Workspace.WorkspaceStatus status);

    List<Workspace> findByStatusNotAndIsPubliclyViewableTrue(Workspace.WorkspaceStatus status);

    List<Workspace> findByStatus(Workspace.WorkspaceStatus status);

    List<Workspace> findByStatusAndIsPubliclyViewableTrue(Workspace.WorkspaceStatus status);

    /**
     * Resolves the workspace id from a repository id via the {@code RepositoryToMonitor} join.
     * Used by mentor-cache invalidation: domain events carry {@code repositoryId} but mentor
     * caches key on {@code workspaceId}.
     *
     * @return at most one workspace per repository (a monitored repo lives in exactly one
     *         workspace; {@code Optional.empty()} if the repository is not monitored anywhere)
     */
    @Query(
        """
        SELECT m.workspace.id
        FROM RepositoryToMonitor m
        JOIN Repository r ON r.nameWithOwner = m.nameWithOwner
        WHERE r.id = :repositoryId
        """
    )
    Optional<Long> findWorkspaceIdByRepositoryId(@Param("repositoryId") Long repositoryId);
}
