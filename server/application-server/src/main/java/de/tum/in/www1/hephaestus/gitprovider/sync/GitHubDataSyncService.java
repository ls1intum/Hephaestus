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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class GitHubDataSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubDataSyncService.class);

    @Value("${monitoring.timeframe}")
    private int timeframe;

    @Value("${monitoring.sync-cooldown-in-minutes}")
    private int syncCooldownInMinutes;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private RepositoryToMonitorRepository repositoryToMonitorRepository;

    @Autowired
    @Lazy
    private WorkspaceService workspaceService;

    @Autowired
    private GitHubUserSyncService userSyncService;

    @Autowired
    private GitHubTeamSyncService teamSyncService;

    @Autowired
    private GitHubClientExecutor gitHubClientExecutor;

    @Autowired
    private GitHubRepositorySyncService repositorySyncService;

    @Autowired
    private GitHubRepositoryCollaboratorSyncService collaboratorSyncService;

    @Autowired
    private GitHubLabelSyncService labelSyncService;

    @Autowired
    private GitHubMilestoneSyncService milestoneSyncService;

    @Autowired
    private GitHubIssueSyncService issueSyncService;

    @Autowired
    private GitHubIssueCommentSyncService issueCommentSyncService;

    @Autowired
    private GitHubPullRequestSyncService pullRequestSyncService;

    @Autowired
    private GitHubPullRequestReviewSyncService pullRequestReviewSyncService;

    @Autowired
    private GitHubPullRequestReviewCommentSyncService pullRequestReviewCommentSyncService;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    @Qualifier("monitoringExecutor")
    private AsyncTaskExecutor monitoringExecutor;

    @Autowired
    private MonitoringScopeFilter monitoringScopeFilter;

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
            logger.debug("Repository {} filtered out; skipping sync.", repositoryToMonitor.getNameWithOwner());
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

        // Check if there are any unsynced issues or pull requests in the past
        if (!shouldSyncRepository) {
            shouldSyncRepository = shouldSyncAllPastIssuesAndPullRequests(repositoryToMonitor);
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

        // TODO: Re-enable once it works without exceptions
        //
        // if (shouldSyncAllPastIssuesAndPullRequests(repositoryToMonitor)) {
        //     logger.info("{} - Syncing all past issues and pull requests...", repositoryToMonitor.getNameWithOwner());
        //     syncAllPastIssuesAndPullRequests(ghRepository, repositoryToMonitor);
        // }
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
                collaboratorSyncService.syncCollaborators(repository.get());
            }
            return repository;
        } catch (RepositorySyncException syncException) {
            handleRepositorySyncFailure(repositoryToMonitor, syncException);
            return Optional.empty();
        }
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
            workspaceService.removeRepositoryToMonitor(workspace.getWorkspaceSlug(), monitor.getNameWithOwner());
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
            syncedUpToTime = syncRepositoryRecentIssuesAndPullRequestsNextPage(repository, issuesIterator);
            repositoryToMonitor.setIssuesAndPullRequestsSyncedAt(syncedUpToTime);
            repositoryToMonitorRepository.save(repositoryToMonitor);
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
        var issues = ghIssues.stream().map(issueSyncService::processIssue).toList();
        issueCommentSyncService.syncIssueCommentsOfAllIssues(ghIssues);
        issues.forEach(issue -> issue.setLastSyncAt(currentTime));

        var pullRequestNumbers = issues.stream().filter(Issue::isHasPullRequest).map(Issue::getNumber).toList();

        var ghPullRequests = pullRequestSyncService.syncPullRequests(repository, pullRequestNumbers, false);
        var pullRequests = ghPullRequests.stream().map(pullRequestSyncService::processPullRequest).toList();
        pullRequestReviewSyncService.syncReviewsOfAllPullRequests(ghPullRequests);
        pullRequestReviewCommentSyncService.syncReviewCommentsOfAllPullRequests(ghPullRequests);
        pullRequests.forEach(pullRequest -> pullRequest.setLastSyncAt(currentTime));

        try {
            return issues.getLast().getUpdatedAt();
        } catch (NoSuchElementException e) {
            return currentTime;
        }
    }

    /**
     * Checks if there are any issues or pull requests that have not been synced
     *
     * @param repositoryToMonitor
     * @return true if there are any issues or pull requests that have not been synced
     */
    private boolean shouldSyncAllPastIssuesAndPullRequests(RepositoryToMonitor repositoryToMonitor) {
        var repository = repositoryRepository.findByNameWithOwner(repositoryToMonitor.getNameWithOwner());
        if (repository.isEmpty()) {
            return false;
        }

        var repositoryId = repository.get().getId();
        int lastIssueNumber = issueRepository.findLastIssueNumber(repositoryId).orElse(0);
        if (lastIssueNumber == 0) {
            return false;
        }

        var syncedIssues = issueRepository.findAllSyncedIssueNumbers(repositoryId);
        var unsyncedIssues = IntStream.iterate(lastIssueNumber, i -> i > 0, i -> i - 1)
            .filter(i -> !syncedIssues.contains(i))
            .boxed()
            .collect(Collectors.toSet());

        if (unsyncedIssues.isEmpty()) {
            var pullRequestNumbers = issueRepository.findAllIssueNumbersWithPullRequest(repositoryId);
            var syncedPullRequests = pullRequestRepository.findAllSyncedPullRequestNumbers(repositoryId);
            var unsyncedPullRequests = pullRequestNumbers
                .stream()
                .filter(i -> !syncedPullRequests.contains(i))
                .collect(Collectors.toSet());

            return !unsyncedPullRequests.isEmpty();
        }

        return true;
    }

    /**
     * Syncs all past issues and pull requests of the repository that have not been synced yet.
     * This method will process all issues and pull requests that have not yet been synced and
     * ensures they are synchronized with the repository.
     *
     * @param repository the GitHub repository to sync
     */
    //TODO: Method never used
    private void syncAllPastIssuesAndPullRequests(GHRepository repository) {
        int lastIssueNumber = issueRepository.findLastIssueNumber(repository.getId()).orElse(0);
        if (lastIssueNumber == 0) {
            return;
        }

        var syncedIssues = issueRepository.findAllSyncedIssueNumbers(repository.getId());
        var unsyncedIssues = IntStream.iterate(lastIssueNumber, i -> i > 0, i -> i - 1)
            .filter(issueNumber -> !syncedIssues.contains(issueNumber))
            .boxed()
            .toList();

        logger.info("Syncing {} past issues and associated pull requests if necessary...", unsyncedIssues.size());
        unsyncedIssues.forEach(issueNumber -> {
            var isPullRequest = syncPastIssue(repository, issueNumber);
            if (!isPullRequest) {
                return;
            }
            var pullRequest = pullRequestRepository.findByRepositoryIdAndNumber(repository.getId(), issueNumber);
            if (pullRequest.isEmpty() || pullRequest.get().getLastSyncAt() == null) {
                syncPastPullRequest(repository, issueNumber);
            }
        });
        logger.info("Past issues sync completed.");

        // Sync remaining pull requests in case they were not synced with the issues
        var pullRequestNumbers = issueRepository.findAllIssueNumbersWithPullRequest(repository.getId());
        var syncedPullRequests = pullRequestRepository.findAllSyncedPullRequestNumbers(repository.getId());
        var unsyncedPullRequests = pullRequestNumbers
            .stream()
            .filter(pullRequestNumber -> !syncedPullRequests.contains(pullRequestNumber))
            .sorted((a, b) -> b - a)
            .toList();

        logger.info("Syncing {} past pull requests...", unsyncedPullRequests.size());
        unsyncedPullRequests.forEach(pullRequestNumber -> syncPastPullRequest(repository, pullRequestNumber));
        logger.info("Past pull requests sync completed.");
    }

    private boolean syncPastIssue(GHRepository repository, Integer issueNumber) {
        var issue = issueSyncService.syncIssue(repository, issueNumber);
        if (issue.isEmpty()) {
            return false;
        }
        issueCommentSyncService.syncIssueCommentsOfIssue(issue.get());
        return issue.get().isPullRequest();
    }

    private void syncPastPullRequest(GHRepository repository, Integer pullRequestNumber) {
        var pullRequest = pullRequestSyncService.syncPullRequest(repository, pullRequestNumber, true);
        if (pullRequest.isEmpty()) {
            return;
        }
        pullRequestReviewSyncService.syncReviewsOfPullRequest(pullRequest.get());
        pullRequestReviewCommentSyncService.syncReviewCommentsOfPullRequest(pullRequest.get());
    }
}
