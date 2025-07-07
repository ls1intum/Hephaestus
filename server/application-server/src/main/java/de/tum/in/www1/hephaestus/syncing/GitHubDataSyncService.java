package de.tum.in.www1.hephaestus.syncing;

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
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import de.tum.in.www1.hephaestus.gitprovider.teamV2.github.GitHubTeamSyncService;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserSyncService;
import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitor;
import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.io.IOException;
import java.time.OffsetDateTime;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class GitHubDataSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubDataSyncService.class);

    @Value("${monitoring.timeframe}")
    private int timeframe;

    @Value("${monitoring.sync-cooldown-in-minutes}")
    private int syncCooldownInMinutes;

    @Value("${monitoring.sync-all-issues-and-pull-requests}")
    private boolean syncAllIssuesAndPullRequests;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private RepositoryToMonitorRepository repositoryToMonitorRepository;

    @Autowired
    private GitHubUserSyncService userSyncService;

    @Autowired
    private GitHubTeamSyncService teamSyncService;

    @Autowired
    private GitHubRepositorySyncService repositorySyncService;

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

    /**
     * Syncs all existing users in the database with their GitHub data
     */
    public void syncUsers(Workspace workspace) {
        boolean shouldSyncUsers =
            workspace.getUsersSyncedAt() == null ||
            workspace.getUsersSyncedAt().isBefore(OffsetDateTime.now().minusMinutes(syncCooldownInMinutes));

        if (!shouldSyncUsers) {
            logger.info("No users to sync.");
            return;
        }

        logger.info("Syncing all existing users...");
        var currentTime = OffsetDateTime.now();
        userSyncService.syncAllExistingUsers();
        workspace.setUsersSyncedAt(currentTime);
        workspaceRepository.save(workspace);
        logger.info("User sync completed.");
    }

    @Async
    public void syncRepositoryToMonitorAsync(RepositoryToMonitor repositoryToMonitor) {
        syncRepositoryToMonitor(repositoryToMonitor);
    }

    /**
     * Syncs the data of the specified repository to the database and keeps track of the sync times
     *
     * @param repositoryToMonitor the repository to sync and syncing status
     */
    public void syncRepositoryToMonitor(RepositoryToMonitor repositoryToMonitor) {
        logger.info(repositoryToMonitor.getNameWithOwner() + " - Syncing data...");

        var cooldownTime = OffsetDateTime.now().minusMinutes(syncCooldownInMinutes);

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
            logger.info(repositoryToMonitor.getNameWithOwner() + " - No data to sync.");
            return;
        }

        logger.info(repositoryToMonitor.getNameWithOwner() + " - Syncing repository data...");
        var ghRepository = syncRepository(repositoryToMonitor).orElse(null);
        if (ghRepository == null) {
            return;
        }

        if (shouldSyncLabels) {
            logger.info(repositoryToMonitor.getNameWithOwner() + " - Syncing labels...");
            syncRepositoryLabels(ghRepository, repositoryToMonitor);
        }
        if (shouldSyncMilestones) {
            logger.info(repositoryToMonitor.getNameWithOwner() + " - Syncing milestones...");
            syncRepositoryMilestones(ghRepository, repositoryToMonitor);
        }
        if (shouldSyncIssuesAndPullRequests) {
            logger.info(repositoryToMonitor.getNameWithOwner() + " - Syncing recent issues and pull requests...");
            syncRepositoryRecentIssuesAndPullRequests(ghRepository, repositoryToMonitor);
        }

        // TODO: Re-enable once it works without exceptions
        //
        // if (shouldSyncAllPastIssuesAndPullRequests(repositoryToMonitor)) {
        //     logger.info(repositoryToMonitor.getNameWithOwner() + " - Syncing all past issues and pull requests...");
        //     syncAllPastIssuesAndPullRequests(ghRepository, repositoryToMonitor);
        // }
        logger.info(repositoryToMonitor.getNameWithOwner() + " - Data sync completed.");
    }

    private Optional<GHRepository> syncRepository(RepositoryToMonitor repositoryToMonitor) {
        String nameWithOwner = repositoryToMonitor.getNameWithOwner();
        var currentTime = OffsetDateTime.now();
        var repository = repositorySyncService.syncRepository(nameWithOwner);
        repositoryToMonitor.setRepositorySyncedAt(currentTime);
        repositoryToMonitorRepository.save(repositoryToMonitor);
        return repository;
    }

    public void syncTeams(Workspace workspace) {
        workspace
            .getRepositoriesToMonitor()
            .stream()
            .map(RepositoryToMonitor::getNameWithOwner)
            .map(s -> s.split("/")[0])
            .distinct()
            .forEach(org -> {
                try {
                    logger.info("Syncing teams for organisation {}", org);
                    teamSyncService.syncAndSaveTeams(org);
                } catch (IOException e) {
                    logger.error("Team sync for {} failed: {}", org, e.getMessage());
                }
            });

        logger.info("Team sync completed.");
    }

    private void syncRepositoryLabels(GHRepository repository, RepositoryToMonitor repositoryToMonitor) {
        var currentTime = OffsetDateTime.now();
        labelSyncService.syncLabelsOfRepository(repository);
        repositoryToMonitor.setLabelsSyncedAt(currentTime);
        repositoryToMonitorRepository.save(repositoryToMonitor);
    }

    private void syncRepositoryMilestones(GHRepository repository, RepositoryToMonitor repositoryToMonitor) {
        var currentTime = OffsetDateTime.now();
        milestoneSyncService.syncMilestonesOfRepository(repository);
        repositoryToMonitor.setMilestonesSyncedAt(currentTime);
        repositoryToMonitorRepository.save(repositoryToMonitor);
    }

    /**
     * Syncs the recent issues and pull requests of the repository in ascending order of their last update time.
     *
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
        var cutoffDate = OffsetDateTime.now().minusDays(timeframe);

        var issuesAndPullRequestsSyncedAt = repositoryToMonitor.getIssuesAndPullRequestsSyncedAt();
        if (issuesAndPullRequestsSyncedAt != null) {
            cutoffDate = issuesAndPullRequestsSyncedAt.isAfter(cutoffDate) ? issuesAndPullRequestsSyncedAt : cutoffDate;
        }

        PagedIterator<GHIssue> issuesIterator = issueSyncService.getIssuesIterator(repository, cutoffDate);
        while (issuesIterator.hasNext()) {
            var syncedUpToTime = syncRepositoryRecentIssuesAndPullRequestsNextPage(repository, issuesIterator);
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
    private OffsetDateTime syncRepositoryRecentIssuesAndPullRequestsNextPage(
        GHRepository repository,
        PagedIterator<GHIssue> issuesIterator
    ) {
        var currentTime = OffsetDateTime.now();
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
        var lastIssueNumber = issueRepository.findLastIssueNumber(repositoryId).orElse(0);
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
     *
     * This method will process all issues and pull requests that have not yet been synced and
     * ensures they are synchronized with the repository.
     *
     * @param repository the GitHub repository to sync
     * @param repositoryToMonitor the repository to sync
     */
    private void syncAllPastIssuesAndPullRequests(GHRepository repository, RepositoryToMonitor repositoryToMonitor) {
        var lastIssueNumber = issueRepository.findLastIssueNumber(repository.getId()).orElse(0);
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
