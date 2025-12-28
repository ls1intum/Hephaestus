package de.tum.in.www1.hephaestus.gitprovider.sync;

import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncTarget;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncType;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.GitHubIssueSyncService;
import de.tum.in.www1.hephaestus.gitprovider.label.github.GitHubLabelSyncService;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.GitHubMilestoneSyncService;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.GitHubPullRequestSyncService;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
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
 * Orchestrates synchronization of all GitHub data using typed GraphQL models.
 * Uses the {@link SyncTargetProvider} SPI to decouple from workspace entities.
 */
@Service
public class GitHubDataSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitHubDataSyncService.class);

    private final int syncCooldownInMinutes;

    private final SyncTargetProvider syncTargetProvider;
    private final RepositoryRepository repositoryRepository;

    private final GitHubLabelSyncService labelSyncService;
    private final GitHubMilestoneSyncService milestoneSyncService;
    private final GitHubIssueSyncService issueSyncService;
    private final GitHubPullRequestSyncService pullRequestSyncService;

    private final AsyncTaskExecutor monitoringExecutor;

    public GitHubDataSyncService(
        @Value("${monitoring.sync-cooldown-in-minutes}") int syncCooldownInMinutes,
        SyncTargetProvider syncTargetProvider,
        RepositoryRepository repositoryRepository,
        GitHubLabelSyncService labelSyncService,
        GitHubMilestoneSyncService milestoneSyncService,
        GitHubIssueSyncService issueSyncService,
        GitHubPullRequestSyncService pullRequestSyncService,
        @Qualifier("monitoringExecutor") AsyncTaskExecutor monitoringExecutor
    ) {
        this.syncCooldownInMinutes = syncCooldownInMinutes;
        this.syncTargetProvider = syncTargetProvider;
        this.repositoryRepository = repositoryRepository;
        this.labelSyncService = labelSyncService;
        this.milestoneSyncService = milestoneSyncService;
        this.issueSyncService = issueSyncService;
        this.pullRequestSyncService = pullRequestSyncService;
        this.monitoringExecutor = monitoringExecutor;
    }

    /**
     * Syncs a sync target asynchronously using GraphQL.
     *
     * @param syncTarget the sync target to sync
     */
    public void syncSyncTargetAsync(SyncTarget syncTarget) {
        monitoringExecutor.submit(() -> syncSyncTarget(syncTarget));
    }

    /**
     * Syncs a sync target using GraphQL API.
     *
     * @param syncTarget the sync target to sync
     */
    public void syncSyncTarget(SyncTarget syncTarget) {
        Long workspaceId = syncTarget.workspaceId();
        String nameWithOwner = syncTarget.repositoryNameWithOwner();

        Repository repository = repositoryRepository.findByNameWithOwner(nameWithOwner).orElse(null);
        if (repository == null) {
            log.warn("Repository {} not found in database, skipping sync", nameWithOwner);
            return;
        }

        Long repositoryId = repository.getId();
        log.info("Starting GraphQL sync for repository {}", nameWithOwner);

        try {
            // Sync labels
            int labelsCount = labelSyncService.syncLabelsForRepository(workspaceId, repositoryId);
            log.debug("Synced {} labels for {}", labelsCount, nameWithOwner);

            // Sync milestones
            int milestonesCount = milestoneSyncService.syncMilestonesForRepository(workspaceId, repositoryId);
            log.debug("Synced {} milestones for {}", milestonesCount, nameWithOwner);

            // Sync issues
            int issuesCount = issueSyncService.syncForRepository(workspaceId, repositoryId);
            log.debug("Synced {} issues for {}", issuesCount, nameWithOwner);

            // Sync pull requests
            int prsCount = pullRequestSyncService.syncForRepository(workspaceId, repositoryId);
            log.debug("Synced {} pull requests for {}", prsCount, nameWithOwner);

            // Update sync timestamp via SPI
            syncTargetProvider.updateSyncTimestamp(
                workspaceId,
                nameWithOwner,
                SyncType.ISSUES_AND_PULL_REQUESTS,
                Instant.now()
            );

            log.info(
                "Completed GraphQL sync for {}: {} labels, {} milestones, {} issues, {} PRs",
                nameWithOwner,
                labelsCount,
                milestonesCount,
                issuesCount,
                prsCount
            );
        } catch (Exception e) {
            log.error("Error syncing repository {} via GraphQL: {}", nameWithOwner, e.getMessage(), e);
        }
    }

    /**
     * Syncs all repositories in a workspace using GraphQL.
     *
     * @param workspaceId the workspace ID
     */
    public void syncAllRepositories(Long workspaceId) {
        var syncTargets = syncTargetProvider.getSyncTargetsForWorkspace(workspaceId);
        if (syncTargets.isEmpty()) {
            log.warn("No sync targets found for workspace {}; skipping sync.", workspaceId);
            return;
        }

        log.info("Syncing {} repositories for workspace {}", syncTargets.size(), workspaceId);

        for (SyncTarget target : syncTargets) {
            if (shouldSync(target)) {
                syncSyncTarget(target);
            }
        }
    }

    private boolean shouldSync(SyncTarget target) {
        if (target.lastIssuesAndPullRequestsSyncedAt() == null) {
            return true;
        }
        return target
            .lastIssuesAndPullRequestsSyncedAt()
            .isBefore(Instant.now().minusSeconds(syncCooldownInMinutes * 60L));
    }
}
