package de.tum.in.www1.hephaestus.gitprovider.sync;

import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubClientExecutor;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.GitHubIssueSyncService;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.github.GitHubIssueCommentSyncService;
import de.tum.in.www1.hephaestus.gitprovider.label.github.GitHubLabelSyncService;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.GitHubMilestoneSyncService;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.GitHubPullRequestSyncService;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github.GitHubPullRequestReviewSyncService;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github.GitHubPullRequestReviewCommentSyncService;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositoryCollaboratorSyncService;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.RepositorySyncException;
import de.tum.in.www1.hephaestus.gitprovider.team.github.GitHubTeamSyncService;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserSyncService;
import de.tum.in.www1.hephaestus.monitoring.MonitoringScopeFilter;
import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitor;
import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import de.tum.in.www1.hephaestus.workspace.WorkspaceService;
import java.io.IOException;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.IntStream;
import org.kohsuke.github.GHDirection;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueQueryBuilder;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Service for synchronizing GitHub data (repositories, issues, PRs, users, teams) for workspaces.
 * Handles both regular sync cycles and historical backfill operations.
 */
@Service
public class GitHubDataSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubDataSyncService.class);

    // Configuration
    private final int timeframe;
    private final int syncCooldownInMinutes;
    private final boolean backfillEnabled;
    private final int backfillBatchSize;
    private final int backfillRateLimitThreshold;
    private final int backfillCooldownMinutes;

    // Repositories
    private final WorkspaceRepository workspaceRepository;
    private final RepositoryToMonitorRepository repositoryToMonitorRepository;
    private final RepositoryRepository repositoryRepository;
    private final IssueRepository issueRepository;
    private final PullRequestRepository pullRequestRepository;

    // Sync services
    private final GitHubUserSyncService userSyncService;
    private final GitHubTeamSyncService teamSyncService;
    private final GitHubClientExecutor gitHubClientExecutor;
    private final GitHubRepositorySyncService repositorySyncService;
    private final GitHubRepositoryCollaboratorSyncService collaboratorSyncService;
    private final GitHubLabelSyncService labelSyncService;
    private final GitHubMilestoneSyncService milestoneSyncService;
    private final GitHubIssueSyncService issueSyncService;
    private final GitHubIssueCommentSyncService issueCommentSyncService;
    private final GitHubPullRequestSyncService pullRequestSyncService;
    private final GitHubPullRequestReviewSyncService pullRequestReviewSyncService;
    private final GitHubPullRequestReviewCommentSyncService pullRequestReviewCommentSyncService;

    // Other dependencies
    private final ObjectProvider<WorkspaceService> workspaceServiceProvider;
    private final AsyncTaskExecutor monitoringExecutor;
    private final MonitoringScopeFilter monitoringScopeFilter;

    public GitHubDataSyncService(
        @Value("${monitoring.timeframe}") int timeframe,
        @Value("${monitoring.sync-cooldown-in-minutes}") int syncCooldownInMinutes,
        @Value("${monitoring.backfill.enabled:false}") boolean backfillEnabled,
        @Value("${monitoring.backfill.batch-size:25}") int backfillBatchSize,
        @Value("${monitoring.backfill.rate-limit-threshold:500}") int backfillRateLimitThreshold,
        @Value("${monitoring.backfill.cooldown-minutes:5}") int backfillCooldownMinutes,
        WorkspaceRepository workspaceRepository,
        RepositoryToMonitorRepository repositoryToMonitorRepository,
        RepositoryRepository repositoryRepository,
        IssueRepository issueRepository,
        PullRequestRepository pullRequestRepository,
        GitHubUserSyncService userSyncService,
        GitHubTeamSyncService teamSyncService,
        GitHubClientExecutor gitHubClientExecutor,
        GitHubRepositorySyncService repositorySyncService,
        GitHubRepositoryCollaboratorSyncService collaboratorSyncService,
        GitHubLabelSyncService labelSyncService,
        GitHubMilestoneSyncService milestoneSyncService,
        GitHubIssueSyncService issueSyncService,
        GitHubIssueCommentSyncService issueCommentSyncService,
        GitHubPullRequestSyncService pullRequestSyncService,
        GitHubPullRequestReviewSyncService pullRequestReviewSyncService,
        GitHubPullRequestReviewCommentSyncService pullRequestReviewCommentSyncService,
        ObjectProvider<WorkspaceService> workspaceServiceProvider,
        @Qualifier("monitoringExecutor") AsyncTaskExecutor monitoringExecutor,
        MonitoringScopeFilter monitoringScopeFilter
    ) {
        this.timeframe = timeframe;
        this.syncCooldownInMinutes = syncCooldownInMinutes;
        this.backfillEnabled = backfillEnabled;
        this.backfillBatchSize = backfillBatchSize;
        this.backfillRateLimitThreshold = backfillRateLimitThreshold;
        this.backfillCooldownMinutes = backfillCooldownMinutes;
        this.workspaceRepository = workspaceRepository;
        this.repositoryToMonitorRepository = repositoryToMonitorRepository;
        this.repositoryRepository = repositoryRepository;
        this.issueRepository = issueRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.userSyncService = userSyncService;
        this.teamSyncService = teamSyncService;
        this.gitHubClientExecutor = gitHubClientExecutor;
        this.repositorySyncService = repositorySyncService;
        this.collaboratorSyncService = collaboratorSyncService;
        this.labelSyncService = labelSyncService;
        this.milestoneSyncService = milestoneSyncService;
        this.issueSyncService = issueSyncService;
        this.issueCommentSyncService = issueCommentSyncService;
        this.pullRequestSyncService = pullRequestSyncService;
        this.pullRequestReviewSyncService = pullRequestReviewSyncService;
        this.pullRequestReviewCommentSyncService = pullRequestReviewCommentSyncService;
        this.workspaceServiceProvider = workspaceServiceProvider;
        this.monitoringExecutor = monitoringExecutor;
        this.monitoringScopeFilter = monitoringScopeFilter;
    }

    /** Lazy accessor for WorkspaceService to break circular dependency. */
    private WorkspaceService getWorkspaceService() {
        return workspaceServiceProvider.getObject();
    }

    /**
     * Syncs all existing users in the database with their GitHub data
     */
    public void syncUsers(Workspace workspace) {
        // Reload workspace to get fresh state - repository monitors may have been deleted by other threads
        workspace = workspaceRepository.findById(workspace.getId()).orElse(null);
        if (workspace == null) {
            logger.warn("Workspace no longer exists; skipping user sync.");
            return;
        }

        boolean shouldSyncUsers =
            workspace.getUsersSyncedAt() == null ||
            workspace.getUsersSyncedAt().isBefore(Instant.now().minusSeconds(syncCooldownInMinutes * 60L));

        if (!shouldSyncUsers) {
            logger.info("No users to sync.");
            return;
        }

        if (!monitoringScopeFilter.isWorkspaceAllowed(workspace)) {
            logger.debug("Skipping user sync for workspace id={} due to monitoring filters.", workspace.getId());
            return;
        }

        logger.info("Syncing all existing users...");
        var currentTime = Instant.now();
        Long workspaceId = workspace.getId();
        try {
            gitHubClientExecutor.execute(workspaceId, gitHub -> {
                userSyncService.syncAllExistingUsers(gitHub);
                return null;
            });
        } catch (IOException e) {
            logger.error("Failed to initialize GitHub client for workspace {}: {}", workspaceId, e.getMessage());
            return;
        }

        // Reload again before saving to avoid stale entity references
        workspaceRepository
            .findById(workspaceId)
            .ifPresent(ws -> {
                ws.setUsersSyncedAt(currentTime);
                workspaceRepository.save(ws);
            });
        logger.info("User sync completed.");
    }

    public void syncRepositoryToMonitorAsync(RepositoryToMonitor repositoryToMonitor) {
        if (repositoryToMonitor == null || repositoryToMonitor.getId() == null) {
            logger.warn("Ignoring async sync request for unsaved repository monitor.");
            return;
        }

        Workspace workspace = repositoryToMonitor.getWorkspace();
        if (
            monitoringScopeFilter.isActive() &&
            (!monitoringScopeFilter.isRepositoryAllowed(repositoryToMonitor) ||
                !monitoringScopeFilter.isWorkspaceAllowed(workspace))
        ) {
            logger.debug(
                "Repository {} filtered out; skipping async sync scheduling.",
                repositoryToMonitor.getNameWithOwner()
            );
            return;
        }

        Long monitorId = repositoryToMonitor.getId();
        Runnable submitTask = () -> monitoringExecutor.submit(() -> runRepositoryMonitorSync(monitorId));

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        submitTask.run();
                    }
                }
            );
        } else {
            submitTask.run();
        }
    }

    private void runRepositoryMonitorSync(Long monitorId) {
        repositoryToMonitorRepository
            .findById(monitorId)
            .ifPresentOrElse(this::syncRepositoryToMonitor, () ->
                logger.debug("Repository monitor {} no longer exists; skipping async sync.", monitorId)
            );
    }

    /**
     * Syncs the data of the specified repository to the database and keeps track of the sync times
     *
     * @param repositoryToMonitor the repository to sync and syncing status
     */
    public void syncRepositoryToMonitor(RepositoryToMonitor repositoryToMonitor) {
        Workspace workspace = repositoryToMonitor.getWorkspace();
        if (
            monitoringScopeFilter.isActive() &&
            (!monitoringScopeFilter.isRepositoryAllowed(repositoryToMonitor) ||
                !monitoringScopeFilter.isWorkspaceAllowed(workspace))
        ) {
            logger.info(
                "Repository {} filtered out; skipping sync (repo allowed={}, workspace allowed={}).",
                repositoryToMonitor.getNameWithOwner(),
                monitoringScopeFilter.isRepositoryAllowed(repositoryToMonitor),
                monitoringScopeFilter.isWorkspaceAllowed(workspace)
            );
            return;
        }

        var cooldownTime = Instant.now().minusSeconds(syncCooldownInMinutes * 60L);

        boolean shouldSyncRepository =
            repositoryToMonitor.getRepositorySyncedAt() == null ||
            repositoryToMonitor.getRepositorySyncedAt().isBefore(cooldownTime);
        boolean shouldSyncLabels =
            repositoryToMonitor.getLabelsSyncedAt() == null ||
            repositoryToMonitor.getLabelsSyncedAt().isBefore(cooldownTime);
        boolean shouldSyncMilestones =
            repositoryToMonitor.getMilestonesSyncedAt() == null ||
            repositoryToMonitor.getMilestonesSyncedAt().isBefore(cooldownTime);
        boolean shouldSyncIssuesAndPullRequests =
            repositoryToMonitor.getIssuesAndPullRequestsSyncedAt() == null ||
            repositoryToMonitor.getIssuesAndPullRequestsSyncedAt().isBefore(cooldownTime);

        // Sync repository is required if any of the following is true
        if (shouldSyncLabels || shouldSyncMilestones || shouldSyncIssuesAndPullRequests) {
            shouldSyncRepository = true;
        }

        // Also trigger sync if backfill is enabled and may need to run
        // Note: We pass false here because recent sync hasn't run in this cycle yet
        if (!shouldSyncRepository && backfillEnabled && !repositoryToMonitor.isBackfillComplete()) {
            shouldSyncRepository = shouldRunBackfill(repositoryToMonitor, false);
        }

        // Nothing to sync
        if (!shouldSyncRepository) {
            logger.debug(
                "{} - Skipping sync (cooldown active). Last sync timestamps - repo: {}, labels: {}, milestones: {}, issues: {}",
                repositoryToMonitor.getNameWithOwner(),
                repositoryToMonitor.getRepositorySyncedAt(),
                repositoryToMonitor.getLabelsSyncedAt(),
                repositoryToMonitor.getMilestonesSyncedAt(),
                repositoryToMonitor.getIssuesAndPullRequestsSyncedAt()
            );
            return;
        }

        logger.info("{} - Syncing data...", repositoryToMonitor.getNameWithOwner());
        var ghRepository = syncRepository(repositoryToMonitor).orElse(null);
        if (ghRepository == null) {
            logger.warn(
                "{} - Repository data unavailable. Skipping downstream sync steps until access is restored.",
                repositoryToMonitor.getNameWithOwner()
            );
            return;
        }

        if (shouldSyncLabels) {
            logger.info("{} - Syncing labels...", repositoryToMonitor.getNameWithOwner());
            syncRepositoryLabels(ghRepository, repositoryToMonitor);
        }
        if (shouldSyncMilestones) {
            logger.info("{} - Syncing milestones...", repositoryToMonitor.getNameWithOwner());
            syncRepositoryMilestones(ghRepository, repositoryToMonitor);
        }
        if (shouldSyncIssuesAndPullRequests) {
            logger.info("{} - Syncing recent issues and pull requests...", repositoryToMonitor.getNameWithOwner());
            syncRepositoryRecentIssuesAndPullRequests(ghRepository, repositoryToMonitor);
        }

        // Run backfill only if enabled and conditions are met
        // - If recent sync just ran in this cycle, pass true (we know data is fresh)
        // - If recent sync didn't run but has run before, pass false (requires timestamp validation)
        if (backfillEnabled && shouldRunBackfill(repositoryToMonitor, shouldSyncIssuesAndPullRequests)) {
            logger.info(
                "{} - Running backfill batch (filter active={}, workspace allowed={}, repo allowed={})...",
                repositoryToMonitor.getNameWithOwner(),
                monitoringScopeFilter.isActive(),
                monitoringScopeFilter.isWorkspaceAllowed(repositoryToMonitor.getWorkspace()),
                monitoringScopeFilter.isRepositoryAllowed(repositoryToMonitor)
            );
            runBackfillBatch(ghRepository, repositoryToMonitor);
        }

        logger.info("{} - Data sync completed.", repositoryToMonitor.getNameWithOwner());
    }

    private Optional<GHRepository> syncRepository(RepositoryToMonitor repositoryToMonitor) {
        String nameWithOwner = repositoryToMonitor.getNameWithOwner();
        Long workSpaceId = repositoryToMonitor.getWorkspace().getId();
        var currentTime = Instant.now();
        try {
            var repository = repositorySyncService.syncRepository(workSpaceId, nameWithOwner);
            if (repository.isPresent()) {
                repositoryToMonitor.setRepositorySyncedAt(currentTime);
                repositoryToMonitorRepository.save(repositoryToMonitor);
                // Check cooldown before syncing collaborators
                syncCollaboratorsIfNeeded(repository.get(), repositoryToMonitor, currentTime);
            }
            return repository;
        } catch (RepositorySyncException syncException) {
            handleRepositorySyncFailure(repositoryToMonitor, syncException);
            return Optional.empty();
        }
    }

    private void syncCollaboratorsIfNeeded(
        GHRepository ghRepository,
        RepositoryToMonitor repositoryToMonitor,
        Instant currentTime
    ) {
        boolean shouldSync =
            repositoryToMonitor.getCollaboratorsSyncedAt() == null ||
            repositoryToMonitor
                .getCollaboratorsSyncedAt()
                .isBefore(currentTime.minusSeconds(syncCooldownInMinutes * 60L));
        if (!shouldSync) {
            logger.debug(
                "{} - Skipping collaborator sync (cooldown active). Last synced at {}",
                repositoryToMonitor.getNameWithOwner(),
                repositoryToMonitor.getCollaboratorsSyncedAt()
            );
            return;
        }
        collaboratorSyncService.syncCollaborators(ghRepository);
        repositoryToMonitor.setCollaboratorsSyncedAt(currentTime);
        repositoryToMonitorRepository.save(repositoryToMonitor);
    }

    private void handleRepositorySyncFailure(RepositoryToMonitor monitor, RepositorySyncException exception) {
        String nameWithOwner = monitor.getNameWithOwner();
        switch (exception.getReason()) {
            case NOT_FOUND -> {
                logger.warn(
                    "Repository {} was removed upstream; removing monitor for workspace {}.",
                    nameWithOwner,
                    monitor.getWorkspace() != null ? monitor.getWorkspace().getId() : "unknown"
                );
                removeMonitorFromWorkspace(monitor);
            }
            case FORBIDDEN -> logger.warn(
                "Repository {} is no longer accessible to the GitHub App. Will retry during the next sync window.",
                nameWithOwner
            );
            default -> logger.error(
                "Unexpected repository sync failure for {}: {}",
                nameWithOwner,
                exception.getMessage()
            );
        }
    }

    private void removeMonitorFromWorkspace(RepositoryToMonitor monitor) {
        var workspace = monitor.getWorkspace();
        if (workspace == null || workspace.getWorkspaceSlug() == null) {
            repositoryToMonitorRepository.delete(monitor);
            return;
        }
        try {
            getWorkspaceService().removeRepositoryToMonitor(workspace.getWorkspaceSlug(), monitor.getNameWithOwner());
        } catch (EntityNotFoundException ignored) {
            // Already removed by another thread; nothing else to do.
        }
    }

    public void syncTeams(Workspace workspace) {
        // Reload workspace to get fresh repository monitor list
        workspace = workspaceRepository.findById(workspace.getId()).orElse(null);
        if (workspace == null) {
            logger.warn("Workspace no longer exists; skipping team sync.");
            return;
        }

        if (!monitoringScopeFilter.isWorkspaceAllowed(workspace)) {
            logger.debug("Skipping team sync for workspace id={} due to monitoring filters.", workspace.getId());
            return;
        }

        // Check cooldown before syncing
        boolean shouldSyncTeams =
            workspace.getTeamsSyncedAt() == null ||
            workspace.getTeamsSyncedAt().isBefore(Instant.now().minusSeconds(syncCooldownInMinutes * 60L));
        if (!shouldSyncTeams) {
            logger.debug(
                "Skipping team sync for workspace {} due to cooldown (last synced at {}).",
                workspace.getWorkspaceSlug(),
                workspace.getTeamsSyncedAt()
            );
            return;
        }

        // Collect org names before entering GitHub client context to avoid lazy loading issues
        var orgNames = workspace
            .getRepositoriesToMonitor()
            .stream()
            .filter(monitoringScopeFilter::isRepositoryAllowed)
            .map(RepositoryToMonitor::getNameWithOwner)
            .map(s -> s.split("/")[0])
            .distinct()
            .toList();

        if (orgNames.isEmpty()) {
            logger.info("No organizations to sync teams for.");
            return;
        }

        final Workspace ws = workspace;
        try {
            gitHubClientExecutor.execute(workspace.getId(), gitHubClient -> {
                orgNames.forEach(org -> {
                    try {
                        logger.info("Syncing teams for organisation {}", org);
                        teamSyncService.syncAndSaveTeams(gitHubClient, org);
                    } catch (IOException e) {
                        logger.error("Team sync for {} failed: {}", org, e.getMessage());
                    }
                });
                // Update timestamp after successful sync
                var currentTime = Instant.now();
                ws.setTeamsSyncedAt(currentTime);
                workspaceRepository.save(ws);
                logger.info("Team sync completed.");
                return null;
            });
        } catch (IOException e) {
            logger.error(
                "Failed to initialize GitHub client for workspace {} while syncing teams: {}",
                workspace.getId(),
                e.getMessage()
            );
        }
    }

    private void syncRepositoryLabels(GHRepository repository, RepositoryToMonitor repositoryToMonitor) {
        var currentTime = Instant.now();
        labelSyncService.syncLabelsOfRepository(repository);
        repositoryToMonitor.setLabelsSyncedAt(currentTime);
        repositoryToMonitorRepository.save(repositoryToMonitor);
    }

    private void syncRepositoryMilestones(GHRepository repository, RepositoryToMonitor repositoryToMonitor) {
        var currentTime = Instant.now();
        milestoneSyncService.syncMilestonesOfRepository(repository);
        repositoryToMonitor.setMilestonesSyncedAt(currentTime);
        repositoryToMonitorRepository.save(repositoryToMonitor);
    }

    /**
     * Syncs the recent issues and pull requests of the repository in ascending order of their last update time.
     * Issues and pull requests are separate entites but are synced together in the same method.
     * Issues are synced first with their associated issue comments.
     * If an issue has a pull request, the pull request is synced next with its associated reviews and review comments.
     *
     * @param repository GitHub repository to sync
     * @param repositoryToMonitor syncing status
     */
    private void syncRepositoryRecentIssuesAndPullRequests(
        GHRepository repository,
        RepositoryToMonitor repositoryToMonitor
    ) {
        var cutoffDate = Instant.now().minusSeconds(timeframe * 24L * 60L * 60L);

        var issuesAndPullRequestsSyncedAt = repositoryToMonitor.getIssuesAndPullRequestsSyncedAt();
        if (issuesAndPullRequestsSyncedAt != null) {
            cutoffDate = issuesAndPullRequestsSyncedAt.isAfter(cutoffDate) ? issuesAndPullRequestsSyncedAt : cutoffDate;
        }

        PagedIterator<GHIssue> issuesIterator = issueSyncService.getIssuesIterator(repository, cutoffDate);
        Instant syncedUpToTime = Instant.now();

        while (issuesIterator.hasNext()) {
            var pageSyncedUpToTime = syncRepositoryRecentIssuesAndPullRequestsNextPage(repository, issuesIterator);
            // Only update sync timestamp if we actually processed items (null means empty page)
            if (pageSyncedUpToTime != null) {
                syncedUpToTime = pageSyncedUpToTime;
                repositoryToMonitor.setIssuesAndPullRequestsSyncedAt(syncedUpToTime);
                repositoryToMonitorRepository.save(repositoryToMonitor);
            }
        }

        // Always update the timestamp, even if no issues were processed.
        // This ensures cooldown is respected when there are no new issues.
        if (
            repositoryToMonitor.getIssuesAndPullRequestsSyncedAt() == null ||
            repositoryToMonitor.getIssuesAndPullRequestsSyncedAt().isBefore(syncedUpToTime)
        ) {
            repositoryToMonitor.setIssuesAndPullRequestsSyncedAt(syncedUpToTime);
            repositoryToMonitorRepository.save(repositoryToMonitor);
        }
    }

    /**
     * Syncs the next page of issues and pull requests of the repository.
     *
     * @param repository     GitHub repository to sync
     * @param issuesIterator iterator for fetching issues
     * @return the last updated time of the last issue or the current time if no issues were fetched
     */
    private Instant syncRepositoryRecentIssuesAndPullRequestsNextPage(
        GHRepository repository,
        PagedIterator<GHIssue> issuesIterator
    ) {
        var currentTime = Instant.now();
        var ghIssues = issuesIterator.nextPage();
        if (ghIssues.isEmpty()) {
            return null; // nothing fetched; avoid moving the sync cursor forward
        }
        var issues = ghIssues.stream().map(issueSyncService::processIssue).toList();
        issueCommentSyncService.syncIssueCommentsOfAllIssues(ghIssues);
        // Mark issues as synced and persist
        issues.forEach(issue -> {
            issue.setLastSyncAt(currentTime);
            issueRepository.save(issue);
        });

        var pullRequestNumbers = issues.stream().filter(Issue::isHasPullRequest).map(Issue::getNumber).toList();

        var ghPullRequests = pullRequestSyncService.syncPullRequests(repository, pullRequestNumbers, false);
        var pullRequests = ghPullRequests.stream().map(pullRequestSyncService::processPullRequest).toList();
        pullRequestReviewSyncService.syncReviewsOfAllPullRequests(ghPullRequests);
        pullRequestReviewCommentSyncService.syncReviewCommentsOfAllPullRequests(ghPullRequests);
        // Mark pull requests as synced and persist
        pullRequests.forEach(pullRequest -> {
            pullRequest.setLastSyncAt(currentTime);
            pullRequestRepository.save(pullRequest);
        });

        try {
            return issues.getLast().getUpdatedAt();
        } catch (NoSuchElementException e) {
            return currentTime;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BACKFILL SYSTEM
    // ═══════════════════════════════════════════════════════════════════════════
    // Strategic historical data synchronization that respects rate limits.
    //
    // Design principles:
    // 1. PRIORITY: Recent data first, backfill only after recent sync completes
    // 2. BATCHING: Process in small batches to avoid rate limit exhaustion
    // 3. CHECKPOINT: Track progress to resume after interruptions
    // 4. RATE-AWARE: Stop backfill when rate limit is low
    // 5. COOLDOWN: Wait between batches to spread load over time
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Determines if a backfill batch should run for this repository.
     * Prerequisites:
     * - Recent sync must be fully complete (not just started)
     * - Backfill must not be complete yet
     * - Cooldown since last backfill must have elapsed
     *
     * @param monitor the repository monitor
     * @param recentSyncJustCompleted true if this is called right after a recent sync finished in this cycle
     */
    private boolean shouldRunBackfill(RepositoryToMonitor monitor, boolean recentSyncJustCompleted) {
        // Recent sync must have run at least once
        if (monitor.getIssuesAndPullRequestsSyncedAt() == null) {
            return false;
        }

        // If recent sync just completed in this cycle, we can proceed with backfill
        // Otherwise, we need to ensure the recent sync timestamp is recent enough
        // (within the cooldown window) to know it's not from a stale/interrupted sync
        if (!recentSyncJustCompleted) {
            var cooldownTime = Instant.now().minusSeconds(syncCooldownInMinutes * 60L);
            if (monitor.getIssuesAndPullRequestsSyncedAt().isBefore(cooldownTime)) {
                // Recent sync hasn't run recently, don't start backfill
                // This prevents backfill from running on stale data
                return false;
            }
        }

        // Already complete
        if (monitor.isBackfillComplete()) {
            return false;
        }

        // Respect cooldown between batches
        if (monitor.getBackfillLastRunAt() != null) {
            var cooldownEnd = monitor.getBackfillLastRunAt().plusSeconds(backfillCooldownMinutes * 60L);
            if (Instant.now().isBefore(cooldownEnd)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Runs a single backfill batch for the repository.
     * Works backwards from the highest issue number on GitHub, syncing items that don't have lastSyncAt set.
     *
     * The backfill starts from the highest issue number in the repository (fetched from GitHub API)
     * and works backwards to issue #1. This ensures we eventually sync all historical issues
     * without re-syncing items that were already synced during recent sync.
     */
    void runBackfillBatch(GHRepository ghRepository, RepositoryToMonitor monitor) {
        var repository = repositoryRepository.findByNameWithOwner(monitor.getNameWithOwner());
        if (repository.isEmpty()) {
            logger.warn("{} - Repository not found in database, skipping backfill", monitor.getNameWithOwner());
            return;
        }
        var repositoryId = repository.get().getId();

        // Initialize high water mark if not set
        // Use the HIGHEST issue number on GitHub as the starting point so we never miss older items.
        if (!monitor.isBackfillInitialized()) {
            var highestIssueNumber = fetchHighestIssueNumber(ghRepository);

            if (highestIssueNumber.isEmpty()) {
                logger.warn(
                    "{} - Unable to determine highest issue number, cannot initialize backfill",
                    monitor.getNameWithOwner()
                );
                return;
            }

            int startingPoint = highestIssueNumber.get();
            if (startingPoint <= 0) {
                monitor.setBackfillHighWaterMark(0);
                monitor.setBackfillCheckpoint(0);
                repositoryToMonitorRepository.save(monitor);
                logger.info(
                    "{} - Repository has no issues; backfill complete by definition",
                    monitor.getNameWithOwner()
                );
                return;
            }

            monitor.setBackfillHighWaterMark(startingPoint);
            monitor.setBackfillCheckpoint(startingPoint);
            repositoryToMonitorRepository.save(monitor);
            logger.info(
                "{} - Initialized backfill: starting from issue #{} down to #1",
                monitor.getNameWithOwner(),
                startingPoint
            );
        }

        // Check rate limit before starting
        if (!hasEnoughRateLimitForBackfill(monitor.getWorkspace().getId())) {
            logger.debug("{} - Skipping backfill batch: rate limit below threshold", monitor.getNameWithOwner());
            return;
        }

        // Find unsynced issues in the batch range
        // Work backwards: from checkpoint down to (checkpoint - batchSize + 1), minimum 1
        var syncedIssues = issueRepository.findAllSyncedIssueNumbers(repositoryId);
        int currentCheckpoint = monitor.getBackfillCheckpoint();
        int batchEnd = Math.max(1, currentCheckpoint - backfillBatchSize + 1);

        var unsyncedInBatch = IntStream.iterate(currentCheckpoint, i -> i >= batchEnd, i -> i - 1)
            .filter(i -> !syncedIssues.contains(i))
            .boxed()
            .toList();

        logger.debug(
            "{} - Backfill batch: checking issues {} to {}, {} unsynced",
            monitor.getNameWithOwner(),
            currentCheckpoint,
            batchEnd,
            unsyncedInBatch.size()
        );

        int synced = 0;
        for (Integer issueNumber : unsyncedInBatch) {
            try {
                boolean wasSynced = syncSingleIssueWithPullRequest(ghRepository, repositoryId, issueNumber);
                if (wasSynced) {
                    synced++;
                }
            } catch (Exception e) {
                logger.warn(
                    "{} - Failed to sync issue #{}: {}",
                    monitor.getNameWithOwner(),
                    issueNumber,
                    e.getMessage()
                );
                // Continue with next issue instead of failing the whole batch
            }
        }

        // Update checkpoint to one below the batch we just processed
        int newCheckpoint = batchEnd - 1;
        monitor.setBackfillCheckpoint(newCheckpoint);
        monitor.setBackfillLastRunAt(Instant.now());
        repositoryToMonitorRepository.save(monitor);

        int remaining = monitor.getBackfillRemaining();
        logger.info(
            "{} - Backfill batch complete: synced {}/{} items, checkpoint now {}, {} remaining",
            monitor.getNameWithOwner(),
            synced,
            unsyncedInBatch.size(),
            newCheckpoint,
            remaining
        );

        if (remaining == 0) {
            logger.info("{} - Backfill complete!", monitor.getNameWithOwner());
        }
    }

    /**
     * Fetch the highest issue number in a repository by requesting the newest issue (created desc).
     * We only need the first page; direction DESC guarantees the first item is the max number.
     */
    protected Optional<Integer> fetchHighestIssueNumber(GHRepository ghRepository) {
        try {
            var latestIssues = ghRepository
                .queryIssues()
                .state(GHIssueState.ALL)
                .sort(GHIssueQueryBuilder.Sort.CREATED)
                .direction(GHDirection.DESC)
                .list()
                .withPageSize(1)
                .iterator();
            if (!latestIssues.hasNext()) {
                return Optional.empty();
            }
            return Optional.of(latestIssues.next().getNumber());
        } catch (Exception e) {
            logger.warn("Failed to fetch highest issue number for {}: {}", ghRepository.getFullName(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Syncs a single issue and its associated pull request if applicable.
     * @return true if the issue was synced (existed), false if it was skipped (deleted/not found)
     */
    private boolean syncSingleIssueWithPullRequest(GHRepository ghRepository, long repositoryId, int issueNumber) {
        var currentTime = Instant.now();

        // Sync the issue
        var ghIssue = issueSyncService.syncIssue(ghRepository, issueNumber);
        if (ghIssue.isEmpty()) {
            // Issue doesn't exist (deleted, never created, etc.) - not an error
            logger.debug("Issue #{} not found on GitHub, skipping", issueNumber);
            return false;
        }

        var issue = issueSyncService.processIssue(ghIssue.get());
        issueCommentSyncService.syncIssueCommentsOfIssue(ghIssue.get());
        issue.setLastSyncAt(currentTime);
        issueRepository.save(issue);

        // If it's a PR, sync PR-specific data
        if (issue.isHasPullRequest()) {
            var existingPr = pullRequestRepository.findByRepositoryIdAndNumber(repositoryId, issueNumber);
            if (existingPr.isEmpty() || existingPr.get().getLastSyncAt() == null) {
                var ghPullRequest = pullRequestSyncService.syncPullRequest(ghRepository, issueNumber, true);
                if (ghPullRequest.isPresent()) {
                    var pullRequest = pullRequestSyncService.processPullRequest(ghPullRequest.get());
                    pullRequestReviewSyncService.syncReviewsOfPullRequest(ghPullRequest.get());
                    pullRequestReviewCommentSyncService.syncReviewCommentsOfPullRequest(ghPullRequest.get());
                    pullRequest.setLastSyncAt(currentTime);
                    pullRequestRepository.save(pullRequest);
                }
            }
        }
        return true;
    }

    /**
     * Checks if there's enough rate limit remaining to run a backfill batch.
     * This prevents backfill from consuming rate limit needed for real-time operations.
     */
    private boolean hasEnoughRateLimitForBackfill(Long workspaceId) {
        try {
            return gitHubClientExecutor.execute(workspaceId, github -> {
                var rateLimit = github.getRateLimit();
                int remaining = rateLimit.getCore().getRemaining();
                boolean hasEnough = remaining >= backfillRateLimitThreshold;
                if (!hasEnough) {
                    logger.debug(
                        "Rate limit too low for backfill: {} remaining, {} threshold",
                        remaining,
                        backfillRateLimitThreshold
                    );
                }
                return hasEnough;
            });
        } catch (IOException e) {
            logger.warn("Failed to check rate limit: {}", e.getMessage());
            return false; // Be conservative - don't run backfill if we can't check
        }
    }
}
