package de.tum.in.www1.hephaestus.syncing;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;

import de.tum.in.www1.hephaestus.gitprovider.issue.github.GitHubIssueSyncService;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.github.GitHubIssueCommentSyncService;
import de.tum.in.www1.hephaestus.gitprovider.label.github.GitHubLabelSyncService;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.GitHubMilestoneSyncService;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.GitHubPullRequestSyncService;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github.GitHubPullRequestReviewSyncService;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github.GitHubPullRequestReviewCommentSyncService;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserSyncService;

@Service
public class GitHubDataSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubDataSyncService.class);

    @Value("${github.auth-token:null}")
    private String ghAuthToken;

    @Value("${monitoring.repositories}")
    private String[] repositoriesToMonitor;

    @Value("${monitoring.timeframe}")
    private int timeframe;

    @Value("${monitoring.run-on-startup-cooldown-in-minutes}")
    private int runOnStartupCooldownInMinutes;

    private final DataSyncStatusRepository dataSyncStatusRepository;
    private final GitHubUserSyncService userSyncService;
    private final GitHubRepositorySyncService repositorySyncService;
    private final GitHubLabelSyncService labelSyncService;
    private final GitHubMilestoneSyncService milestoneSyncService;
    private final GitHubIssueSyncService issueSyncService;
    private final GitHubIssueCommentSyncService issueCommentSyncService;
    private final GitHubPullRequestSyncService pullRequestSyncService;
    private final GitHubPullRequestReviewSyncService pullRequestReviewSyncService;
    private final GitHubPullRequestReviewCommentSyncService pullRequestReviewCommentSyncService;

    public GitHubDataSyncService(
            DataSyncStatusRepository dataSyncStatusRepository,
            GitHubUserSyncService userSyncService,
            GitHubRepositorySyncService repositorySyncService,
            GitHubLabelSyncService labelSyncService,
            GitHubMilestoneSyncService milestoneSyncService,
            GitHubIssueSyncService issueSyncService,
            GitHubIssueCommentSyncService issueCommentSyncService,
            GitHubPullRequestSyncService pullRequestSyncService,
            GitHubPullRequestReviewSyncService pullRequestReviewSyncService,
            GitHubPullRequestReviewCommentSyncService pullRequestReviewCommentSyncService) {
        this.dataSyncStatusRepository = dataSyncStatusRepository;
        this.userSyncService = userSyncService;
        this.repositorySyncService = repositorySyncService;
        this.labelSyncService = labelSyncService;
        this.milestoneSyncService = milestoneSyncService;
        this.issueSyncService = issueSyncService;
        this.issueCommentSyncService = issueCommentSyncService;
        this.pullRequestSyncService = pullRequestSyncService;
        this.pullRequestReviewSyncService = pullRequestReviewSyncService;
        this.pullRequestReviewCommentSyncService = pullRequestReviewCommentSyncService;
    }

    @Transactional
    public void syncData() {
        var cutoffDate = OffsetDateTime.now().minusDays(timeframe);
        
        // Get last sync time
        var lastSync = dataSyncStatusRepository.findTopByOrderByStartTimeDesc();
        if (lastSync.isPresent()) {
            var lastSyncTime = lastSync.get().getStartTime();
            cutoffDate = lastSyncTime.isAfter(cutoffDate) ? lastSyncTime : cutoffDate;
        } 

        var cooldownTime = OffsetDateTime.now().minusMinutes(runOnStartupCooldownInMinutes);
        if (lastSync.isPresent() && lastSync.get().getStartTime().isAfter(cooldownTime)) {
            logger.info("Skipping sync, last sync was less than {} minutes ago", runOnStartupCooldownInMinutes);
            return;
        }

        // Start new sync
        var startTime = OffsetDateTime.now();

        var repositories = repositorySyncService.syncAllMonitoredRepositories();
        labelSyncService.syncLabelsOfAllRepositories(repositories);
        milestoneSyncService.syncMilestonesOfAllRepositories(repositories);
        var issues = issueSyncService.syncIssuesOfAllRepositories(repositories, Optional.of(cutoffDate)); // also contains PRs as issues
        issueCommentSyncService.syncIssueCommentsOfAllIssues(issues, Optional.of(cutoffDate));
        var pullRequests = pullRequestSyncService.syncPullRequestsOfAllRepositories(repositories, Optional.of(cutoffDate));
        pullRequestReviewSyncService.syncReviewsOfAllPullRequests(pullRequests);
        pullRequestReviewCommentSyncService.syncReviewCommentsOfAllPullRequests(pullRequests);
        userSyncService.syncAllExistingUsers();

        var endTime = OffsetDateTime.now();

        // Store successful sync status
        var syncStatus = new DataSyncStatus();
        syncStatus.setStartTime(startTime);
        syncStatus.setEndTime(endTime);
        dataSyncStatusRepository.save(syncStatus);
    }
}
