package de.tum.in.www1.hephaestus.syncing;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Value("${github.authToken:null}")
    private String ghAuthToken;

    @Value("${monitoring.timeframe}")
    private int timeframe;

    @Value("${monitoring.runOnStartupCooldownInMinutes}")
    private int runOnStartupCooldownInMinutes;

    @Autowired
    private DataSyncStatusRepository dataSyncStatusRepository;
    @Autowired
    private GitHubUserSyncService userSyncService;
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
        var issues = issueSyncService.syncIssuesOfAllRepositories(repositories, Optional.of(cutoffDate)); // also
                                                                                                          // contains
                                                                                                          // PRs as
                                                                                                          // issues
        issueCommentSyncService.syncIssueCommentsOfAllIssues(issues, Optional.of(cutoffDate));
        var pullRequests = pullRequestSyncService.syncPullRequestsOfAllRepositories(repositories,
                Optional.of(cutoffDate));
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
