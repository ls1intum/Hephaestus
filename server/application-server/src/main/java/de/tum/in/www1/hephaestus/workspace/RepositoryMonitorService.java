package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.workspace.exception.RepositoryAlreadyMonitoredException;
import de.tum.in.www1.hephaestus.workspace.exception.RepositoryManagementNotAllowedException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing repositories to monitor within a workspace.
 *
 * <p>Handles:
 * <ul>
 *   <li>Adding/removing repositories to monitor</li>
 *   <li>Listing monitored repositories</li>
 *   <li>Repository validation</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class RepositoryMonitorService {

    private final WorkspaceRepository workspaceRepository;
    private final RepositoryToMonitorRepository repositoryToMonitorRepository;
    private final RepositoryRepository repositoryRepository;

    /**
     * Get list of repositories being monitored for a workspace.
     *
     * @param workspaceId the workspace ID
     * @return list of repository nameWithOwner strings
     */
    @Transactional(readOnly = true)
    public List<String> getMonitoredRepositories(Long workspaceId) {
        return workspaceRepository
            .findById(workspaceId)
            .map(ws -> ws.getRepositoriesToMonitor().stream().map(RepositoryToMonitor::getNameWithOwner).toList())
            .orElse(List.of());
    }

    /**
     * Check if a repository is being monitored.
     *
     * @param workspace the workspace
     * @param nameWithOwner the repository identifier (owner/name)
     * @return true if monitored
     */
    public boolean isMonitored(Workspace workspace, String nameWithOwner) {
        return workspace
            .getRepositoriesToMonitor()
            .stream()
            .anyMatch(r -> r.getNameWithOwner().equals(nameWithOwner));
    }

    /**
     * Find a monitored repository by name.
     *
     * @param workspace the workspace
     * @param nameWithOwner the repository identifier
     * @return the RepositoryToMonitor if found
     */
    public Optional<RepositoryToMonitor> findByNameWithOwner(Workspace workspace, String nameWithOwner) {
        return workspace
            .getRepositoriesToMonitor()
            .stream()
            .filter(r -> r.getNameWithOwner().equals(nameWithOwner))
            .findFirst();
    }

    /**
     * Validate that repository management is allowed for this workspace.
     *
     * @param workspace the workspace
     * @throws RepositoryManagementNotAllowedException if management is blocked
     */
    public void validateManagementAllowed(Workspace workspace) {
        if (Workspace.GitProviderMode.GITHUB_APP_INSTALLATION.equals(workspace.getGitProviderMode())) {
            throw new RepositoryManagementNotAllowedException(workspace.getWorkspaceSlug());
        }
    }

    /**
     * Create a new repository monitor entry.
     *
     * @param workspace the workspace
     * @param nameWithOwner the repository identifier
     * @return the created monitor entry
     */
    @Transactional
    public RepositoryToMonitor create(Workspace workspace, String nameWithOwner) {
        if (isMonitored(workspace, nameWithOwner)) {
            throw new RepositoryAlreadyMonitoredException(nameWithOwner);
        }

        RepositoryToMonitor monitor = new RepositoryToMonitor();
        monitor.setNameWithOwner(nameWithOwner);
        monitor.setWorkspace(workspace);
        return repositoryToMonitorRepository.save(monitor);
    }

    /**
     * Remove a repository monitor and optionally clean up orphaned repository data.
     *
     * <p>If cleanupRepository is true, the Repository entity will only be deleted
     * if no other workspace is monitoring it (i.e., it's orphaned).
     *
     * @param workspace the workspace
     * @param nameWithOwner the repository identifier
     * @param cleanupRepository whether to delete the repository if orphaned
     * @throws EntityNotFoundException if the monitor doesn't exist
     */
    @Transactional
    public void remove(Workspace workspace, String nameWithOwner, boolean cleanupRepository) {
        RepositoryToMonitor monitor = findByNameWithOwner(workspace, nameWithOwner).orElseThrow(() ->
            new EntityNotFoundException("Repository", nameWithOwner)
        );

        repositoryToMonitorRepository.delete(monitor);
        workspace.getRepositoriesToMonitor().remove(monitor);

        if (cleanupRepository) {
            deleteRepositoryIfOrphaned(nameWithOwner);
        }
    }

    /**
     * Deletes a repository only if no workspace is monitoring it.
     * This preserves repositories that are shared across multiple workspaces.
     *
     * @param nameWithOwner the repository full name (e.g., "owner/repo")
     */
    private void deleteRepositoryIfOrphaned(String nameWithOwner) {
        if (nameWithOwner == null || nameWithOwner.isBlank()) {
            return;
        }

        // Check if any workspace is still monitoring this repository
        // Note: We already deleted this workspace's monitor, so count should be from other workspaces
        var monitorOpt = repositoryToMonitorRepository.findByNameWithOwner(nameWithOwner);
        if (monitorOpt.isPresent()) {
            // Another workspace is monitoring this repository, don't delete
            return;
        }

        // No workspace is monitoring this repository, safe to delete
        repositoryRepository.findByNameWithOwner(nameWithOwner).ifPresent(repositoryRepository::delete);
    }

    /**
     * Remove a repository monitor entry.
     *
     * @param monitor the monitor to remove
     */
    @Transactional
    public void remove(RepositoryToMonitor monitor) {
        repositoryToMonitorRepository.delete(monitor);
    }
}
