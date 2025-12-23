package de.tum.in.www1.hephaestus.gitprovider.sync;

import de.tum.in.www1.hephaestus.gitprovider.issue.github.GitHubIssueGraphQlSyncService;
import de.tum.in.www1.hephaestus.gitprovider.label.github.GitHubLabelSyncService;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.GitHubMilestoneSyncService;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.GitHubPullRequestGraphQlSyncService;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitor;
import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

/**
 * GraphQL-based data synchronization service.
 * <p>
 * This service provides GraphQL-based synchronization for all GitHub data.
 * It uses GraphQL API exclusively for all GitHub data synchronization.
 */
@Service
public class GitHubGraphQlDataSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubGraphQlDataSyncService.class);

    // Configuration
    private final int syncCooldownInMinutes;

    // Repositories
    private final WorkspaceRepository workspaceRepository;
    private final RepositoryToMonitorRepository repositoryToMonitorRepository;
    private final RepositoryRepository repositoryRepository;

    // GraphQL Sync services
    private final GitHubLabelSyncService labelSyncService;
    private final GitHubMilestoneSyncService milestoneSyncService;
    private final GitHubIssueGraphQlSyncService issueSyncService;
    private final GitHubPullRequestGraphQlSyncService pullRequestSyncService;

    // Async executor
    private final AsyncTaskExecutor monitoringExecutor;

    public GitHubGraphQlDataSyncService(
        @Value("${monitoring.sync-cooldown-in-minutes}") int syncCooldownInMinutes,
        WorkspaceRepository workspaceRepository,
        RepositoryToMonitorRepository repositoryToMonitorRepository,
        RepositoryRepository repositoryRepository,
        GitHubLabelSyncService labelSyncService,
        GitHubMilestoneSyncService milestoneSyncService,
        GitHubIssueGraphQlSyncService issueSyncService,
        GitHubPullRequestGraphQlSyncService pullRequestSyncService,
        @Qualifier("monitoringExecutor") AsyncTaskExecutor monitoringExecutor
    ) {
        this.syncCooldownInMinutes = syncCooldownInMinutes;
        this.workspaceRepository = workspaceRepository;
        this.repositoryToMonitorRepository = repositoryToMonitorRepository;
        this.repositoryRepository = repositoryRepository;
        this.labelSyncService = labelSyncService;
        this.milestoneSyncService = milestoneSyncService;
        this.issueSyncService = issueSyncService;
        this.pullRequestSyncService = pullRequestSyncService;
        this.monitoringExecutor = monitoringExecutor;
    }

    /**
     * Syncs a repository asynchronously using GraphQL.
     */
    public void syncRepositoryAsync(RepositoryToMonitor repositoryToMonitor) {
        monitoringExecutor.submit(() -> syncRepository(repositoryToMonitor));
    }

    /**
     * Syncs a repository using GraphQL API.
     *
     * @param repositoryToMonitor the repository to sync
     */
    public void syncRepository(RepositoryToMonitor repositoryToMonitor) {
        Long workspaceId = repositoryToMonitor.getWorkspace().getId();
        String nameWithOwner = repositoryToMonitor.getNameWithOwner();

        Repository repository = repositoryRepository.findByNameWithOwner(nameWithOwner).orElse(null);
        if (repository == null) {
            logger.warn("Repository {} not found in database, skipping sync", nameWithOwner);
            return;
        }

        Long repositoryId = repository.getId();
        logger.info("Starting GraphQL sync for repository {}", nameWithOwner);

        try {
            // Sync labels
            int labelsCount = labelSyncService.syncLabelsForRepository(workspaceId, repositoryId);
            logger.debug("Synced {} labels for {}", labelsCount, nameWithOwner);

            // Sync milestones
            int milestonesCount = milestoneSyncService.syncMilestonesForRepository(workspaceId, repositoryId);
            logger.debug("Synced {} milestones for {}", milestonesCount, nameWithOwner);

            // Sync issues
            int issuesCount = issueSyncService.syncIssuesForRepository(workspaceId, repositoryId);
            logger.debug("Synced {} issues for {}", issuesCount, nameWithOwner);

            // Sync pull requests
            int prsCount = pullRequestSyncService.syncPullRequestsForRepository(workspaceId, repositoryId);
            logger.debug("Synced {} pull requests for {}", prsCount, nameWithOwner);

            // Update sync timestamp
            repositoryToMonitor.setIssuesAndPullRequestsSyncedAt(Instant.now());
            repositoryToMonitorRepository.save(repositoryToMonitor);

            logger.info(
                "Completed GraphQL sync for {}: {} labels, {} milestones, {} issues, {} PRs",
                nameWithOwner,
                labelsCount,
                milestonesCount,
                issuesCount,
                prsCount
            );
        } catch (Exception e) {
            logger.error("Error syncing repository {} via GraphQL: {}", nameWithOwner, e.getMessage(), e);
        }
    }

    /**
     * Syncs all repositories in a workspace using GraphQL.
     */
    public void syncAllRepositories(Workspace workspace) {
        workspace = workspaceRepository.findById(workspace.getId()).orElse(null);
        if (workspace == null) {
            logger.warn("Workspace no longer exists; skipping sync.");
            return;
        }

        var repositoriesToMonitor = repositoryToMonitorRepository.findByWorkspaceId(workspace.getId());
        logger.info(
            "Syncing {} repositories for workspace {}",
            repositoriesToMonitor.size(),
            workspace.getWorkspaceSlug()
        );

        for (RepositoryToMonitor repo : repositoriesToMonitor) {
            if (shouldSync(repo)) {
                syncRepository(repo);
            }
        }
    }

    private boolean shouldSync(RepositoryToMonitor repo) {
        if (repo.getIssuesAndPullRequestsSyncedAt() == null) {
            return true;
        }
        return repo
            .getIssuesAndPullRequestsSyncedAt()
            .isBefore(Instant.now().minusSeconds(syncCooldownInMinutes * 60L));
    }
}
