package de.tum.in.www1.hephaestus.gitprovider.sync.backfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.commit.github.GitHubInlineCommitSyncService;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.AuthMode;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.BackfillStateProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncContextProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncSession;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncTarget;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.GitHubIssueProcessor;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.github.GitHubIssueCommentProcessor;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.GitHubPullRequestProcessor;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github.GitHubPullRequestReviewProcessor;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github.GitHubPullRequestReviewSyncService;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github.GitHubPullRequestReviewCommentSyncService;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.sync.SyncSchedulerProperties;
import de.tum.in.www1.hephaestus.gitprovider.sync.SyncSchedulerProperties.BackfillProperties;
import de.tum.in.www1.hephaestus.gitprovider.sync.SyncSchedulerProperties.FilterProperties;
import de.tum.in.www1.hephaestus.gitprovider.sync.backfill.HistoricalBackfillService.BackfillCycleResult;
import de.tum.in.www1.hephaestus.gitprovider.sync.backfill.HistoricalBackfillService.BackfillProgress;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Unit tests for {@link HistoricalBackfillService}.
 *
 * <p>Tests the backfill orchestration logic including enable/disable gating,
 * per-repository skip conditions (incremental sync pending, cooldown, rate limits,
 * already complete), and progress tracking records.
 */
class HistoricalBackfillServiceTest extends BaseUnitTest {

    @Mock
    private SyncTargetProvider syncTargetProvider;

    @Mock
    private BackfillStateProvider backfillStateProvider;

    @Mock
    private GitHubGraphQlClientProvider graphQlClientProvider;

    @Mock
    private GitHubIssueProcessor issueProcessor;

    @Mock
    private GitHubIssueCommentProcessor issueCommentProcessor;

    @Mock
    private GitHubPullRequestProcessor prProcessor;

    @Mock
    private GitHubPullRequestReviewProcessor reviewProcessor;

    @Mock
    private GitHubPullRequestReviewSyncService reviewSyncService;

    @Mock
    private GitHubPullRequestReviewCommentSyncService reviewCommentSyncService;

    @Mock
    private GitHubInlineCommitSyncService inlineCommitSyncService;

    @Mock
    private RepositoryRepository repositoryRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private HttpGraphQlClient client;

    private HistoricalBackfillService service;

    private static final Long SCOPE_ID = 100L;
    private static final Long INSTALLATION_ID = 200L;
    private static final Long SYNC_TARGET_ID_A = 1L;
    private static final Long SYNC_TARGET_ID_B = 2L;
    private static final Long SYNC_TARGET_ID_C = 3L;

    private GitHubSyncProperties syncProperties;
    private SyncSchedulerProperties enabledSchedulerProperties;
    private SyncSchedulerProperties disabledSchedulerProperties;

    @BeforeEach
    void setUp() {
        syncProperties = new GitHubSyncProperties(
            Duration.ofSeconds(30),
            Duration.ofSeconds(60),
            Duration.ofSeconds(120),
            Duration.ZERO, // no throttle in tests
            true,
            Duration.ofMinutes(5),
            10
        );

        enabledSchedulerProperties = new SyncSchedulerProperties(
            true,
            7,
            "0 0 3 * * *",
            15,
            new BackfillProperties(true, 50, 100, 60),
            new FilterProperties(Set.of(), Set.of(), Set.of())
        );

        disabledSchedulerProperties = new SyncSchedulerProperties(
            true,
            7,
            "0 0 3 * * *",
            15,
            new BackfillProperties(false, 50, 100, 60),
            new FilterProperties(Set.of(), Set.of(), Set.of())
        );

        // TransactionTemplate: execute callbacks immediately
        lenient()
            .when(transactionTemplate.execute(any()))
            .thenAnswer(invocation -> {
                TransactionCallback<?> callback = invocation.getArgument(0);
                return callback.doInTransaction(null);
            });
    }

    // ═══════════════════════════════════════════════════════════════
    // Test construction helpers
    // ═══════════════════════════════════════════════════════════════

    private HistoricalBackfillService createService(SyncSchedulerProperties schedulerProps) {
        return new HistoricalBackfillService(
            syncTargetProvider,
            backfillStateProvider,
            schedulerProps,
            syncProperties,
            graphQlClientProvider,
            issueProcessor,
            issueCommentProcessor,
            prProcessor,
            reviewProcessor,
            reviewSyncService,
            reviewCommentSyncService,
            inlineCommitSyncService,
            repositoryRepository,
            transactionTemplate,
            Runnable::run // synchronous executor for tests
        );
    }

    /**
     * Creates a SyncTarget with completed incremental sync and no backfill progress.
     */
    private SyncTarget createTargetWithIncrementalComplete(Long id, String repoName) {
        return new SyncTarget(
            id,
            SCOPE_ID,
            INSTALLATION_ID,
            null,
            AuthMode.GITHUB_APP,
            repoName,
            Instant.now(), // lastLabelsSyncedAt
            Instant.now(), // lastMilestonesSyncedAt
            Instant.now(), // lastIssuesSyncedAt (completed)
            Instant.now(), // lastPullRequestsSyncedAt (completed)
            Instant.now(), // lastCollaboratorsSyncedAt
            Instant.now(), // lastFullSyncAt
            null, // issueBackfillHighWaterMark (not initialized)
            null, // issueBackfillCheckpoint
            null, // pullRequestBackfillHighWaterMark (not initialized)
            null, // pullRequestBackfillCheckpoint
            null, // backfillLastRunAt
            null, // issueSyncCursor
            null // pullRequestSyncCursor
        );
    }

    /**
     * Creates a SyncTarget where incremental sync has NOT completed yet.
     */
    private SyncTarget createTargetPendingIncrementalSync(Long id, String repoName) {
        return new SyncTarget(
            id,
            SCOPE_ID,
            INSTALLATION_ID,
            null,
            AuthMode.GITHUB_APP,
            repoName,
            null, // lastLabelsSyncedAt
            null, // lastMilestonesSyncedAt
            null, // lastIssuesSyncedAt (NOT completed)
            null, // lastPullRequestsSyncedAt (NOT completed)
            null, // lastCollaboratorsSyncedAt
            null, // lastFullSyncAt
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    /**
     * Creates a SyncTarget where backfill is already complete.
     */
    private SyncTarget createTargetWithBackfillComplete(Long id, String repoName) {
        return new SyncTarget(
            id,
            SCOPE_ID,
            INSTALLATION_ID,
            null,
            AuthMode.GITHUB_APP,
            repoName,
            Instant.now(), // lastLabelsSyncedAt
            Instant.now(), // lastMilestonesSyncedAt
            Instant.now(), // lastIssuesSyncedAt
            Instant.now(), // lastPullRequestsSyncedAt
            Instant.now(), // lastCollaboratorsSyncedAt
            Instant.now(), // lastFullSyncAt
            100, // issueBackfillHighWaterMark
            0, // issueBackfillCheckpoint = 0 means complete
            50, // pullRequestBackfillHighWaterMark
            0, // pullRequestBackfillCheckpoint = 0 means complete
            Instant.now(),
            null,
            null
        );
    }

    /**
     * Creates a SyncTarget with backfill in progress (partially complete).
     */
    private SyncTarget createTargetWithBackfillInProgress(Long id, String repoName) {
        return new SyncTarget(
            id,
            SCOPE_ID,
            INSTALLATION_ID,
            null,
            AuthMode.GITHUB_APP,
            repoName,
            Instant.now(), // lastLabelsSyncedAt
            Instant.now(), // lastMilestonesSyncedAt
            Instant.now(), // lastIssuesSyncedAt (completed)
            Instant.now(), // lastPullRequestsSyncedAt (completed)
            Instant.now(), // lastCollaboratorsSyncedAt
            Instant.now(), // lastFullSyncAt
            100, // issueBackfillHighWaterMark
            42, // issueBackfillCheckpoint (in progress)
            50, // pullRequestBackfillHighWaterMark
            25, // pullRequestBackfillCheckpoint (in progress)
            Instant.now().minusSeconds(120),
            "cursor-issue-page-3", // issueSyncCursor
            "cursor-pr-page-2" // pullRequestSyncCursor
        );
    }

    private SyncSession createSession(List<SyncTarget> targets) {
        return new SyncSession(
            SCOPE_ID,
            "test-workspace",
            "Test Workspace",
            "test-org",
            INSTALLATION_ID,
            targets,
            new SyncContextProvider.SyncContext(SCOPE_ID, "test-workspace", "Test Workspace", INSTALLATION_ID)
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // isEnabled
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("isEnabled")
    class IsEnabled {

        @Test
        void shouldReturnTrueWhenBackfillEnabled() {
            // Arrange
            service = createService(enabledSchedulerProperties);

            // Act & Assert
            assertThat(service.isEnabled()).isTrue();
        }

        @Test
        void shouldReturnFalseWhenBackfillDisabled() {
            // Arrange
            service = createService(disabledSchedulerProperties);

            // Act & Assert
            assertThat(service.isEnabled()).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // runBackfillCycle - disabled / no work
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("runBackfillCycle")
    class RunBackfillCycle {

        @Test
        void shouldReturnNothingToDoWhenDisabled() {
            // Arrange
            service = createService(disabledSchedulerProperties);

            // Act
            BackfillCycleResult result = service.runBackfillCycle();

            // Assert
            assertThat(result.repositoriesProcessed()).isZero();
            assertThat(result.pendingRepositories()).isZero();
            assertThat(result.skipReason()).isNull();
        }

        @Test
        void shouldReturnNothingToDoWhenNoSessions() {
            // Arrange
            service = createService(enabledSchedulerProperties);
            when(syncTargetProvider.getSyncSessions()).thenReturn(List.of());

            // Act
            BackfillCycleResult result = service.runBackfillCycle();

            // Assert
            assertThat(result.repositoriesProcessed()).isZero();
            assertThat(result.pendingRepositories()).isZero();
            assertThat(result.skipReason()).isNull();
        }

        @Test
        void shouldSkipScopeWhenRateLimitBelowThreshold() {
            // Arrange
            service = createService(enabledSchedulerProperties);
            SyncTarget target = createTargetWithIncrementalComplete(SYNC_TARGET_ID_A, "org/repo-a");
            SyncSession session = createSession(List.of(target));
            when(syncTargetProvider.getSyncSessions()).thenReturn(List.of(session));
            // Rate limit below threshold (100)
            when(graphQlClientProvider.getRateLimitRemaining(SCOPE_ID)).thenReturn(50);
            when(graphQlClientProvider.getRateLimitResetAt(SCOPE_ID)).thenReturn(Instant.now().plusSeconds(300));

            // Act
            BackfillCycleResult result = service.runBackfillCycle();

            // Assert
            assertThat(result.repositoriesProcessed()).isZero();
            assertThat(result.pendingRepositories()).isEqualTo(1);
        }

        @Test
        void shouldSkipScopeWhenRateLimitBelowThresholdButNotCountCompleteRepos() {
            // Arrange
            service = createService(enabledSchedulerProperties);
            SyncTarget completeTarget = createTargetWithBackfillComplete(SYNC_TARGET_ID_A, "org/repo-a");
            SyncTarget incompleteTarget = createTargetWithIncrementalComplete(SYNC_TARGET_ID_B, "org/repo-b");
            SyncSession session = createSession(List.of(completeTarget, incompleteTarget));
            when(syncTargetProvider.getSyncSessions()).thenReturn(List.of(session));
            when(graphQlClientProvider.getRateLimitRemaining(SCOPE_ID)).thenReturn(50);
            when(graphQlClientProvider.getRateLimitResetAt(SCOPE_ID)).thenReturn(Instant.now().plusSeconds(300));

            // Act
            BackfillCycleResult result = service.runBackfillCycle();

            // Assert - only the incomplete target should be counted as pending
            assertThat(result.repositoriesProcessed()).isZero();
            assertThat(result.pendingRepositories()).isEqualTo(1);
        }

        @Test
        void shouldSkipRepoWhenBackfillAlreadyComplete() {
            // Arrange
            service = createService(enabledSchedulerProperties);
            SyncTarget completeTarget = createTargetWithBackfillComplete(SYNC_TARGET_ID_A, "org/repo-a");
            SyncSession session = createSession(List.of(completeTarget));
            when(syncTargetProvider.getSyncSessions()).thenReturn(List.of(session));
            when(graphQlClientProvider.getRateLimitRemaining(SCOPE_ID)).thenReturn(5000);

            // Act
            BackfillCycleResult result = service.runBackfillCycle();

            // Assert - no work, no pending (complete repos are silently skipped)
            assertThat(result.repositoriesProcessed()).isZero();
            assertThat(result.pendingRepositories()).isZero();
        }

        @Test
        void shouldSkipRepoWhenIncrementalSyncPendingAndCountAsPending() {
            // Arrange
            service = createService(enabledSchedulerProperties);
            SyncTarget pendingTarget = createTargetPendingIncrementalSync(SYNC_TARGET_ID_A, "org/repo-a");
            SyncSession session = createSession(List.of(pendingTarget));
            when(syncTargetProvider.getSyncSessions()).thenReturn(List.of(session));
            // Rate limit is checked before per-repo iteration; stub it above threshold
            // so we reach the per-repo incremental sync gate
            when(graphQlClientProvider.getRateLimitRemaining(SCOPE_ID)).thenReturn(5000);

            // Act
            BackfillCycleResult result = service.runBackfillCycle();

            // Assert - repo is skipped per-repo because lastIssuesAndPullRequestsSyncedAt == null
            assertThat(result.repositoriesProcessed()).isZero();
            assertThat(result.pendingRepositories()).isEqualTo(1);
        }

        @Test
        @DisplayName("Per-repo gate: repos with pending incremental sync are skipped individually")
        void shouldSkipEntireScopeWhenAnyRepoPendingIncrementalSync() {
            // Arrange
            service = createService(enabledSchedulerProperties);

            SyncTarget repoA = createTargetWithIncrementalComplete(SYNC_TARGET_ID_A, "org/repo-a");
            SyncTarget repoB = createTargetPendingIncrementalSync(SYNC_TARGET_ID_B, "org/repo-b");
            SyncSession session = createSession(List.of(repoA, repoB));

            when(syncTargetProvider.getSyncSessions()).thenReturn(List.of(session));
            // Rate limit is always checked; stub above threshold to reach per-repo iteration
            when(graphQlClientProvider.getRateLimitRemaining(SCOPE_ID)).thenReturn(5000);
            // repoA passes incremental check, enters backfillRepository but not found in DB
            when(repositoryRepository.findByNameWithOwner("org/repo-a")).thenReturn(Optional.empty());

            // Act
            BackfillCycleResult result = service.runBackfillCycle();

            // Assert - repoA: incremental done, attempted backfill but not in DB (not counted)
            //          repoB: skipped per-repo due to pending incremental sync (counted as pending)
            assertThat(result.pendingRepositories()).isEqualTo(1);
            assertThat(result.repositoriesProcessed()).isZero();
        }

        @Test
        void shouldBreakScopeLoopWhenRateLimitDropsBelowThresholdMidLoop() {
            // Arrange
            service = createService(enabledSchedulerProperties);

            SyncTarget repoA = createTargetWithIncrementalComplete(SYNC_TARGET_ID_A, "org/repo-a");
            SyncTarget repoB = createTargetWithIncrementalComplete(SYNC_TARGET_ID_B, "org/repo-b");
            SyncSession session = createSession(List.of(repoA, repoB));

            when(syncTargetProvider.getSyncSessions()).thenReturn(List.of(session));
            // First call: above threshold (scope-level check passes)
            // Second call (re-check before repo A): still above threshold
            // Third call (re-check before repo B): below threshold
            when(graphQlClientProvider.getRateLimitRemaining(SCOPE_ID))
                .thenReturn(5000)
                .thenReturn(5000)
                .thenReturn(50);
            // Note: forScope is NOT called because backfillRepository returns false
            // (repo not in DB) before reaching the GraphQL client
            when(repositoryRepository.findByNameWithOwner("org/repo-a")).thenReturn(Optional.empty());

            // Act
            BackfillCycleResult result = service.runBackfillCycle();

            // Assert - repo B should be counted as pending due to rate limit break
            assertThat(result.pendingRepositories()).isEqualTo(1);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // backfillRepository (package-private)
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("backfillRepository")
    class BackfillRepository {

        @BeforeEach
        void setUpService() {
            service = createService(enabledSchedulerProperties);
        }

        @Test
        void shouldReturnFalseWhenRepoNameIsInvalid() {
            // Arrange
            SyncTarget target = new SyncTarget(
                SYNC_TARGET_ID_A,
                SCOPE_ID,
                INSTALLATION_ID,
                null,
                AuthMode.GITHUB_APP,
                "invalid-repo-name", // no owner/name separator
                null,
                null,
                Instant.now(), // lastIssuesSyncedAt
                Instant.now(), // lastPullRequestsSyncedAt
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            );

            // Act
            boolean result = service.backfillRepository(target, 50);

            // Assert
            assertThat(result).isFalse();
        }

        @Test
        void shouldReturnFalseWhenRepositoryNotInDatabase() {
            // Arrange
            SyncTarget target = createTargetWithIncrementalComplete(SYNC_TARGET_ID_A, "org/repo-a");
            when(repositoryRepository.findByNameWithOwner("org/repo-a")).thenReturn(Optional.empty());

            // Act
            boolean result = service.backfillRepository(target, 50);

            // Assert
            assertThat(result).isFalse();
            verify(graphQlClientProvider, never()).forScope(anyLong());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // BackfillCycleResult
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("BackfillCycleResult")
    class BackfillCycleResultTests {

        @Test
        void shouldCreateNothingToDoResult() {
            // Act
            BackfillCycleResult result = BackfillCycleResult.nothingToDo();

            // Assert
            assertThat(result.repositoriesProcessed()).isZero();
            assertThat(result.pendingRepositories()).isZero();
            assertThat(result.skipReason()).isNull();
        }

        @Test
        void shouldCreateResultWithValues() {
            // Act
            BackfillCycleResult result = new BackfillCycleResult(5, 3, "rateLimitLow");

            // Assert
            assertThat(result.repositoriesProcessed()).isEqualTo(5);
            assertThat(result.pendingRepositories()).isEqualTo(3);
            assertThat(result.skipReason()).isEqualTo("rateLimitLow");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // BackfillProgress
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("BackfillProgress")
    class BackfillProgressTests {

        @Test
        void shouldCreateFromSyncTargetNotStarted() {
            // Arrange
            SyncTarget target = createTargetWithIncrementalComplete(SYNC_TARGET_ID_A, "org/repo-a");

            // Act
            BackfillProgress progress = BackfillProgress.fromSyncTarget(target);

            // Assert
            assertThat(progress.repositoryName()).isEqualTo("org/repo-a");
            assertThat(progress.isInitialized()).isFalse();
            assertThat(progress.isComplete()).isFalse();
            assertThat(progress.lastRunAt()).isNull();
            assertThat(progress.issueCursor()).isNull();
            assertThat(progress.prCursor()).isNull();
        }

        @Test
        void shouldCreateFromSyncTargetComplete() {
            // Arrange
            SyncTarget target = createTargetWithBackfillComplete(SYNC_TARGET_ID_A, "org/repo-a");

            // Act
            BackfillProgress progress = BackfillProgress.fromSyncTarget(target);

            // Assert
            assertThat(progress.repositoryName()).isEqualTo("org/repo-a");
            assertThat(progress.isInitialized()).isTrue();
            assertThat(progress.isComplete()).isTrue();
            assertThat(progress.lastRunAt()).isNotNull();
        }

        @Test
        void shouldCreateFromSyncTargetInProgress() {
            // Arrange
            SyncTarget target = createTargetWithBackfillInProgress(SYNC_TARGET_ID_A, "org/repo-a");

            // Act
            BackfillProgress progress = BackfillProgress.fromSyncTarget(target);

            // Assert
            assertThat(progress.repositoryName()).isEqualTo("org/repo-a");
            assertThat(progress.isInitialized()).isTrue();
            assertThat(progress.isComplete()).isFalse();
            assertThat(progress.lastRunAt()).isNotNull();
            assertThat(progress.issueCursor()).isEqualTo("cursor-issue-page-3");
            assertThat(progress.prCursor()).isEqualTo("cursor-pr-page-2");
        }

        @Test
        void shouldReturnCompleteSummary() {
            // Arrange
            SyncTarget target = createTargetWithBackfillComplete(SYNC_TARGET_ID_A, "org/repo-a");
            BackfillProgress progress = BackfillProgress.fromSyncTarget(target);

            // Act
            String summary = progress.summary();

            // Assert
            assertThat(summary).isEqualTo("Backfill complete for org/repo-a");
        }

        @Test
        void shouldReturnNotStartedSummary() {
            // Arrange
            SyncTarget target = createTargetWithIncrementalComplete(SYNC_TARGET_ID_A, "org/repo-a");
            BackfillProgress progress = BackfillProgress.fromSyncTarget(target);

            // Act
            String summary = progress.summary();

            // Assert
            assertThat(summary).isEqualTo("Backfill not started for org/repo-a");
        }

        @Test
        void shouldReturnInProgressSummary() {
            // Arrange
            SyncTarget target = createTargetWithBackfillInProgress(SYNC_TARGET_ID_A, "org/repo-a");
            BackfillProgress progress = BackfillProgress.fromSyncTarget(target);

            // Act
            String summary = progress.summary();

            // Assert
            assertThat(summary).startsWith("Backfill in progress for org/repo-a (last run: ");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SyncTarget convenience methods
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SyncTarget backfill state methods")
    class SyncTargetBackfillState {

        @Test
        void shouldReportBackfillNotInitializedWhenNoHighWaterMark() {
            // Arrange
            SyncTarget target = createTargetWithIncrementalComplete(SYNC_TARGET_ID_A, "org/repo-a");

            // Assert
            assertThat(target.isIssueBackfillInitialized()).isFalse();
            assertThat(target.isPullRequestBackfillInitialized()).isFalse();
            assertThat(target.isBackfillInitialized()).isFalse();
            assertThat(target.isBackfillComplete()).isFalse();
        }

        @Test
        void shouldReportBackfillCompleteWhenCheckpointsAreZero() {
            // Arrange
            SyncTarget target = createTargetWithBackfillComplete(SYNC_TARGET_ID_A, "org/repo-a");

            // Assert
            assertThat(target.isIssueBackfillInitialized()).isTrue();
            assertThat(target.isPullRequestBackfillInitialized()).isTrue();
            assertThat(target.isBackfillInitialized()).isTrue();
            assertThat(target.isIssueBackfillComplete()).isTrue();
            assertThat(target.isPullRequestBackfillComplete()).isTrue();
            assertThat(target.isBackfillComplete()).isTrue();
        }

        @Test
        void shouldReportBackfillInProgressWhenCheckpointsNonZero() {
            // Arrange
            SyncTarget target = createTargetWithBackfillInProgress(SYNC_TARGET_ID_A, "org/repo-a");

            // Assert
            assertThat(target.isBackfillInitialized()).isTrue();
            assertThat(target.isBackfillComplete()).isFalse();
            assertThat(target.getIssueBackfillRemaining()).isEqualTo(42);
            assertThat(target.getPullRequestBackfillRemaining()).isEqualTo(25);
            assertThat(target.getBackfillRemaining()).isEqualTo(67);
        }

        @Test
        void shouldReportZeroRemainingWhenNotInitialized() {
            // Arrange
            SyncTarget target = createTargetWithIncrementalComplete(SYNC_TARGET_ID_A, "org/repo-a");

            // Assert
            assertThat(target.getIssueBackfillRemaining()).isZero();
            assertThat(target.getPullRequestBackfillRemaining()).isZero();
            assertThat(target.getBackfillRemaining()).isZero();
        }

        @Test
        void shouldReportZeroRemainingWhenHighWaterMarkIsZero() {
            // Arrange - a repo with zero issues/PRs
            SyncTarget target = new SyncTarget(
                SYNC_TARGET_ID_A,
                SCOPE_ID,
                INSTALLATION_ID,
                null,
                AuthMode.GITHUB_APP,
                "org/empty-repo",
                null, // lastLabelsSyncedAt
                null, // lastMilestonesSyncedAt
                Instant.now(), // lastIssuesSyncedAt
                Instant.now(), // lastPullRequestsSyncedAt
                null, // lastCollaboratorsSyncedAt
                null, // lastFullSyncAt
                0, // issueBackfillHighWaterMark = 0 (no issues)
                0, // issueBackfillCheckpoint = 0
                0, // pullRequestBackfillHighWaterMark = 0 (no PRs)
                0, // pullRequestBackfillCheckpoint = 0
                Instant.now(),
                null,
                null
            );

            // Assert
            assertThat(target.isIssueBackfillComplete()).isTrue();
            assertThat(target.isPullRequestBackfillComplete()).isTrue();
            assertThat(target.isBackfillComplete()).isTrue();
            assertThat(target.getIssueBackfillRemaining()).isZero();
            assertThat(target.getPullRequestBackfillRemaining()).isZero();
        }

        @Test
        void shouldReportHighWaterMarkAsRemainingWhenCheckpointIsNull() {
            // Arrange - initialized but checkpoint not yet set (first batch hasn't completed)
            SyncTarget target = new SyncTarget(
                SYNC_TARGET_ID_A,
                SCOPE_ID,
                INSTALLATION_ID,
                null,
                AuthMode.GITHUB_APP,
                "org/repo-a",
                null, // lastLabelsSyncedAt
                null, // lastMilestonesSyncedAt
                Instant.now(), // lastIssuesSyncedAt
                Instant.now(), // lastPullRequestsSyncedAt
                null, // lastCollaboratorsSyncedAt
                null, // lastFullSyncAt
                200, // issueBackfillHighWaterMark
                null, // issueBackfillCheckpoint (not yet set)
                80, // pullRequestBackfillHighWaterMark
                null, // pullRequestBackfillCheckpoint (not yet set)
                null,
                null,
                null
            );

            // Assert - remaining should be the high water mark itself
            assertThat(target.getIssueBackfillRemaining()).isEqualTo(200);
            assertThat(target.getPullRequestBackfillRemaining()).isEqualTo(80);
            assertThat(target.getBackfillRemaining()).isEqualTo(280);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // getProgress
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getProgress")
    class GetProgress {

        @Test
        void shouldReturnProgressWhenTargetExists() {
            // Arrange
            service = createService(enabledSchedulerProperties);
            SyncTarget target = createTargetWithBackfillInProgress(SYNC_TARGET_ID_A, "org/repo-a");
            when(syncTargetProvider.findSyncTargetById(SYNC_TARGET_ID_A)).thenReturn(Optional.of(target));

            // Act
            Optional<BackfillProgress> progress = service.getProgress(SYNC_TARGET_ID_A);

            // Assert
            assertThat(progress).isPresent();
            assertThat(progress.get().repositoryName()).isEqualTo("org/repo-a");
            assertThat(progress.get().isInitialized()).isTrue();
            assertThat(progress.get().isComplete()).isFalse();
        }

        @Test
        void shouldReturnEmptyWhenTargetNotFound() {
            // Arrange
            service = createService(enabledSchedulerProperties);
            when(syncTargetProvider.findSyncTargetById(999L)).thenReturn(Optional.empty());

            // Act
            Optional<BackfillProgress> progress = service.getProgress(999L);

            // Assert
            assertThat(progress).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Multiple sessions
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("runBackfillCycle with multiple sessions")
    class MultipleSessions {

        @Test
        void shouldProcessMultipleSessionsIndependently() {
            // Arrange
            service = createService(enabledSchedulerProperties);

            Long scopeId2 = 200L;
            Long installationId2 = 300L;

            SyncTarget targetInScope1 = createTargetPendingIncrementalSync(SYNC_TARGET_ID_A, "org/repo-a");
            SyncTarget targetInScope2 = new SyncTarget(
                SYNC_TARGET_ID_B,
                scopeId2,
                installationId2,
                null,
                AuthMode.GITHUB_APP,
                "org2/repo-b",
                null,
                null,
                null, // lastIssuesSyncedAt
                null, // lastPullRequestsSyncedAt
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            );

            SyncSession session1 = createSession(List.of(targetInScope1));
            SyncSession session2 = new SyncSession(
                scopeId2,
                "workspace-2",
                "Workspace 2",
                "org2",
                installationId2,
                List.of(targetInScope2),
                new SyncContextProvider.SyncContext(scopeId2, "workspace-2", "Workspace 2", installationId2)
            );

            when(syncTargetProvider.getSyncSessions()).thenReturn(List.of(session1, session2));
            // Rate limit is always checked per scope; stub above threshold to reach per-repo iteration
            when(graphQlClientProvider.getRateLimitRemaining(SCOPE_ID)).thenReturn(5000);
            when(graphQlClientProvider.getRateLimitRemaining(scopeId2)).thenReturn(5000);

            // Act
            BackfillCycleResult result = service.runBackfillCycle();

            // Assert - both repos have pending incremental sync, so they are skipped per-repo
            // and counted as pending
            assertThat(result.repositoriesProcessed()).isZero();
            assertThat(result.pendingRepositories()).isEqualTo(2);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Mixed target states in single session
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("runBackfillCycle with mixed target states")
    class MixedTargetStates {

        @Test
        @DisplayName("Should skip pending-incremental repos per-repo and process eligible ones")
        void shouldHandleMixedStatesCorrectly() {
            // Arrange
            service = createService(enabledSchedulerProperties);

            SyncTarget completeRepo = createTargetWithBackfillComplete(SYNC_TARGET_ID_A, "org/repo-complete");
            SyncTarget pendingRepo = createTargetPendingIncrementalSync(SYNC_TARGET_ID_B, "org/repo-pending");
            SyncTarget eligibleRepo = createTargetWithIncrementalComplete(SYNC_TARGET_ID_C, "org/repo-eligible");

            SyncSession session = createSession(List.of(completeRepo, pendingRepo, eligibleRepo));
            when(syncTargetProvider.getSyncSessions()).thenReturn(List.of(session));
            // Rate limit is always checked; stub above threshold to reach per-repo iteration
            when(graphQlClientProvider.getRateLimitRemaining(SCOPE_ID)).thenReturn(5000);
            // eligibleRepo passes incremental check, enters backfillRepository but not in DB
            when(repositoryRepository.findByNameWithOwner("org/repo-eligible")).thenReturn(Optional.empty());

            // Act
            BackfillCycleResult result = service.runBackfillCycle();

            // Assert:
            //   completeRepo: backfill complete, silently skipped (not counted)
            //   pendingRepo: incremental sync pending, skipped per-repo, counted as pending
            //   eligibleRepo: attempted backfill but not in DB, returns false (not counted)
            assertThat(result.pendingRepositories()).isEqualTo(1);
            assertThat(result.repositoriesProcessed()).isZero();
        }

        @Test
        @DisplayName("Should process eligible repos and skip complete ones when all have completed incremental sync")
        void shouldProcessEligibleAndSkipCompleteWhenAllIncrementalDone() {
            // Arrange
            service = createService(enabledSchedulerProperties);

            SyncTarget completeRepo = createTargetWithBackfillComplete(SYNC_TARGET_ID_A, "org/repo-complete");
            SyncTarget eligibleRepo = createTargetWithIncrementalComplete(SYNC_TARGET_ID_B, "org/repo-eligible");

            SyncSession session = createSession(List.of(completeRepo, eligibleRepo));
            when(syncTargetProvider.getSyncSessions()).thenReturn(List.of(session));
            when(graphQlClientProvider.getRateLimitRemaining(SCOPE_ID)).thenReturn(5000);
            // eligibleRepo enters the per-repo loop and calls backfillRepository
            when(repositoryRepository.findByNameWithOwner("org/repo-eligible")).thenReturn(Optional.empty());

            // Act
            BackfillCycleResult result = service.runBackfillCycle();

            // Assert
            // completeRepo: backfill complete → silently skipped (not pending, not processed)
            // eligibleRepo: attempted backfill but not in DB → backfillRepository returns false
            //   (not counted as processed, not counted as pending)
            assertThat(result.repositoriesProcessed()).isZero();
            assertThat(result.pendingRepositories()).isZero();
        }
    }
}
