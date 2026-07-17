package de.tum.cit.aet.hephaestus.workspace;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
@WorkspaceAgnostic("Workspace is the tenant root - queries manage workspaces themselves")
public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
    /**
     * Pessimistic lock, for a read whose value is about to be snapshotted and mutated (the audited
     * AI-settings writes). Without it the before-snapshot and the write are not serialized: two
     * concurrent admin PATCHes both read the same prior state, Hibernate's full-column UPDATE makes the
     * later one silently revert the earlier's field, and the audit trail ends up asserting a transition
     * that never survived — with no row for the write that undid it.
     */
    @WorkspaceAgnostic("Locking read of the tenant root itself, by its own id")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT w FROM Workspace w WHERE w.id = :id")
    Optional<Workspace> findByIdForUpdate(@Param("id") Long id);

    /**
     * Reverse-lookup a workspace by its GitHub App installation id. Joins through the
     * {@code Connection} row whose {@code kind='GITHUB'} and {@code instance_key=installationId}.
     * Cross-workspace collision is structurally impossible at this layer because the
     * inline-create-from-installation path in {@code GithubLifecycleListener} only writes a
     * {@code Connection} row for the workspace it is creating, so the join is at-most-one.
     */
    default Optional<Workspace> findByInstallationId(Long installationId) {
        if (installationId == null) {
            return Optional.empty();
        }
        return findByGitHubInstallationInstanceKey(installationId.toString());
    }

    @Query(
        """
        SELECT c.workspace
        FROM Connection c
        WHERE c.kind = de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind.GITHUB
          AND c.instanceKey = :instanceKey
        """
    )
    Optional<Workspace> findByGitHubInstallationInstanceKey(@Param("instanceKey") String instanceKey);

    Optional<Workspace> findByRepositoriesToMonitor_NameWithOwner(String nameWithOwner);
    Optional<Workspace> findByOrganization_Login(String login);
    Optional<Workspace> findByAccountLoginIgnoreCase(String login);
    List<Workspace> findAllByAccountLoginIgnoreCase(String login);
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
     * <p>The join is on {@code nameWithOwner} (no FK from {@code RepositoryToMonitor} to {@code
     * Repository}), and {@code repository_to_monitor.name_with_owner} has no unique constraint, so two
     * monitors that share a {@code nameWithOwner} would yield more than one row. Rather than assert a
     * cardinality the schema does not enforce — which would surface as an uncaught
     * {@code IncorrectResultSizeDataAccessException} inside the async invalidation listener — this picks
     * the lowest workspace id deterministically and caps the result to one row.
     *
     * @return the resolved workspace id, or {@code Optional.empty()} if the repository is not monitored
     *         anywhere
     */
    default Optional<Long> findWorkspaceIdByRepositoryId(Long repositoryId) {
        List<Long> ids = findWorkspaceIdsByRepositoryId(repositoryId, PageRequest.of(0, 1));
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }

    @Query(
        """
        SELECT m.workspace.id
        FROM RepositoryToMonitor m
        JOIN Repository r ON r.nameWithOwner = m.nameWithOwner
        WHERE r.id = :repositoryId
        ORDER BY m.workspace.id
        """
    )
    List<Long> findWorkspaceIdsByRepositoryId(@Param("repositoryId") Long repositoryId, Pageable pageable);
}
