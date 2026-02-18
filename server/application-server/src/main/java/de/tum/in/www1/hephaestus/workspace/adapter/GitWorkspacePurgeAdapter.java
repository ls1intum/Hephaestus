package de.tum.in.www1.hephaestus.workspace.adapter;

import de.tum.in.www1.hephaestus.gitprovider.git.GitRepositoryManager;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitor;
import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.in.www1.hephaestus.workspace.spi.WorkspacePurgeContributor;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Cleans up local git repository clones when a workspace is purged.
 * <p>
 * For each repository monitored exclusively by the purged workspace (not shared
 * with other workspaces), the local filesystem clone is deleted. Shared
 * repositories retain their clones since other workspaces still need them.
 * <p>
 * Runs at order 200 — after activity events (100) but before repository monitors
 * are deleted in step 5 of the purge sequence. This ordering is critical because
 * the adapter queries {@link RepositoryToMonitorRepository} to find monitored
 * repos and check exclusivity, which requires the monitor entries to still exist.
 */
@Component
public class GitWorkspacePurgeAdapter implements WorkspacePurgeContributor {

    private static final Logger log = LoggerFactory.getLogger(GitWorkspacePurgeAdapter.class);

    private final GitRepositoryManager gitRepositoryManager;
    private final RepositoryToMonitorRepository repositoryToMonitorRepository;
    private final RepositoryRepository repositoryRepository;

    public GitWorkspacePurgeAdapter(
        GitRepositoryManager gitRepositoryManager,
        RepositoryToMonitorRepository repositoryToMonitorRepository,
        RepositoryRepository repositoryRepository
    ) {
        this.gitRepositoryManager = gitRepositoryManager;
        this.repositoryToMonitorRepository = repositoryToMonitorRepository;
        this.repositoryRepository = repositoryRepository;
    }

    @Override
    public void deleteWorkspaceData(Long workspaceId) {
        if (!gitRepositoryManager.isEnabled()) {
            return;
        }

        List<RepositoryToMonitor> monitors = repositoryToMonitorRepository.findByWorkspaceId(workspaceId);
        int deleted = 0;

        for (RepositoryToMonitor monitor : monitors) {
            String nameWithOwner = monitor.getNameWithOwner();

            // Only delete clone if this is the sole workspace monitoring this repo.
            // Shared repos keep their clones for the remaining workspaces.
            long monitorCount = repositoryToMonitorRepository.countByNameWithOwner(nameWithOwner);
            if (monitorCount > 1) {
                log.debug(
                    "Skipped git clone cleanup: reason=sharedRepository, repoName={}, monitorCount={}",
                    nameWithOwner,
                    monitorCount
                );
                continue;
            }

            repositoryRepository
                .findByNameWithOwner(nameWithOwner)
                .ifPresent(repository -> {
                    gitRepositoryManager.deleteClone(repository.getId());
                });
            deleted++;
        }

        if (deleted > 0) {
            log.info(
                "Cleaned up git clones during workspace purge: workspaceId={}, deletedCount={}, totalMonitors={}",
                workspaceId,
                deleted,
                monitors.size()
            );
        }
    }

    @Override
    public int getOrder() {
        // After activity events (100) but before monitor deletion (step 5).
        // Must run while RepositoryToMonitor entries still exist.
        return 200;
    }
}
