package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link RepositoryToMonitor} entities.
 * Tracks which GitHub repositories are monitored by each workspace.
 *
 * <p>Workspace-agnostic: This is a configuration table that maps repositories
 * to workspaces. Queries filter by workspace ID or nameWithOwner which is used
 * to resolve workspace context during sync operations.
 */
@Repository
@WorkspaceAgnostic("Configuration table mapping repositories to workspaces")
public interface RepositoryToMonitorRepository extends JpaRepository<RepositoryToMonitor, Long> {
    boolean existsByWorkspaceIdAndNameWithOwner(Long workspaceId, String nameWithOwner);
    Optional<RepositoryToMonitor> findByWorkspaceIdAndNameWithOwner(Long workspaceId, String nameWithOwner);
    List<RepositoryToMonitor> findByWorkspaceId(Long workspaceId);

    /**
     * Finds a repository monitor by its full name (owner/name).
     * Used during sync operations to resolve which workspace a repository belongs to.
     */
    Optional<RepositoryToMonitor> findByNameWithOwner(String nameWithOwner);

    /**
     * Counts the number of workspaces monitoring a given repository.
     * Used to determine if a repository can be safely deleted when removing a monitor.
     */
    long countByNameWithOwner(String nameWithOwner);
}
