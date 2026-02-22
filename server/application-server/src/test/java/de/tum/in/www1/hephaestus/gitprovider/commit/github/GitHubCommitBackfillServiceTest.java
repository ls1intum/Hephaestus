package de.tum.in.www1.hephaestus.gitprovider.commit.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.commit.Commit;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitAuthorResolver;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.AuthMode;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncTarget;
import de.tum.in.www1.hephaestus.gitprovider.git.GitRepositoryManager;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("GitHubCommitBackfillService")
class GitHubCommitBackfillServiceTest extends BaseUnitTest {

    @Mock
    private GitRepositoryManager gitRepositoryManager;

    @Mock
    private GitHubAppTokenService tokenService;

    @Mock
    private CommitRepository commitRepository;

    @Mock
    private CommitAuthorResolver authorResolver;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private TransactionTemplate transactionTemplate;

    private GitHubCommitBackfillService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        // Make TransactionTemplate execute callbacks directly (no actual transaction)
        lenient()
            .when(transactionTemplate.execute(any(TransactionCallback.class)))
            .thenAnswer(invocation -> {
                TransactionCallback<Object> callback = invocation.getArgument(0);
                return callback.doInTransaction(null);
            });

        service = new GitHubCommitBackfillService(
            gitRepositoryManager,
            tokenService,
            commitRepository,
            authorResolver,
            eventPublisher,
            transactionTemplate
        );
    }

    // ========== Helpers ==========

    private static SyncTarget createSyncTarget(AuthMode authMode) {
        return new SyncTarget(
            1L,
            100L,
            authMode == AuthMode.GITHUB_APP ? 42L : null,
            authMode == AuthMode.PERSONAL_ACCESS_TOKEN ? "ghp_test_token" : null,
            authMode,
            "owner/repo",
            null,
            null,
            null,
            null,
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
    }

    private static Repository createMockRepository(Long id, String nameWithOwner, String defaultBranch) {
        Repository repo = mock(Repository.class);
        lenient().when(repo.getId()).thenReturn(id);
        lenient().when(repo.getNameWithOwner()).thenReturn(nameWithOwner);
        lenient().when(repo.getDefaultBranch()).thenReturn(defaultBranch);
        return repo;
    }

    private static GitRepositoryManager.CommitInfo createCommitInfo(String sha, String message) {
        return new GitRepositoryManager.CommitInfo(
            sha,
            message,
            null,
            "Author",
            "author@test.com",
            Instant.parse("2024-01-15T10:00:00Z"),
            "Committer",
            "committer@test.com",
            Instant.parse("2024-01-15T10:00:00Z"),
            10,
            5,
            1,
            List.of(
                new GitRepositoryManager.FileChange(
                    "src/Main.java",
                    GitRepositoryManager.ChangeType.MODIFIED,
                    10,
                    5,
                    15,
                    null
                )
            )
        );
    }

    private static Commit createMockCommit(String sha, Long repoId) {
        Commit commit = mock(Commit.class);
        lenient().when(commit.getSha()).thenReturn(sha);
        lenient().when(commit.getId()).thenReturn(1L);
        lenient().when(commit.getMessage()).thenReturn("test");
        lenient().when(commit.getAuthoredAt()).thenReturn(Instant.parse("2024-01-15T10:00:00Z"));
        lenient().when(commit.getAdditions()).thenReturn(0);
        lenient().when(commit.getDeletions()).thenReturn(0);
        lenient().when(commit.getChangedFiles()).thenReturn(0);

        Repository mockRepo = mock(Repository.class);
        lenient().when(mockRepo.getId()).thenReturn(repoId);
        lenient().when(commit.getRepository()).thenReturn(mockRepo);

        return commit;
    }

    // ========== Tests ==========

    @Nested
    @DisplayName("skip conditions")
    class SkipConditions {

        @Test
        @DisplayName("should return -1 when git checkout is disabled")
        void shouldReturnNegativeOneWhenDisabled() {
            when(gitRepositoryManager.isEnabled()).thenReturn(false);
            Repository repo = createMockRepository(1L, "owner/repo", "main");
            SyncTarget target = createSyncTarget(AuthMode.GITHUB_APP);

            int result = service.backfillCommits(target, repo, 100L);

            assertThat(result).isEqualTo(-1);
            verify(gitRepositoryManager, never()).ensureRepository(anyLong(), anyString(), any());
        }

        @Test
        @DisplayName("should return -1 when default branch is null")
        void shouldReturnNegativeOneWhenDefaultBranchNull() {
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            Repository repo = createMockRepository(1L, "owner/repo", null);
            SyncTarget target = createSyncTarget(AuthMode.GITHUB_APP);

            int result = service.backfillCommits(target, repo, 100L);

            assertThat(result).isEqualTo(-1);
            verify(gitRepositoryManager, never()).ensureRepository(anyLong(), anyString(), any());
        }

        @Test
        @DisplayName("should return -1 when default branch is blank")
        void shouldReturnNegativeOneWhenDefaultBranchBlank() {
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            Repository repo = createMockRepository(1L, "owner/repo", "  ");
            SyncTarget target = createSyncTarget(AuthMode.GITHUB_APP);

            int result = service.backfillCommits(target, repo, 100L);

            assertThat(result).isEqualTo(-1);
        }

        @Test
        @DisplayName("should return -1 when HEAD cannot be resolved")
        void shouldReturnNegativeOneWhenHeadUnresolvable() {
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(gitRepositoryManager.resolveDefaultBranchHead(1L, "main")).thenReturn(null);
            Repository repo = createMockRepository(1L, "owner/repo", "main");
            SyncTarget target = createSyncTarget(AuthMode.GITHUB_APP);

            int result = service.backfillCommits(target, repo, 100L);

            assertThat(result).isEqualTo(-1);
        }

        @Test
        @DisplayName("should return 0 when HEAD equals latest known SHA")
        void shouldReturnZeroWhenAlreadyUpToDate() {
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(gitRepositoryManager.resolveDefaultBranchHead(1L, "main")).thenReturn("abc123");

            Commit latestCommit = createMockCommit("abc123", 1L);
            when(commitRepository.findLatestByRepositoryId(1L)).thenReturn(Optional.of(latestCommit));

            Repository repo = createMockRepository(1L, "owner/repo", "main");
            SyncTarget target = createSyncTarget(AuthMode.GITHUB_APP);

            int result = service.backfillCommits(target, repo, 100L);

            assertThat(result).isEqualTo(0);
            verify(gitRepositoryManager, never()).walkCommits(anyLong(), any(), anyString());
        }
    }

    @Nested
    @DisplayName("full backfill (no previous commits)")
    class FullBackfill {

        @Test
        @DisplayName("should walk all commits when no previous commits exist")
        void shouldWalkAllCommitsWhenNoPreviousCommits() {
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(gitRepositoryManager.resolveDefaultBranchHead(1L, "main")).thenReturn("head123");
            when(commitRepository.findLatestByRepositoryId(1L)).thenReturn(Optional.empty());

            GitRepositoryManager.CommitInfo commitInfo = createCommitInfo("commit1", "First commit");
            when(gitRepositoryManager.walkCommits(1L, null, "head123")).thenReturn(List.of(commitInfo));

            // Not already persisted
            when(commitRepository.existsByShaAndRepositoryId("commit1", 1L)).thenReturn(false);

            // Mock the commit lookup after upsert for event publishing
            Commit mockCommit = createMockCommit("commit1", 1L);
            when(commitRepository.findByShaAndRepositoryId("commit1", 1L)).thenReturn(Optional.of(mockCommit));

            Repository repo = createMockRepository(1L, "owner/repo", "main");
            SyncTarget target = createSyncTarget(AuthMode.GITHUB_APP);

            int result = service.backfillCommits(target, repo, 100L);

            assertThat(result).isEqualTo(1);
            // fromSha should be null (full walk)
            verify(gitRepositoryManager).walkCommits(1L, null, "head123");
            verify(commitRepository).upsertCommit(
                eq("commit1"),
                eq("First commit"),
                any(),
                eq("https://github.com/owner/repo/commit/commit1"),
                any(),
                any(),
                eq(10),
                eq(5),
                eq(1),
                any(),
                eq(1L),
                any(),
                any(),
                any(),
                any()
            );
        }

        @Test
        @DisplayName("should publish CommitCreated event for new commits")
        void shouldPublishCommitCreatedEvent() {
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(gitRepositoryManager.resolveDefaultBranchHead(1L, "main")).thenReturn("head123");
            when(commitRepository.findLatestByRepositoryId(1L)).thenReturn(Optional.empty());

            GitRepositoryManager.CommitInfo commitInfo = createCommitInfo("commit1", "First commit");
            when(gitRepositoryManager.walkCommits(1L, null, "head123")).thenReturn(List.of(commitInfo));
            when(commitRepository.existsByShaAndRepositoryId("commit1", 1L)).thenReturn(false);

            Commit mockCommit = createMockCommit("commit1", 1L);
            when(commitRepository.findByShaAndRepositoryId("commit1", 1L)).thenReturn(Optional.of(mockCommit));

            Repository repo = createMockRepository(1L, "owner/repo", "main");
            SyncTarget target = createSyncTarget(AuthMode.GITHUB_APP);

            service.backfillCommits(target, repo, 100L);

            ArgumentCaptor<DomainEvent.CommitCreated> eventCaptor = ArgumentCaptor.forClass(
                DomainEvent.CommitCreated.class
            );
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            DomainEvent.CommitCreated event = eventCaptor.getValue();
            assertThat(event.context().scopeId()).isEqualTo(100L);
        }
    }

    @Nested
    @DisplayName("incremental backfill")
    class IncrementalBackfill {

        @Test
        @DisplayName("should walk from latest known SHA when previous commits exist")
        void shouldWalkFromLatestKnownSha() {
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(gitRepositoryManager.resolveDefaultBranchHead(1L, "main")).thenReturn("head456");

            Commit latestCommit = createMockCommit("prev123", 1L);
            when(commitRepository.findLatestByRepositoryId(1L)).thenReturn(Optional.of(latestCommit));

            GitRepositoryManager.CommitInfo newCommit = createCommitInfo("head456", "New commit");
            when(gitRepositoryManager.walkCommits(1L, "prev123", "head456")).thenReturn(List.of(newCommit));

            when(commitRepository.existsByShaAndRepositoryId("head456", 1L)).thenReturn(false);
            Commit mockCommit = createMockCommit("head456", 1L);
            when(commitRepository.findByShaAndRepositoryId("head456", 1L)).thenReturn(Optional.of(mockCommit));

            Repository repo = createMockRepository(1L, "owner/repo", "main");
            SyncTarget target = createSyncTarget(AuthMode.GITHUB_APP);

            int result = service.backfillCommits(target, repo, 100L);

            assertThat(result).isEqualTo(1);
            // fromSha should be the latest known SHA
            verify(gitRepositoryManager).walkCommits(1L, "prev123", "head456");
        }
    }

    @Nested
    @DisplayName("duplicate handling")
    class DuplicateHandling {

        @Test
        @DisplayName("should skip commits that already exist")
        void shouldSkipExistingCommits() {
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(gitRepositoryManager.resolveDefaultBranchHead(1L, "main")).thenReturn("head123");
            when(commitRepository.findLatestByRepositoryId(1L)).thenReturn(Optional.empty());

            GitRepositoryManager.CommitInfo existingCommit = createCommitInfo("existing", "Old commit");
            GitRepositoryManager.CommitInfo newCommit = createCommitInfo("newone", "New commit");
            when(gitRepositoryManager.walkCommits(1L, null, "head123")).thenReturn(List.of(existingCommit, newCommit));

            // First commit already exists, second does not
            when(commitRepository.existsByShaAndRepositoryId("existing", 1L)).thenReturn(true);
            when(commitRepository.existsByShaAndRepositoryId("newone", 1L)).thenReturn(false);
            // Existing commit has correct file change count — no repair needed
            when(commitRepository.countFileChangesByShaAndRepositoryId("existing", 1L)).thenReturn(1);

            Commit mockCommit = createMockCommit("newone", 1L);
            when(commitRepository.findByShaAndRepositoryId("newone", 1L)).thenReturn(Optional.of(mockCommit));

            Repository repo = createMockRepository(1L, "owner/repo", "main");
            SyncTarget target = createSyncTarget(AuthMode.GITHUB_APP);

            int result = service.backfillCommits(target, repo, 100L);

            assertThat(result).isEqualTo(1);
            // Only the new commit should be upserted
            verify(commitRepository, times(1)).upsertCommit(
                eq("newone"),
                anyString(),
                any(),
                anyString(),
                any(),
                any(),
                any(Integer.class),
                any(Integer.class),
                any(Integer.class),
                any(),
                anyLong(),
                any(),
                any(),
                any(),
                any()
            );
        }

        @Test
        @DisplayName("should return 0 when walk returns empty list")
        void shouldReturnZeroWhenWalkReturnsEmpty() {
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(gitRepositoryManager.resolveDefaultBranchHead(1L, "main")).thenReturn("head123");
            when(commitRepository.findLatestByRepositoryId(1L)).thenReturn(Optional.empty());
            when(gitRepositoryManager.walkCommits(1L, null, "head123")).thenReturn(List.of());

            Repository repo = createMockRepository(1L, "owner/repo", "main");
            SyncTarget target = createSyncTarget(AuthMode.GITHUB_APP);

            int result = service.backfillCommits(target, repo, 100L);

            assertThat(result).isEqualTo(0);
            verify(commitRepository, never()).upsertCommit(
                anyString(),
                anyString(),
                any(),
                anyString(),
                any(),
                any(),
                any(Integer.class),
                any(Integer.class),
                any(Integer.class),
                any(),
                anyLong(),
                any(),
                any(),
                any(),
                any()
            );
        }
    }

    @Nested
    @DisplayName("authentication")
    class Authentication {

        @Test
        @DisplayName("should use installation token for GitHub App auth")
        void shouldUseInstallationTokenForGitHubApp() {
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(tokenService.isConfigured()).thenReturn(true);
            when(tokenService.getInstallationToken(42L)).thenReturn("ghs_install_token");
            when(gitRepositoryManager.resolveDefaultBranchHead(1L, "main")).thenReturn("head123");
            when(commitRepository.findLatestByRepositoryId(1L)).thenReturn(Optional.empty());
            when(gitRepositoryManager.walkCommits(1L, null, "head123")).thenReturn(List.of());

            Repository repo = createMockRepository(1L, "owner/repo", "main");
            SyncTarget target = createSyncTarget(AuthMode.GITHUB_APP);

            service.backfillCommits(target, repo, 100L);

            verify(gitRepositoryManager).ensureRepository(1L, "https://github.com/owner/repo.git", "ghs_install_token");
        }

        @Test
        @DisplayName("should use PAT for personal access token auth")
        void shouldUsePATForPersonalAccessTokenAuth() {
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(gitRepositoryManager.resolveDefaultBranchHead(1L, "main")).thenReturn("head123");
            when(commitRepository.findLatestByRepositoryId(1L)).thenReturn(Optional.empty());
            when(gitRepositoryManager.walkCommits(1L, null, "head123")).thenReturn(List.of());

            Repository repo = createMockRepository(1L, "owner/repo", "main");
            SyncTarget target = createSyncTarget(AuthMode.PERSONAL_ACCESS_TOKEN);

            service.backfillCommits(target, repo, 100L);

            verify(gitRepositoryManager).ensureRepository(1L, "https://github.com/owner/repo.git", "ghp_test_token");
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should return -1 on GitOperationException")
        void shouldReturnNegativeOneOnGitOperationException() {
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(gitRepositoryManager.ensureRepository(anyLong(), anyString(), any())).thenThrow(
                new GitRepositoryManager.GitOperationException("Clone failed", new RuntimeException())
            );

            Repository repo = createMockRepository(1L, "owner/repo", "main");
            SyncTarget target = createSyncTarget(AuthMode.GITHUB_APP);

            int result = service.backfillCommits(target, repo, 100L);

            assertThat(result).isEqualTo(-1);
        }

        @Test
        @DisplayName("should return -1 on unexpected exception")
        void shouldReturnNegativeOneOnUnexpectedException() {
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(gitRepositoryManager.ensureRepository(anyLong(), anyString(), any())).thenThrow(
                new RuntimeException("Unexpected error")
            );

            Repository repo = createMockRepository(1L, "owner/repo", "main");
            SyncTarget target = createSyncTarget(AuthMode.GITHUB_APP);

            int result = service.backfillCommits(target, repo, 100L);

            assertThat(result).isEqualTo(-1);
        }

        @Test
        @DisplayName("should return -1 when token service fails")
        void shouldReturnNegativeOneWhenTokenServiceFails() {
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(tokenService.isConfigured()).thenReturn(true);
            when(tokenService.getInstallationToken(42L)).thenThrow(new RuntimeException("Token error"));
            // Should still proceed with null token (token failure is not fatal)
            when(gitRepositoryManager.resolveDefaultBranchHead(1L, "main")).thenReturn("head123");
            when(commitRepository.findLatestByRepositoryId(1L)).thenReturn(Optional.empty());
            when(gitRepositoryManager.walkCommits(1L, null, "head123")).thenReturn(List.of());

            Repository repo = createMockRepository(1L, "owner/repo", "main");
            SyncTarget target = createSyncTarget(AuthMode.GITHUB_APP);

            int result = service.backfillCommits(target, repo, 100L);

            // Should succeed (token failure is logged, null token used)
            assertThat(result).isEqualTo(0);
            verify(gitRepositoryManager).ensureRepository(1L, "https://github.com/owner/repo.git", null);
        }
    }

    @Nested
    @DisplayName("user resolution")
    class UserResolution {

        @Test
        @DisplayName("should resolve author and committer IDs by email")
        void shouldResolveUserIdsByEmail() {
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(gitRepositoryManager.resolveDefaultBranchHead(1L, "main")).thenReturn("head123");
            when(commitRepository.findLatestByRepositoryId(1L)).thenReturn(Optional.empty());

            GitRepositoryManager.CommitInfo commitInfo = createCommitInfo("commit1", "Test commit");
            when(gitRepositoryManager.walkCommits(1L, null, "head123")).thenReturn(List.of(commitInfo));
            when(commitRepository.existsByShaAndRepositoryId("commit1", 1L)).thenReturn(false);

            when(authorResolver.resolveByEmail("author@test.com")).thenReturn(10L);
            when(authorResolver.resolveByEmail("committer@test.com")).thenReturn(20L);

            Commit mockCommit = createMockCommit("commit1", 1L);
            when(commitRepository.findByShaAndRepositoryId("commit1", 1L)).thenReturn(Optional.of(mockCommit));

            Repository repo = createMockRepository(1L, "owner/repo", "main");
            SyncTarget target = createSyncTarget(AuthMode.GITHUB_APP);

            service.backfillCommits(target, repo, 100L);

            verify(commitRepository).upsertCommit(
                eq("commit1"),
                anyString(),
                any(),
                anyString(),
                any(),
                any(),
                any(Integer.class),
                any(Integer.class),
                any(Integer.class),
                any(),
                eq(1L),
                eq(10L),
                eq(20L),
                any(),
                any()
            );
        }

        @Test
        @DisplayName("should pass null IDs when users not found")
        void shouldPassNullIdsWhenUsersNotFound() {
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(gitRepositoryManager.resolveDefaultBranchHead(1L, "main")).thenReturn("head123");
            when(commitRepository.findLatestByRepositoryId(1L)).thenReturn(Optional.empty());

            GitRepositoryManager.CommitInfo commitInfo = createCommitInfo("commit1", "Test commit");
            when(gitRepositoryManager.walkCommits(1L, null, "head123")).thenReturn(List.of(commitInfo));
            when(commitRepository.existsByShaAndRepositoryId("commit1", 1L)).thenReturn(false);

            when(authorResolver.resolveByEmail("author@test.com")).thenReturn(null);
            when(authorResolver.resolveByEmail("committer@test.com")).thenReturn(null);

            Commit mockCommit = createMockCommit("commit1", 1L);
            when(commitRepository.findByShaAndRepositoryId("commit1", 1L)).thenReturn(Optional.of(mockCommit));

            Repository repo = createMockRepository(1L, "owner/repo", "main");
            SyncTarget target = createSyncTarget(AuthMode.GITHUB_APP);

            service.backfillCommits(target, repo, 100L);

            verify(commitRepository).upsertCommit(
                eq("commit1"),
                anyString(),
                any(),
                anyString(),
                any(),
                any(),
                any(Integer.class),
                any(Integer.class),
                any(Integer.class),
                any(),
                eq(1L),
                eq(null),
                eq(null),
                any(),
                any()
            );
        }
    }

    @Nested
    @DisplayName("file changes")
    class FileChanges {

        @Test
        @DisplayName("should attach file changes to persisted commit")
        void shouldAttachFileChangesToPersistedCommit() {
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(gitRepositoryManager.resolveDefaultBranchHead(1L, "main")).thenReturn("head123");
            when(commitRepository.findLatestByRepositoryId(1L)).thenReturn(Optional.empty());

            GitRepositoryManager.CommitInfo commitInfo = createCommitInfo("commit1", "Test commit");
            when(gitRepositoryManager.walkCommits(1L, null, "head123")).thenReturn(List.of(commitInfo));
            when(commitRepository.existsByShaAndRepositoryId("commit1", 1L)).thenReturn(false);

            Repository repo = createMockRepository(1L, "owner/repo", "main");

            Commit mockCommit = mock(Commit.class);
            lenient().when(mockCommit.getSha()).thenReturn("commit1");
            lenient().when(mockCommit.getRepository()).thenReturn(repo);
            // findByShaAndRepositoryId called twice: once for file changes, once for event
            when(commitRepository.findByShaAndRepositoryId("commit1", 1L)).thenReturn(Optional.of(mockCommit));
            SyncTarget target = createSyncTarget(AuthMode.GITHUB_APP);

            service.backfillCommits(target, repo, 100L);

            // Verify file change was attached
            verify(mockCommit).addFileChange(any());
            verify(commitRepository).save(mockCommit);
        }
    }
}
