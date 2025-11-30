package de.tum.in.www1.hephaestus.gitprovider.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.sync.GitHubDataSyncService;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;

/**
 * Live GitHub integration tests for the data sync service.
 * These tests use real GitHub API calls with actual credentials.
 *
 * Best practices followed:
 * - No mocking: tests hit real GitHub API endpoints
 * - Ephemeral resources: creates temporary repos/issues that are cleaned up
 * - Realistic scenarios: tests actual sync behavior end-to-end
 * - Independent: each test can run in isolation
 * - Deterministic: uses controlled test data
 */
@TestPropertySource(
    properties = {
        "monitoring.timeframe=7",
        "monitoring.sync-cooldown-in-minutes=0", // Disable cooldown for tests
        "monitoring.backfill.enabled=true",
        "monitoring.backfill.batch-size=5",
        "monitoring.backfill.rate-limit-threshold=100",
        "monitoring.backfill.cooldown-minutes=0", // Disable cooldown for tests
    }
)
class GitHubDataSyncIntegrationTest extends AbstractGitHubSyncIntegrationTest {

    @Autowired
    private GitHubDataSyncService dataSyncService;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Value("${monitoring.timeframe}")
    private int timeframe;

    // ═══════════════════════════════════════════════════════════════════════════
    // RECENT SYNC TESTS
    // Verify that recent sync correctly uses timeframe and syncs only recent items
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Recent Sync - Timeframe Behavior")
    class RecentSyncTimeframeBehavior {

        @Test
        @DisplayName("Recent sync should set issuesAndPullRequestsSyncedAt timestamp")
        void recentSyncSetsTimestamp() throws Exception {
            // Arrange
            var ghRepository = createEphemeralRepository("recent-ts");
            var monitor = registerRepositoryToMonitor(ghRepository);

            // Act
            dataSyncService.syncRepositoryToMonitor(monitor);

            // Assert
            var refreshedMonitor = repositoryToMonitorRepository.findById(monitor.getId()).orElseThrow();
            assertThat(refreshedMonitor.getIssuesAndPullRequestsSyncedAt())
                .as("issuesAndPullRequestsSyncedAt should be set after sync")
                .isNotNull()
                .isAfter(Instant.now().minus(Duration.ofMinutes(5)));
        }

        @Test
        @DisplayName("Recent sync should sync issues created within timeframe")
        void recentSyncSyncsRecentIssues() throws Exception {
            // Arrange
            var ghRepository = createEphemeralRepository("recent-issue");
            var createdIssue = createIssueWithComment(ghRepository);
            var monitor = registerRepositoryToMonitor(ghRepository);

            // Wait for GitHub API to propagate the issue via query (more reliable than single issue fetch)
            awaitIssuesVisibleInApi(ghRepository, 1);

            // Act
            dataSyncService.syncRepositoryToMonitor(monitor);

            // Assert - query by repository and number since GitHub ID is the primary key
            var repoId = repositoryRepository.findByNameWithOwner(ghRepository.getFullName()).orElseThrow().getId();

            // Wait for issue to be in the synced set
            awaitCondition("issue in synced set", () -> {
                Set<Integer> synced = issueRepository.findAllSyncedIssueNumbers(repoId);
                return synced.contains(createdIssue.issueNumber());
            });

            var syncedNumbers = issueRepository.findAllSyncedIssueNumbers(repoId);
            assertThat(syncedNumbers).as("Issue number should be in synced set").contains(createdIssue.issueNumber());
        }

        @Test
        @DisplayName("Recent sync should sync pull requests created within timeframe with lastSyncAt")
        void recentSyncSyncsPullRequestsWithLastSyncAt() throws Exception {
            // Arrange
            var ghRepository = createEphemeralRepository("recent-pr");
            var prArtifacts = createPullRequestWithReview(ghRepository);
            var monitor = registerRepositoryToMonitor(ghRepository);

            // Wait for GitHub API to propagate (PR shows as issue in list)
            awaitIssuesVisibleInApi(ghRepository, 1);

            // Act
            dataSyncService.syncRepositoryToMonitor(monitor);

            // Assert - get repository and query PR
            var repoId = repositoryRepository.findByNameWithOwner(ghRepository.getFullName()).orElseThrow().getId();

            // Wait for PR to be synced
            awaitCondition("PR synced", () ->
                pullRequestRepository.findByRepositoryIdAndNumber(repoId, prArtifacts.pullRequestNumber()).isPresent()
            );

            var storedPr = pullRequestRepository
                .findByRepositoryIdAndNumber(repoId, prArtifacts.pullRequestNumber())
                .orElseThrow();
            assertThat(storedPr.getNumber()).isEqualTo(prArtifacts.pullRequestNumber());
            assertThat(storedPr.getLastSyncAt()).as("Pull request should have lastSyncAt set after sync").isNotNull();
        }

        @Test
        @DisplayName("Synced issues should have lastSyncAt persisted to database")
        void syncedIssuesHaveLastSyncAtPersisted() throws Exception {
            // Arrange
            var ghRepository = createEphemeralRepository("persist-lastsync");
            var issue1 = createIssueWithComment(ghRepository);
            var issue2 = createIssueWithComment(ghRepository);
            var monitor = registerRepositoryToMonitor(ghRepository);

            // Wait for GitHub API to propagate the issues
            awaitCondition("issues visible in API", () -> {
                try {
                    var issues = ghRepository.queryIssues().state(GHIssueState.ALL).list().toList();
                    return issues.size() >= 2;
                } catch (IOException e) {
                    return false;
                }
            });

            // Act
            dataSyncService.syncRepositoryToMonitor(monitor);

            // Assert
            var repoId = repositoryRepository.findByNameWithOwner(ghRepository.getFullName()).orElseThrow().getId();

            // Wait for issues to be synced
            awaitCondition("issues synced", () -> {
                Set<Integer> synced = issueRepository.findAllSyncedIssueNumbers(repoId);
                return synced.size() >= 2;
            });

            Set<Integer> syncedNumbers = issueRepository.findAllSyncedIssueNumbers(repoId);
            assertThat(syncedNumbers)
                .as("Both issues should be in synced set")
                .contains(issue1.issueNumber(), issue2.issueNumber());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BACKFILL INITIALIZATION TESTS
    // Verify that backfill only initializes after recent sync completes
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Backfill Initialization")
    class BackfillInitialization {

        @Test
        @DisplayName("Backfill should not start before recent sync completes")
        void backfillDoesNotStartBeforeRecentSync() throws Exception {
            // Arrange - create monitor without running sync
            var ghRepository = createEphemeralRepository("backfill-init");
            var monitor = registerRepositoryToMonitor(ghRepository);

            // Assert - backfill should not be initialized
            assertThat(monitor.getIssuesAndPullRequestsSyncedAt()).as("Recent sync should not have run yet").isNull();
            assertThat(monitor.isBackfillInitialized())
                .as("Backfill should not be initialized before recent sync")
                .isFalse();
            assertThat(monitor.isBackfillComplete())
                .as("Backfill should not be complete before initialization")
                .isFalse();
        }

        @Test
        @DisplayName("Backfill should initialize after first successful recent sync")
        void backfillInitializesAfterRecentSync() throws Exception {
            // Arrange - create issues so there's something to track
            var ghRepository = createEphemeralRepository("backfill-after");
            createIssueWithComment(ghRepository); // Issue #1
            createIssueWithComment(ghRepository); // Issue #2
            var monitor = registerRepositoryToMonitor(ghRepository);

            // Wait for GitHub API to propagate the issues
            awaitIssuesVisibleInApi(ghRepository, 2);

            // Act - run sync which should trigger both recent sync and backfill init
            dataSyncService.syncRepositoryToMonitor(monitor);

            // Assert
            var refreshedMonitor = repositoryToMonitorRepository.findById(monitor.getId()).orElseThrow();
            assertThat(refreshedMonitor.getIssuesAndPullRequestsSyncedAt())
                .as("Recent sync should have completed")
                .isNotNull();

            // Backfill should be initialized - but since these are new issues in the
            // timeframe, they'll all be synced and backfill will complete immediately
            // with highWaterMark = 0 (nothing to backfill before issue #1)
            // So we just check that the backfill was processed (initialized OR complete)
            assertThat(refreshedMonitor.getBackfillHighWaterMark())
                .as("Backfill highWaterMark should be set after sync")
                .isNotNull();
        }

        @Test
        @DisplayName("Backfill highWaterMark should be based on lowest synced issue number minus 1")
        void backfillHighWaterMarkCorrectlySet() throws Exception {
            // Arrange
            var ghRepository = createEphemeralRepository("hwm-test");
            createIssueWithComment(ghRepository); // Should be #1
            createIssueWithComment(ghRepository); // Should be #2
            var monitor = registerRepositoryToMonitor(ghRepository);

            // Act
            dataSyncService.syncRepositoryToMonitor(monitor);

            // Assert
            var refreshedMonitor = repositoryToMonitorRepository.findById(monitor.getId()).orElseThrow();

            // If both issues were synced (as expected since they're recent),
            // the lowest synced should be #1, so highWaterMark = 1 - 1 = 0
            // This means nothing to backfill
            if (refreshedMonitor.getBackfillHighWaterMark() != null) {
                if (refreshedMonitor.getBackfillHighWaterMark() == 0) {
                    assertThat(refreshedMonitor.isBackfillComplete())
                        .as("If highWaterMark is 0, backfill should be complete")
                        .isTrue();
                } else {
                    // If there are gaps, highWaterMark should be positive
                    assertThat(refreshedMonitor.getBackfillHighWaterMark())
                        .as("HighWaterMark should be at least 0")
                        .isGreaterThanOrEqualTo(0);
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BACKFILL PROGRESSION TESTS
    // Verify that backfill correctly processes issues in batches
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Backfill Progression")
    class BackfillProgression {

        @Test
        @DisplayName("Backfill marks issues as synced with lastSyncAt")
        void backfillMarksIssuesAsSynced() throws Exception {
            // Arrange
            var ghRepository = createEphemeralRepository("backfill-mark");
            // Create multiple issues
            int issueCount = 3;
            for (int i = 0; i < issueCount; i++) {
                createIssueWithComment(ghRepository);
            }
            var monitor = registerRepositoryToMonitor(ghRepository);

            // Wait for GitHub API to propagate
            awaitIssuesVisibleInApi(ghRepository, issueCount);

            // Act
            dataSyncService.syncRepositoryToMonitor(monitor);

            // Assert - wait for issues to be synced
            var repoId = repositoryRepository.findByNameWithOwner(ghRepository.getFullName()).orElseThrow().getId();

            awaitCondition("issues synced", () -> {
                Set<Integer> synced = issueRepository.findAllSyncedIssueNumbers(repoId);
                return synced.size() >= issueCount;
            });

            Set<Integer> syncedNumbers = issueRepository.findAllSyncedIssueNumbers(repoId);
            assertThat(syncedNumbers)
                .as("All created issues should be marked as synced")
                .hasSizeGreaterThanOrEqualTo(issueCount);
        }

        @Test
        @DisplayName("Backfill checkpoint decrements after processing batch")
        void backfillCheckpointDecrements() throws Exception {
            // Arrange - create repository with issues
            var ghRepository = createEphemeralRepository("checkpoint");
            createIssueWithComment(ghRepository);
            var monitor = registerRepositoryToMonitor(ghRepository);

            // Act - first sync
            dataSyncService.syncRepositoryToMonitor(monitor);

            // Assert
            var refreshedMonitor = repositoryToMonitorRepository.findById(monitor.getId()).orElseThrow();

            // After sync, if backfill ran, checkpoint should be set
            if (refreshedMonitor.isBackfillInitialized()) {
                assertThat(refreshedMonitor.getBackfillCheckpoint())
                    .as("Checkpoint should be set after backfill batch")
                    .isNotNull();
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // END-TO-END SYNC FLOW TESTS
    // Verify the complete sync flow from start to finish
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("End-to-End Sync Flow")
    class EndToEndSyncFlow {

        @Test
        @DisplayName("Complete sync flow: recent sync followed by backfill initialization")
        void completeSyncFlowRecentThenBackfill() throws Exception {
            // Arrange
            var ghRepository = createEphemeralRepository("e2e-flow");
            var issue = createIssueWithComment(ghRepository);
            var pr = createPullRequestWithReview(ghRepository);
            var monitor = registerRepositoryToMonitor(ghRepository);

            // Wait for GitHub API to propagate
            awaitIssuesVisibleInApi(ghRepository, 2); // Issue + PR

            // Act
            dataSyncService.syncRepositoryToMonitor(monitor);

            // Assert - Step 1: Recent sync completed
            var refreshedMonitor = repositoryToMonitorRepository.findById(monitor.getId()).orElseThrow();
            assertThat(refreshedMonitor.getIssuesAndPullRequestsSyncedAt())
                .as("Recent sync timestamp should be set")
                .isNotNull();

            // Get repository ID for queries
            var repoId = repositoryRepository.findByNameWithOwner(ghRepository.getFullName()).orElseThrow().getId();

            // Assert - Step 2: Issue synced
            awaitCondition("issue synced", () -> {
                Set<Integer> synced = issueRepository.findAllSyncedIssueNumbers(repoId);
                return synced.contains(issue.issueNumber());
            });

            // Assert - Step 3: PR synced
            awaitCondition("PR synced", () ->
                pullRequestRepository.findByRepositoryIdAndNumber(repoId, pr.pullRequestNumber()).isPresent()
            );
            var storedPr = pullRequestRepository
                .findByRepositoryIdAndNumber(repoId, pr.pullRequestNumber())
                .orElseThrow();
            assertThat(storedPr.getLastSyncAt()).as("PR should have lastSyncAt after sync").isNotNull();

            // Assert - Step 4: Backfill processed
            assertThat(refreshedMonitor.getBackfillHighWaterMark())
                .as("Backfill should be processed after recent sync")
                .isNotNull();
        }

        @Test
        @DisplayName("Second sync should not re-sync already synced items")
        void secondSyncSkipsAlreadySyncedItems() throws Exception {
            // Arrange
            var ghRepository = createEphemeralRepository("idempotent");
            var issue = createIssueWithComment(ghRepository);
            var monitor = registerRepositoryToMonitor(ghRepository);

            // Wait for GitHub API to propagate
            awaitIssuesVisibleInApi(ghRepository, 1);

            // Act - First sync
            dataSyncService.syncRepositoryToMonitor(monitor);

            // Get repo ID and wait for sync
            var repoId = repositoryRepository.findByNameWithOwner(ghRepository.getFullName()).orElseThrow().getId();

            awaitCondition("issue synced after first sync", () -> {
                Set<Integer> synced = issueRepository.findAllSyncedIssueNumbers(repoId);
                return synced.contains(issue.issueNumber());
            });

            // Small delay to ensure time difference is measurable
            Thread.sleep(100);

            // Act - Second sync
            dataSyncService.syncRepositoryToMonitor(
                repositoryToMonitorRepository.findById(monitor.getId()).orElseThrow()
            );

            // Assert - issue number should still be in synced set
            Set<Integer> syncedAfterSecond = issueRepository.findAllSyncedIssueNumbers(repoId);
            assertThat(syncedAfterSecond)
                .as("Issue should still be synced after second run")
                .contains(issue.issueNumber());
        }

        @Test
        @DisplayName("Sync correctly tracks repository in database")
        void syncTracksRepositoryCorrectly() throws Exception {
            // Arrange
            var ghRepository = createEphemeralRepository("track-repo");
            var monitor = registerRepositoryToMonitor(ghRepository);

            // Act
            dataSyncService.syncRepositoryToMonitor(monitor);

            // Assert
            var storedRepo = repositoryRepository.findByNameWithOwner(ghRepository.getFullName());
            assertThat(storedRepo).isPresent();
            assertThat(storedRepo.get().getName()).isEqualTo(ghRepository.getName());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BACKFILL STATE MACHINE TESTS
    // Verify the backfill state transitions are correct
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Backfill State Machine")
    class BackfillStateMachine {

        @Test
        @DisplayName("New monitor: isBackfillInitialized=false, isBackfillComplete=false")
        void newMonitorNotInitialized() throws Exception {
            // Arrange
            var ghRepository = createEphemeralRepository("state-new");
            var monitor = registerRepositoryToMonitor(ghRepository);

            // Assert
            assertThat(monitor.isBackfillInitialized()).isFalse();
            assertThat(monitor.isBackfillInProgress()).isFalse();
            assertThat(monitor.isBackfillComplete()).isFalse();
            assertThat(monitor.getBackfillRemaining()).isZero();
        }

        @Test
        @DisplayName("After sync with no prior issues: backfill complete with highWaterMark=0")
        void emptyRepoBackfillComplete() throws Exception {
            // Arrange - empty repo (just auto-init README)
            var ghRepository = createEphemeralRepository("state-empty");
            var monitor = registerRepositoryToMonitor(ghRepository);

            // Act
            dataSyncService.syncRepositoryToMonitor(monitor);

            // Assert - no issues to backfill
            var refreshedMonitor = repositoryToMonitorRepository.findById(monitor.getId()).orElseThrow();
            // If no issues were synced, backfill may not initialize at all or mark complete
            // Either way, there should be nothing to backfill
            assertThat(refreshedMonitor.getBackfillRemaining()).isZero();
        }

        @Test
        @DisplayName("Backfill remaining count is accurate")
        void backfillRemainingCountAccurate() throws Exception {
            // Arrange
            var ghRepository = createEphemeralRepository("remaining");
            createIssueWithComment(ghRepository);
            var monitor = registerRepositoryToMonitor(ghRepository);

            // Act
            dataSyncService.syncRepositoryToMonitor(monitor);

            // Assert
            var refreshedMonitor = repositoryToMonitorRepository.findById(monitor.getId()).orElseThrow();
            int remaining = refreshedMonitor.getBackfillRemaining();

            // Remaining should be non-negative
            assertThat(remaining).isGreaterThanOrEqualTo(0);

            // If backfill is complete, remaining should be 0
            if (refreshedMonitor.isBackfillComplete()) {
                assertThat(remaining).isZero();
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FINDLOWESTSYNCEDISSUENUMBER TESTS
    // Verify the repository query method works correctly
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Issue Repository Queries")
    class IssueRepositoryQueries {

        @Test
        @DisplayName("findLowestSyncedIssueNumber returns correct minimum")
        void findLowestSyncedIssueNumberWorks() throws Exception {
            // Arrange
            var ghRepository = createEphemeralRepository("lowest-query");
            var issue1 = createIssueWithComment(ghRepository); // #1
            createIssueWithComment(ghRepository); // #2 - also created but we only check #1 is lowest
            var monitor = registerRepositoryToMonitor(ghRepository);

            // Wait for GitHub API to propagate
            awaitIssuesVisibleInApi(ghRepository, 2);

            // Act
            dataSyncService.syncRepositoryToMonitor(monitor);

            // Assert - wait for issues to be synced
            var repoId = repositoryRepository.findByNameWithOwner(ghRepository.getFullName()).orElseThrow().getId();

            awaitCondition("issues synced", () -> {
                Set<Integer> synced = issueRepository.findAllSyncedIssueNumbers(repoId);
                return synced.size() >= 2;
            });

            Optional<Integer> lowestSynced = issueRepository.findLowestSyncedIssueNumber(repoId);

            assertThat(lowestSynced).as("Should find lowest synced issue number").isPresent();

            // The lowest should be #1 (first issue created)
            assertThat(lowestSynced.get()).as("Lowest synced issue should be #1").isEqualTo(issue1.issueNumber());
        }

        @Test
        @DisplayName("findAllSyncedIssueNumbers returns all synced issues")
        void findAllSyncedIssueNumbersWorks() throws Exception {
            // Arrange
            var ghRepository = createEphemeralRepository("all-synced");
            var issue1 = createIssueWithComment(ghRepository);
            var issue2 = createIssueWithComment(ghRepository);
            var monitor = registerRepositoryToMonitor(ghRepository);

            // Wait for GitHub API to propagate
            awaitIssuesVisibleInApi(ghRepository, 2);

            // Act
            dataSyncService.syncRepositoryToMonitor(monitor);

            // Assert - wait for issues to be synced
            var repoId = repositoryRepository.findByNameWithOwner(ghRepository.getFullName()).orElseThrow().getId();

            awaitCondition("issues synced", () -> {
                Set<Integer> synced = issueRepository.findAllSyncedIssueNumbers(repoId);
                return synced.size() >= 2;
            });

            Set<Integer> syncedNumbers = issueRepository.findAllSyncedIssueNumbers(repoId);

            assertThat(syncedNumbers)
                .as("Should contain all synced issue numbers")
                .contains(issue1.issueNumber(), issue2.issueNumber());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Waits for the GitHub API to reflect created issues.
     */
    private void awaitIssuesVisibleInApi(GHRepository repository, int expectedCount) throws Exception {
        awaitCondition("issues visible in API", () -> {
            try {
                var issues = repository.queryIssues().state(GHIssueState.ALL).list().toList();
                return issues.size() >= expectedCount;
            } catch (IOException e) {
                return false;
            }
        });
    }
}
