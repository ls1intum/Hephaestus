package de.tum.in.www1.hephaestus.gitprovider.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import de.tum.in.www1.hephaestus.gitprovider.team.github.GitHubTeamSyncService;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserSyncService;
import de.tum.in.www1.hephaestus.monitoring.MonitoringScopeFilter;
import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitor;
import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import de.tum.in.www1.hephaestus.workspace.WorkspaceService;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.AsyncTaskExecutor;

/**
 * Focused unit test guarding the backfill high-water mark initialization to prevent regressions
 * like starting from a truncated issue number (e.g., #6985 when the repo has >11k issues).
 */
@Tag("unit")
@DisplayName("GitHubDataSyncService")
class GitHubDataSyncServiceTest {

    @Test
    @DisplayName("initializes backfill from highest issue number, not lowest synced")
    void initializesBackfillFromHighestIssueNumberNotLowest() throws Exception {
        // Arrange
        Workspace workspace = new Workspace();
        workspace.setId(99L);

        RepositoryToMonitor monitor = new RepositoryToMonitor();
        monitor.setNameWithOwner("owner/repo");
        monitor.setWorkspace(workspace);

        var repository = new de.tum.in.www1.hephaestus.gitprovider.repository.Repository();
        repository.setId(1L);

        WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
        RepositoryToMonitorRepository repositoryToMonitorRepository = mock(RepositoryToMonitorRepository.class);
        RepositoryRepository repositoryRepository = mock(RepositoryRepository.class);
        IssueRepository issueRepository = mock(IssueRepository.class);
        PullRequestRepository pullRequestRepository = mock(PullRequestRepository.class);
        GitHubUserSyncService userSyncService = mock(GitHubUserSyncService.class);
        GitHubTeamSyncService teamSyncService = mock(GitHubTeamSyncService.class);
        GitHubRepositorySyncService repositorySyncService = mock(GitHubRepositorySyncService.class);
        GitHubRepositoryCollaboratorSyncService collaboratorSyncService = mock(
            GitHubRepositoryCollaboratorSyncService.class
        );
        GitHubLabelSyncService labelSyncService = mock(GitHubLabelSyncService.class);
        GitHubMilestoneSyncService milestoneSyncService = mock(GitHubMilestoneSyncService.class);
        GitHubIssueSyncService issueSyncService = mock(GitHubIssueSyncService.class);
        GitHubIssueCommentSyncService issueCommentSyncService = mock(GitHubIssueCommentSyncService.class);
        GitHubPullRequestSyncService pullRequestSyncService = mock(GitHubPullRequestSyncService.class);
        GitHubPullRequestReviewSyncService pullRequestReviewSyncService = mock(
            GitHubPullRequestReviewSyncService.class
        );
        GitHubPullRequestReviewCommentSyncService pullRequestReviewCommentSyncService = mock(
            GitHubPullRequestReviewCommentSyncService.class
        );
        ObjectProvider<WorkspaceService> workspaceServiceProvider = mock(ObjectProvider.class);
        AsyncTaskExecutor monitoringExecutor = mock(AsyncTaskExecutor.class);
        MonitoringScopeFilter monitoringScopeFilter = mock(MonitoringScopeFilter.class);
        var gitHubClientExecutor = mock(de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubClientExecutor.class);

        when(repositoryRepository.findByNameWithOwner("owner/repo")).thenReturn(Optional.of(repository));
        when(issueRepository.findAllSyncedIssueNumbers(repository.getId())).thenReturn(
            IntStream.rangeClosed(11026, 11050).boxed().collect(Collectors.toSet())
        );
        when(gitHubClientExecutor.execute(anyLong(), any())).thenReturn(true);

        // Test harness: override highest issue number to a known value (11050)
        GitHubDataSyncService service = new GitHubDataSyncService(
            7,
            15,
            true,
            25,
            500,
            5,
            workspaceRepository,
            repositoryToMonitorRepository,
            repositoryRepository,
            issueRepository,
            pullRequestRepository,
            userSyncService,
            teamSyncService,
            gitHubClientExecutor,
            repositorySyncService,
            collaboratorSyncService,
            labelSyncService,
            milestoneSyncService,
            issueSyncService,
            issueCommentSyncService,
            pullRequestSyncService,
            pullRequestReviewSyncService,
            pullRequestReviewCommentSyncService,
            workspaceServiceProvider,
            monitoringExecutor,
            monitoringScopeFilter
        ) {
            @Override
            protected Optional<Integer> fetchHighestIssueNumber(GHRepository ghRepository) {
                return Optional.of(11050);
            }
        };

        GHRepository ghRepository = mock(GHRepository.class);

        // Act: invoke backfill batch (accessible due to package-private visibility)
        service.runBackfillBatch(ghRepository, monitor);

        // Assert
        assertThat(monitor.getBackfillHighWaterMark()).isEqualTo(11050);
        assertThat(monitor.getBackfillCheckpoint()).isEqualTo(11025); // highWaterMark - batchSize
    }
}
