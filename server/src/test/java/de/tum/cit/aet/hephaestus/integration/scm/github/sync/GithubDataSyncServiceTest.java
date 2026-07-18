package de.tum.cit.aet.hephaestus.integration.scm.github.sync;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.framework.SyncSchedulerProperties;
import de.tum.cit.aet.hephaestus.integration.core.framework.SyncSchedulerProperties.BackfillProperties;
import de.tum.cit.aet.hephaestus.integration.core.framework.SyncSchedulerProperties.DiscussionsProperties;
import de.tum.cit.aet.hephaestus.integration.core.framework.SyncSchedulerProperties.FilterProperties;
import de.tum.cit.aet.hephaestus.integration.core.framework.SyncSchedulerProperties.ProjectsProperties;
import de.tum.cit.aet.hephaestus.integration.core.spi.AuthMode;
import de.tum.cit.aet.hephaestus.integration.core.spi.InstallationTokenProvider;
import de.tum.cit.aet.hephaestus.integration.core.spi.OrganizationMembershipListener;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncResult;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncTargetProvider;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncTargetProvider.SyncTarget;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncTargetTestBuilder;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.exception.RepositoryNotFoundOnGitProviderException;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.OrganizationRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.github.app.GitHubAppTokenService;
import de.tum.cit.aet.hephaestus.integration.scm.github.commit.CommitAuthorEnrichmentService;
import de.tum.cit.aet.hephaestus.integration.scm.github.commit.CommitMetadataEnrichmentService;
import de.tum.cit.aet.hephaestus.integration.scm.github.commit.GitHubCommitBackfillService;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubExceptionClassifier;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubExceptionClassifier.Category;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubExceptionClassifier.ClassificationResult;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.RateLimitTracker;
import de.tum.cit.aet.hephaestus.integration.scm.github.discussion.GitHubDiscussionSyncService;
import de.tum.cit.aet.hephaestus.integration.scm.github.issue.GitHubIssueSyncService;
import de.tum.cit.aet.hephaestus.integration.scm.github.issuedependency.GitHubIssueDependencySyncService;
import de.tum.cit.aet.hephaestus.integration.scm.github.issuetype.GitHubIssueTypeSyncService;
import de.tum.cit.aet.hephaestus.integration.scm.github.label.GitHubLabelSyncService;
import de.tum.cit.aet.hephaestus.integration.scm.github.milestone.GitHubMilestoneSyncService;
import de.tum.cit.aet.hephaestus.integration.scm.github.organization.GitHubOrganizationSyncService;
import de.tum.cit.aet.hephaestus.integration.scm.github.project.GitHubProjectSyncService;
import de.tum.cit.aet.hephaestus.integration.scm.github.pullrequest.GitHubPullRequestSyncService;
import de.tum.cit.aet.hephaestus.integration.scm.github.repository.GitHubRepositorySyncService;
import de.tum.cit.aet.hephaestus.integration.scm.github.repository.collaborator.GitHubCollaboratorSyncService;
import de.tum.cit.aet.hephaestus.integration.scm.github.subissue.GitHubSubIssueSyncService;
import de.tum.cit.aet.hephaestus.integration.scm.github.team.GitHubTeamSyncService;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link GithubDataSyncService#syncSyncTarget}, covering two data-loss
 * regressions in the incremental path:
 *
 * <ol>
 *   <li>A {@code repoUnchanged} short-circuit used to skip every sub-sync when
 *       {@code repository.updatedAt} had not advanced. GitHub does not bump that field when an
 *       issue/PR is opened or commented on, so new PRs were silently never ingested.</li>
 *   <li>The incremental path used to read and write {@code issueSyncCursor} /
 *       {@code pullRequestSyncCursor}, which are owned by the CREATED_AT-ordered historical
 *       backfill. Resuming an UPDATED_AT-ordered query from a CREATED_AT cursor skips the newest
 *       items and clobbers backfill's checkpoint.</li>
 * </ol>
 */
class GithubDataSyncServiceTest extends BaseUnitTest {

    private static final long SCOPE_ID = 1L;
    private static final long SYNC_TARGET_ID = 42L;
    private static final long REPOSITORY_ID = 100L;
    private static final long PROVIDER_ID = 7L;
    private static final String REPO_NAME = "acme/widgets";

    /** Frozen so "unchanged" means bit-identical, exactly as the removed short-circuit tested. */
    private static final Instant REPO_UPDATED_AT = Instant.parse("2026-07-01T00:00:00Z");

    @Mock
    private IdentityProviderRepository gitProviderRepository;

    @Mock
    private SyncTargetProvider syncTargetProvider;

    @Mock
    private OrganizationMembershipListener organizationMembershipListener;

    @Mock
    private RepositoryRepository repositoryRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private GitHubLabelSyncService labelSyncService;

    @Mock
    private GitHubMilestoneSyncService milestoneSyncService;

    @Mock
    private GitHubIssueSyncService issueSyncService;

    @Mock
    private GitHubIssueDependencySyncService issueDependencySyncService;

    @Mock
    private GitHubIssueTypeSyncService issueTypeSyncService;

    @Mock
    private GitHubSubIssueSyncService subIssueSyncService;

    @Mock
    private GitHubPullRequestSyncService pullRequestSyncService;

    @Mock
    private GitHubDiscussionSyncService discussionSyncService;

    @Mock
    private GitHubTeamSyncService teamSyncService;

    @Mock
    private GitHubProjectSyncService projectSyncService;

    @Mock
    private GitHubOrganizationSyncService organizationSyncService;

    @Mock
    private GitHubRepositorySyncService repositorySyncService;

    @Mock
    private GitHubCollaboratorSyncService collaboratorSyncService;

    @Mock
    private GitHubCommitBackfillService commitBackfillService;

    @Mock
    private CommitAuthorEnrichmentService commitAuthorEnrichmentService;

    @Mock
    private CommitMetadataEnrichmentService commitMetadataEnrichmentService;

    @Mock
    private GitHubExceptionClassifier exceptionClassifier;

    @Mock
    private InstallationTokenProvider tokenProvider;

    @Mock
    private GitHubAppTokenService gitHubAppTokenService;

    @Mock
    private RateLimitTracker rateLimitTracker;

    private GithubDataSyncService service;

    @BeforeEach
    void setUp() {
        SyncSchedulerProperties properties = new SyncSchedulerProperties(
            true,
            7,
            "0 0 3 * * *",
            15, // cooldownMinutes
            new BackfillProperties(false, 50, 100, 60),
            new FilterProperties(Set.of(), Set.of(), Set.of()),
            new DiscussionsProperties(false), // discussions off — keeps the test on the issue/PR path
            new ProjectsProperties(false)
        );

        service = new GithubDataSyncService(
            properties,
            gitProviderRepository,
            syncTargetProvider,
            organizationMembershipListener,
            repositoryRepository,
            organizationRepository,
            labelSyncService,
            milestoneSyncService,
            issueSyncService,
            issueDependencySyncService,
            issueTypeSyncService,
            subIssueSyncService,
            pullRequestSyncService,
            discussionSyncService,
            teamSyncService,
            projectSyncService,
            organizationSyncService,
            repositorySyncService,
            collaboratorSyncService,
            commitBackfillService,
            commitAuthorEnrichmentService,
            commitMetadataEnrichmentService,
            exceptionClassifier,
            tokenProvider,
            gitHubAppTokenService,
            rateLimitTracker,
            Runnable::run // synchronous executor — deterministic assertions
        );

        IdentityProvider provider = new IdentityProvider();
        ReflectionTestUtils.setField(provider, "id", PROVIDER_ID);

        Repository repository = new Repository();
        repository.setId(REPOSITORY_ID);
        repository.setNameWithOwner(REPO_NAME);
        repository.setProvider(provider);
        // The crux of bug A: GitHub leaves updatedAt untouched when a PR is opened, so a
        // re-synced repository reports the very same timestamp we already had stored.
        repository.setUpdatedAt(REPO_UPDATED_AT);

        when(syncTargetProvider.isScopeActiveForSync(SCOPE_ID)).thenReturn(true);
        when(
            gitProviderRepository.findByTypeAndServerUrl(IdentityProviderType.GITHUB, "https://github.com")
        ).thenReturn(Optional.of(provider));
        when(repositoryRepository.findByNameWithOwnerAndProviderId(REPO_NAME, PROVIDER_ID)).thenReturn(
            Optional.of(repository)
        );
        // Re-sync returns the same entity with an unchanged updatedAt. Lenient: the NOT_FOUND
        // rename/delete tests re-stub this to throw, which would otherwise flag this as unused.
        lenient()
            .when(repositorySyncService.syncRepository(SCOPE_ID, REPO_NAME, provider))
            .thenReturn(Optional.of(repository));

        lenient().when(commitBackfillService.backfillCommits(any(), any(), any())).thenReturn(0);
        lenient()
            .when(issueSyncService.syncForRepository(any(), any(), any(), any(), any()))
            .thenReturn(SyncResult.completed(3));
        lenient()
            .when(pullRequestSyncService.syncForRepository(any(), any(), any(), any(), any()))
            .thenReturn(SyncResult.completed(2));
    }

    /**
     * Builds a sync target that has already completed an initial issue/PR sync and whose
     * collaborator/label/milestone cooldowns are still warm, so the only remaining work is
     * issues and PRs.
     *
     * @param issueCursor       value for the backfill-owned issue cursor column
     * @param pullRequestCursor value for the backfill-owned PR cursor column
     */
    private static SyncTarget syncTarget(String issueCursor, String pullRequestCursor) {
        // All timestamps recent => within cooldown and initial sync "completed".
        Instant recent = Instant.now();
        return SyncTargetTestBuilder.syncTarget()
            .id(SYNC_TARGET_ID)
            .scopeId(SCOPE_ID)
            .installationId(100L)
            .authMode(AuthMode.INSTALLATION_APP)
            .repositoryNameWithOwner(REPO_NAME)
            .lastLabelsSyncedAt(recent)
            .lastMilestonesSyncedAt(recent)
            .lastIssuesSyncedAt(recent)
            .lastPullRequestsSyncedAt(recent)
            .lastDiscussionsSyncedAt(recent)
            .lastCollaboratorsSyncedAt(recent)
            .lastFullSyncAt(recent)
            .issueSyncCursor(issueCursor)
            .pullRequestSyncCursor(pullRequestCursor)
            .build();
    }

    private static final long NATIVE_ID = 555L;

    /** Same warm target as {@link #syncTarget}, with a stable provider {@code nativeId} attached. */
    private static SyncTarget syncTargetWithNativeId(Long nativeId) {
        Instant recent = Instant.now();
        return SyncTargetTestBuilder.syncTarget()
            .id(SYNC_TARGET_ID)
            .scopeId(SCOPE_ID)
            .installationId(100L)
            .authMode(AuthMode.INSTALLATION_APP)
            .repositoryNameWithOwner(REPO_NAME)
            .lastLabelsSyncedAt(recent)
            .lastMilestonesSyncedAt(recent)
            .lastIssuesSyncedAt(recent)
            .lastPullRequestsSyncedAt(recent)
            .lastDiscussionsSyncedAt(recent)
            .lastCollaboratorsSyncedAt(recent)
            .lastFullSyncAt(recent)
            .nativeId(nativeId)
            .build();
    }

    @Test
    void shouldPreserveSyncTargetWhenRenamedRepoHasStableIdOnNotFound() {
        // Arrange: the repo was renamed upstream. Its local row (found by the old name) still exists,
        // so metadata re-sync is attempted and GitHub answers a definitive 404 for the old name. Because
        // the monitor carries a stable native id, this is a rename — NOT a deletion.
        SyncTarget target = syncTargetWithNativeId(NATIVE_ID);
        when(repositorySyncService.syncRepository(eq(SCOPE_ID), eq(REPO_NAME), any())).thenThrow(
            new RepositoryNotFoundOnGitProviderException(REPO_NAME)
        );
        when(exceptionClassifier.classifyWithDetails(any())).thenReturn(
            ClassificationResult.of(Category.NOT_FOUND, "not found")
        );
        lenient().when(repositoryRepository.findById(REPOSITORY_ID)).thenReturn(Optional.empty());

        // Act
        boolean result = service.syncSyncTarget(target);

        // Assert: neither the monitor nor the repository is deleted (the confirmed data-loss cascade),
        // and the cycle reports not-completed so it retries.
        verify(syncTargetProvider, never()).removeSyncTarget(any());
        verify(repositoryRepository, never()).delete(any());
        org.assertj.core.api.Assertions.assertThat(result).isFalse();
    }

    @Test
    void shouldRemoveSyncTargetWhenLegacyRowHasNoStableIdOnNotFound() {
        // Arrange: a legacy monitor with no captured native id. A definitive 404 is treated as a real
        // deletion (unchanged pre-fix behavior), so the orphan is cleaned up.
        SyncTarget target = syncTargetWithNativeId(null);
        when(repositorySyncService.syncRepository(eq(SCOPE_ID), eq(REPO_NAME), any())).thenThrow(
            new RepositoryNotFoundOnGitProviderException(REPO_NAME)
        );
        when(exceptionClassifier.classifyWithDetails(any())).thenReturn(
            ClassificationResult.of(Category.NOT_FOUND, "not found")
        );
        lenient().when(repositoryRepository.findById(REPOSITORY_ID)).thenReturn(Optional.empty());

        // Act
        boolean result = service.syncSyncTarget(target);

        // Assert: the monitor is removed to stop perpetual retries for a genuinely deleted repository.
        verify(syncTargetProvider).removeSyncTarget(SYNC_TARGET_ID);
        org.assertj.core.api.Assertions.assertThat(result).isTrue();
    }

    @Test
    void shouldReconcileMonitorIdentityOnEverySync() {
        // The happy path backfills the monitor's stable id (and re-keys its name on divergence) so future
        // renames can be told apart from deletions.
        Repository resolved = new Repository();
        resolved.setId(REPOSITORY_ID);
        resolved.setNativeId(NATIVE_ID);
        resolved.setNameWithOwner(REPO_NAME);
        when(repositoryRepository.findByNameWithOwnerAndProviderId(REPO_NAME, PROVIDER_ID)).thenReturn(
            Optional.of(resolved)
        );

        service.syncSyncTarget(syncTarget(null, null));

        verify(syncTargetProvider).reconcileSyncTargetIdentity(SYNC_TARGET_ID, NATIVE_ID, REPO_NAME);
    }

    @Test
    void shouldSyncIssuesAndPullRequestsWhenRepositoryUpdatedAtUnchanged() {
        // Arrange: a repo that has completed its initial sync and whose updatedAt has not moved —
        // precisely the state in which the old short-circuit returned early and dropped PR #15.
        SyncTarget target = syncTarget(null, null);

        // Act
        boolean result = service.syncSyncTarget(target);

        // Assert: the sub-syncs still run rather than being skipped as "repoUnchanged".
        verify(issueSyncService).syncForRepository(eq(SCOPE_ID), eq(REPOSITORY_ID), isNull(), isNull(), any());
        verify(pullRequestSyncService).syncForRepository(eq(SCOPE_ID), eq(REPOSITORY_ID), isNull(), isNull(), any());
        org.assertj.core.api.Assertions.assertThat(result).isTrue();
    }

    @Test
    void shouldNotResumeIncrementalSyncFromBackfillCursor() {
        // Arrange: mid-backfill state — the shared cursor columns hold CREATED_AT-ordered
        // checkpoints belonging to GitHubHistoricalBackfillService.
        SyncTarget target = syncTarget("issue-cursor-created-at-desc", "pr-cursor-created-at-desc");

        // Act
        service.syncSyncTarget(target);

        // Assert: the UPDATED_AT-ordered incremental path starts fresh instead of resuming from a
        // cursor produced by a different ordering (which would skip the newest items).
        verify(issueSyncService).syncForRepository(eq(SCOPE_ID), eq(REPOSITORY_ID), isNull(), isNull(), any());
        verify(pullRequestSyncService).syncForRepository(eq(SCOPE_ID), eq(REPOSITORY_ID), isNull(), isNull(), any());
    }

    @Test
    void shouldNotClobberBackfillCursorWhenRunningIncrementalSync() {
        // Arrange
        SyncTarget target = syncTarget("issue-cursor-created-at-desc", "pr-cursor-created-at-desc");

        // Act
        service.syncSyncTarget(target);

        // Assert: passing a null syncTargetId disables cursor persistence inside the sync
        // services, so backfill's checkpoint survives an interleaved incremental run.
        verify(issueSyncService, never()).syncForRepository(any(), any(), eq(SYNC_TARGET_ID), any(), any());
        verify(pullRequestSyncService, never()).syncForRepository(any(), any(), eq(SYNC_TARGET_ID), any(), any());
    }
}
