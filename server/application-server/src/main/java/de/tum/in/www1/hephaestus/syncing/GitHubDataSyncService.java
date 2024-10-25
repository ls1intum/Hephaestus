package de.tum.in.www1.hephaestus.syncing;

import java.util.Date;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

    @Value("${github.authToken:null}")
    private String ghAuthToken;

    @Value("${monitoring.repositories}")
    private String[] repositoriesToMonitor;

    @Value("${monitoring.timeframe}")
    private int timeframe;

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
            GitHubUserSyncService userSyncService,
            GitHubRepositorySyncService repositorySyncService,
            GitHubLabelSyncService labelSyncService,
            GitHubMilestoneSyncService milestoneSyncService,
            GitHubIssueSyncService issueSyncService,
            GitHubIssueCommentSyncService issueCommentSyncService,
            GitHubPullRequestSyncService pullRequestSyncService,
            GitHubPullRequestReviewSyncService pullRequestReviewSyncService,
            GitHubPullRequestReviewCommentSyncService pullRequestReviewCommentSyncService) {
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

    public void syncData() {
        var cutoffDate = Optional.of(new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 24 * timeframe));

        var repositories = repositorySyncService.syncAllMonitoredRepositories();
        labelSyncService.syncLabelsOfAllRepositories(repositories);
        milestoneSyncService.syncMilestonesOfAllRepositories(repositories);
        var issues = issueSyncService.syncIssuesOfAllRepositories(repositories, cutoffDate); // also contains PRs as issues
        issueCommentSyncService.syncIssueCommentsOfAllIssues(issues, cutoffDate);
        var pullRequests = pullRequestSyncService.syncPullRequestsOfAllRepositories(repositories, cutoffDate);
        pullRequestReviewSyncService.syncReviewsOfAllPullRequests(pullRequests);
        pullRequestReviewCommentSyncService.syncReviewCommentsOfAllPullRequests(pullRequests);
        userSyncService.syncAllExistingUsers();
    }
}
