package de.tum.in.www1.hephaestus.gitprovider.sync;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncTarget;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncType;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.WorkspaceSyncMetadata;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.GitHubIssueSyncService;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.github.GitHubIssueCommentSyncService;
import de.tum.in.www1.hephaestus.gitprovider.issuedependency.github.GitHubIssueDependencySyncService;
import de.tum.in.www1.hephaestus.gitprovider.label.github.GitHubLabelSyncService;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.GitHubMilestoneSyncService;
import de.tum.in.www1.hephaestus.gitprovider.organization.github.GitHubOrganizationSyncService;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.GitHubPullRequestSyncService;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github.GitHubPullRequestReviewCommentSyncService;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.collaborator.github.GitHubCollaboratorSyncService;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import de.tum.in.www1.hephaestus.gitprovider.subissue.github.GitHubSubIssueSyncService;
import de.tum.in.www1.hephaestus.gitprovider.team.github.GitHubTeamSyncService;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

/**
 * GraphQL-based data synchronization service.
 *
 * <h2>Purpose</h2>
 * Orchestrates synchronization of all GitHub data using typed GraphQL models.
 * Uses the {@link SyncTargetProvider} SPI to decouple from workspace entities.
 *
 * <h2>Thread Safety</h2>
 * This class is thread-safe. All methods can be called from multiple threads:
 * <ul>
 *   <li>{@code syncSyncTargetAsync()} submits work to the virtual thread executor</li>
 *   <li>{@code syncSyncTarget()} is safe for concurrent calls (each operates on independent data)</li>
 *   <li>{@code syncAllRepositories()} synchronizes on workspace level (one call per workspace)</li>
 * </ul>
 * Note: Underlying sync services must also be thread-safe.
 *
 * <h2>Configuration</h2>
 * <ul>
 *   <li>{@code monitoring.sync-cooldown-in-minutes} - Minimum interval between syncs for the same target</li>
 * </ul>
 *
 * @see SyncTargetProvider
 * @see GitHubDataSyncScheduler
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
    private final GitHubIssueCommentSyncService issueCommentSyncService;
    private final GitHubIssueDependencySyncService issueDependencySyncService;
    private final GitHubSubIssueSyncService subIssueSyncService;
    private final GitHubPullRequestSyncService pullRequestSyncService;
    private final GitHubPullRequestReviewCommentSyncService pullRequestReviewCommentSyncService;
    private final GitHubTeamSyncService teamSyncService;
    private final GitHubOrganizationSyncService organizationSyncService;
    private final GitHubRepositorySyncService repositorySyncService;
    private final GitHubCollaboratorSyncService collaboratorSyncService;

    private final AsyncTaskExecutor monitoringExecutor;

    public GitHubDataSyncService(
        @Value("${monitoring.sync-cooldown-in-minutes}") int syncCooldownInMinutes,
        SyncTargetProvider syncTargetProvider,
        RepositoryRepository repositoryRepository,
        GitHubLabelSyncService labelSyncService,
        GitHubMilestoneSyncService milestoneSyncService,
        GitHubIssueSyncService issueSyncService,
        GitHubIssueCommentSyncService issueCommentSyncService,
        GitHubIssueDependencySyncService issueDependencySyncService,
        GitHubSubIssueSyncService subIssueSyncService,
        GitHubPullRequestSyncService pullRequestSyncService,
        GitHubPullRequestReviewCommentSyncService pullRequestReviewCommentSyncService,
        GitHubTeamSyncService teamSyncService,
        GitHubOrganizationSyncService organizationSyncService,
        GitHubRepositorySyncService repositorySyncService,
        GitHubCollaboratorSyncService collaboratorSyncService,
        @Qualifier("monitoringExecutor") AsyncTaskExecutor monitoringExecutor
    ) {
        this.syncCooldownInMinutes = syncCooldownInMinutes;
        this.syncTargetProvider = syncTargetProvider;
        this.repositoryRepository = repositoryRepository;
        this.labelSyncService = labelSyncService;
        this.milestoneSyncService = milestoneSyncService;
        this.issueSyncService = issueSyncService;
        this.issueCommentSyncService = issueCommentSyncService;
        this.issueDependencySyncService = issueDependencySyncService;
        this.subIssueSyncService = subIssueSyncService;
        this.pullRequestSyncService = pullRequestSyncService;
        this.pullRequestReviewCommentSyncService = pullRequestReviewCommentSyncService;
        this.teamSyncService = teamSyncService;
        this.organizationSyncService = organizationSyncService;
        this.repositorySyncService = repositorySyncService;
        this.collaboratorSyncService = collaboratorSyncService;
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
        String safeNameWithOwner = sanitizeForLog(nameWithOwner);

        Repository repository = repositoryRepository.findByNameWithOwner(nameWithOwner).orElse(null);
        if (repository == null) {
            log.warn("Repository {} not found in database, skipping sync", safeNameWithOwner);
            return;
        }

        Long repositoryId = repository.getId();
        log.info("Starting GraphQL sync for repository {}", safeNameWithOwner);

        try {
            // Sync repository metadata first (ensures entity is up-to-date before syncing related data)
            var syncedRepository = repositorySyncService.syncRepository(workspaceId, nameWithOwner);
            if (syncedRepository.isPresent()) {
                log.debug("Synced repository metadata for {}", safeNameWithOwner);
            } else {
                log.warn(
                    "Failed to sync repository metadata for {}, continuing with other sync operations",
                    safeNameWithOwner
                );
            }

            // Sync collaborators
            int collaboratorsCount = syncCollaboratorsIfNeeded(syncTarget, workspaceId, repositoryId);

            // Sync labels
            int labelsCount = labelSyncService.syncLabelsForRepository(workspaceId, repositoryId);

            // Sync milestones
            int milestonesCount = milestoneSyncService.syncMilestonesForRepository(workspaceId, repositoryId);

            // Sync issues
            int issuesCount = issueSyncService.syncForRepository(workspaceId, repositoryId);

            // Sync issue comments (requires issues to exist)
            int issueCommentsCount = issueCommentSyncService.syncForRepository(workspaceId, repositoryId);

            // Sync pull requests
            int prsCount = pullRequestSyncService.syncForRepository(workspaceId, repositoryId);

            // Sync PR review comments/threads (requires PRs to exist)
            int prReviewCommentsCount = pullRequestReviewCommentSyncService.syncCommentsForRepository(
                workspaceId,
                repositoryId
            );

            // Update sync timestamp via SPI
            syncTargetProvider.updateSyncTimestamp(
                workspaceId,
                nameWithOwner,
                SyncType.ISSUES_AND_PULL_REQUESTS,
                Instant.now()
            );

            log.info(
                "Completed GraphQL sync for {}: repo metadata {}, {} collaborators, {} labels, {} milestones, {} issues, {} issue comments, {} PRs, {} PR review comments",
                safeNameWithOwner,
                syncedRepository.isPresent() ? "synced" : "failed",
                collaboratorsCount >= 0 ? collaboratorsCount : "skipped",
                labelsCount,
                milestonesCount,
                issuesCount,
                issueCommentsCount,
                prsCount,
                prReviewCommentsCount
            );
        } catch (Exception e) {
            log.error("Error syncing repository {} via GraphQL: {}", safeNameWithOwner, e.getMessage(), e);
        }
    }

    /**
     * Syncs all repositories in a workspace using GraphQL.
     * <p>
     * This method orchestrates:
     * <ol>
     *   <li>Organization sync (if organization exists)</li>
     *   <li>Team sync (if organization exists)</li>
     *   <li>Per-repository syncs (labels, milestones, issues, PRs, comments)</li>
     *   <li>Workspace-level issue dependencies sync</li>
     *   <li>Workspace-level sub-issues sync</li>
     * </ol>
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

        // Sync organization and teams first (if applicable)
        syncOrganizationAndTeams(workspaceId);

        // Sync each repository
        for (SyncTarget target : syncTargets) {
            if (shouldSync(target)) {
                syncSyncTarget(target);
            }
        }

        // Sync workspace-level relationships (after all issues/PRs are synced)
        syncWorkspaceLevelRelationships(workspaceId);
    }

    /**
     * Syncs organization and teams for a workspace.
     * <p>
     * Organization sync includes memberships.
     * Team sync includes team memberships.
     *
     * @param workspaceId the workspace ID
     */
    private void syncOrganizationAndTeams(Long workspaceId) {
        Optional<WorkspaceSyncMetadata> metadataOpt = syncTargetProvider.getWorkspaceSyncMetadata(workspaceId);
        if (metadataOpt.isEmpty()) {
            log.debug("No workspace metadata found for workspace {}, skipping org/team sync", workspaceId);
            return;
        }

        WorkspaceSyncMetadata metadata = metadataOpt.get();
        String organizationLogin = metadata.organizationLogin();

        if (organizationLogin == null || organizationLogin.isBlank()) {
            log.debug("No organization login for workspace {}, skipping org/team sync", workspaceId);
            return;
        }

        try {
            // Sync organization and memberships
            var organization = organizationSyncService.syncOrganization(workspaceId, organizationLogin);
            if (organization != null) {
                log.debug("Synced organization {} for workspace {}", organizationLogin, workspaceId);
            }

            // Sync teams and team memberships
            int teamsCount = teamSyncService.syncTeamsForOrganization(workspaceId, organizationLogin);
            log.debug(
                "Synced {} teams for organization {} in workspace {}",
                teamsCount,
                organizationLogin,
                workspaceId
            );
        } catch (Exception e) {
            log.error("Error syncing organization/teams for workspace {}: {}", workspaceId, e.getMessage(), e);
        }
    }

    /**
     * Syncs workspace-level relationships that require issues and PRs to exist first.
     * <p>
     * This includes:
     * <ul>
     *   <li>Issue dependencies (blocked_by relationships)</li>
     *   <li>Sub-issues (parent-child relationships)</li>
     * </ul>
     * <p>
     * These sync operations have their own cooldown mechanisms.
     *
     * @param workspaceId the workspace ID
     */
    private void syncWorkspaceLevelRelationships(Long workspaceId) {
        try {
            // Sync issue dependencies (has internal cooldown check)
            int dependenciesCount = issueDependencySyncService.syncDependenciesForWorkspace(workspaceId);
            if (dependenciesCount >= 0) {
                log.debug("Synced {} issue dependencies for workspace {}", dependenciesCount, workspaceId);
            } else {
                log.debug("Issue dependencies sync skipped due to cooldown for workspace {}", workspaceId);
            }
        } catch (Exception e) {
            log.error("Error syncing issue dependencies for workspace {}: {}", workspaceId, e.getMessage(), e);
        }

        try {
            // Sync sub-issues (has internal cooldown check)
            int subIssuesCount = subIssueSyncService.syncSubIssuesForWorkspace(workspaceId);
            if (subIssuesCount >= 0) {
                log.debug("Synced {} sub-issue relationships for workspace {}", subIssuesCount, workspaceId);
            } else {
                log.debug("Sub-issues sync skipped due to cooldown for workspace {}", workspaceId);
            }
        } catch (Exception e) {
            log.error("Error syncing sub-issues for workspace {}: {}", workspaceId, e.getMessage(), e);
        }
    }

    /**
     * Syncs collaborators for a repository if the cooldown has expired.
     *
     * @param syncTarget   the sync target containing cooldown timestamps
     * @param workspaceId  the workspace ID
     * @param repositoryId the repository ID
     * @return number of collaborators synced, or -1 if skipped due to cooldown
     */
    private int syncCollaboratorsIfNeeded(SyncTarget syncTarget, Long workspaceId, Long repositoryId) {
        Instant cooldownThreshold = Instant.now().minusSeconds(syncCooldownInMinutes * 60L);
        boolean shouldSync =
            syncTarget.lastCollaboratorsSyncedAt() == null ||
            syncTarget.lastCollaboratorsSyncedAt().isBefore(cooldownThreshold);

        if (!shouldSync) {
            log.debug(
                "Skipping collaborator sync for repository {} (cooldown active, last synced at {})",
                repositoryId,
                syncTarget.lastCollaboratorsSyncedAt()
            );
            return -1;
        }

        int count = collaboratorSyncService.syncCollaboratorsForRepository(workspaceId, repositoryId);

        // Update sync timestamp
        syncTargetProvider.updateSyncTimestamp(
            workspaceId,
            syncTarget.repositoryNameWithOwner(),
            SyncType.COLLABORATORS,
            Instant.now()
        );

        return count;
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
