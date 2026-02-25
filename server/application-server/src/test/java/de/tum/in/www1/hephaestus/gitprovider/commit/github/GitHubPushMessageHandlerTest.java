package de.tum.in.www1.hephaestus.gitprovider.commit.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.commit.CommitAuthorResolver;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ScopeIdResolver;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider;
import de.tum.in.www1.hephaestus.gitprovider.git.GitRepositoryManager;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.lang.reflect.Method;
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
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("GitHubPushMessageHandler")
class GitHubPushMessageHandlerTest extends BaseUnitTest {

    @Mock
    private GitRepositoryManager gitRepositoryManager;

    @Mock
    private GitHubAppTokenService tokenService;

    @Mock
    private RepositoryRepository repositoryRepository;

    @Mock
    private CommitRepository commitRepository;

    @Mock
    private CommitAuthorResolver authorResolver;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ScopeIdResolver scopeIdResolver;

    @Mock
    private SyncTargetProvider syncTargetProvider;

    @Mock
    private NatsMessageDeserializer deserializer;

    @Mock
    private TransactionTemplate transactionTemplate;

    private GitHubPushMessageHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GitHubPushMessageHandler(
            gitRepositoryManager,
            tokenService,
            repositoryRepository,
            commitRepository,
            authorResolver,
            eventPublisher,
            scopeIdResolver,
            syncTargetProvider,
            deserializer,
            transactionTemplate
        );
    }

    /**
     * Invokes the protected handleEvent method via reflection.
     */
    private void invokeHandleEvent(GitHubPushEventDTO event) throws Exception {
        Method handleEvent = GitHubPushMessageHandler.class.getDeclaredMethod("handleEvent", GitHubPushEventDTO.class);
        handleEvent.setAccessible(true);
        handleEvent.invoke(handler, event);
    }

    // ========== Test Data Builders ==========

    private static GitHubRepositoryRefDTO createRepoRef(Long id, String fullName) {
        return new GitHubRepositoryRefDTO(
            id,
            "node_" + id,
            fullName.split("/")[1],
            fullName,
            false,
            "https://github.com/" + fullName,
            null
        );
    }

    private static GitHubPushEventDTO.PushCommit createPushCommit(
        String sha,
        String message,
        List<String> added,
        List<String> modified,
        List<String> removed
    ) {
        return new GitHubPushEventDTO.PushCommit(
            sha,
            "tree123",
            message,
            Instant.parse("2024-01-15T10:30:00Z"),
            "https://github.com/owner/repo/commit/" + sha,
            new GitHubPushEventDTO.CommitUser("Author", "author@test.com", "authoruser"),
            new GitHubPushEventDTO.CommitUser("Committer", "committer@test.com", "committeruser"),
            added,
            removed,
            modified,
            true
        );
    }

    private static GitHubPushEventDTO createBasicPushEvent(
        String ref,
        boolean deleted,
        List<GitHubPushEventDTO.PushCommit> commits
    ) {
        return new GitHubPushEventDTO(
            ref,
            "abc123",
            "def456",
            false,
            deleted,
            false,
            "https://github.com/owner/repo/compare/abc123...def456",
            commits,
            commits != null && !commits.isEmpty() ? commits.get(commits.size() - 1) : null,
            createRepoRef(100L, "owner/repo"),
            new GitHubPushEventDTO.Pusher("pusher", "pusher@test.com"),
            new GitHubPushEventDTO.Sender(1L, "pusheruser"),
            new GitHubPushEventDTO.InstallationRef(42L, "node123")
        );
    }

    private Repository createMockRepository(Long id, String nameWithOwner, String defaultBranch) {
        Repository repo = mock(Repository.class, org.mockito.Mockito.withSettings().lenient());
        when(repo.getId()).thenReturn(id);
        when(repo.getNameWithOwner()).thenReturn(nameWithOwner);
        when(repo.getDefaultBranch()).thenReturn(defaultBranch);
        when(repo.getOrganization()).thenReturn(null);
        return repo;
    }

    /**
     * Sets up scope resolution mocks so that the handler considers the scope active.
     * Required for tests that exercise the local git processing path.
     */
    private void mockActiveScopeForRepo(String nameWithOwner) {
        when(scopeIdResolver.findScopeIdByRepositoryName(nameWithOwner)).thenReturn(Optional.of(1L));
        when(syncTargetProvider.isScopeActiveForSync(1L)).thenReturn(true);
    }

    @Nested
    @DisplayName("getEventType")
    class GetEventType {

        @Test
        @DisplayName("should return PUSH event type")
        void shouldReturnPushEventType() {
            GitHubEventType type = handler.getEventType();
            assertThat(type).isEqualTo(GitHubEventType.PUSH);
        }
    }

    @Nested
    @DisplayName("skip conditions")
    class SkipConditions {

        @Test
        @DisplayName("should skip branch deletion events")
        void shouldSkipBranchDeletionEvents() throws Exception {
            var event = createBasicPushEvent("refs/heads/feature", true, List.of());

            invokeHandleEvent(event);

            verify(repositoryRepository, never()).findByIdWithOrganization(anyLong());
            verify(commitRepository, never()).upsertCommit(
                anyString(),
                anyString(),
                any(),
                any(),
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
        @DisplayName("should skip events with null commits")
        void shouldSkipEventsWithNullCommits() throws Exception {
            var event = new GitHubPushEventDTO(
                "refs/heads/main",
                "abc123",
                "def456",
                false,
                false,
                false,
                "https://github.com/owner/repo/compare/abc123...def456",
                null,
                null,
                createRepoRef(100L, "owner/repo"),
                new GitHubPushEventDTO.Pusher("pusher", "pusher@test.com"),
                null,
                null
            );

            invokeHandleEvent(event);

            verify(repositoryRepository, never()).findByIdWithOrganization(anyLong());
        }

        @Test
        @DisplayName("should skip events with empty commits list")
        void shouldSkipEventsWithEmptyCommitsList() throws Exception {
            var event = createBasicPushEvent("refs/heads/main", false, List.of());

            invokeHandleEvent(event);

            verify(repositoryRepository, never()).findByIdWithOrganization(anyLong());
        }

        @Test
        @DisplayName("should skip when repository not found in database")
        void shouldSkipWhenRepositoryNotFoundInDatabase() throws Exception {
            var commit = createPushCommit("sha1", "message", List.of("file.txt"), List.of(), List.of());
            var event = createBasicPushEvent("refs/heads/main", false, List.of(commit));

            when(repositoryRepository.findByIdWithOrganization(100L)).thenReturn(Optional.empty());

            invokeHandleEvent(event);

            verify(commitRepository, never()).upsertCommit(
                anyString(),
                anyString(),
                any(),
                any(),
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
        @DisplayName("should skip when repository ref has null id")
        void shouldSkipWhenRepositoryRefHasNullId() throws Exception {
            var commit = createPushCommit("sha1", "message", List.of("file.txt"), List.of(), List.of());
            var event = new GitHubPushEventDTO(
                "refs/heads/main",
                "abc123",
                "def456",
                false,
                false,
                false,
                null,
                List.of(commit),
                commit,
                createRepoRef(null, "owner/repo"),
                new GitHubPushEventDTO.Pusher("pusher", "pusher@test.com"),
                null,
                null
            );

            invokeHandleEvent(event);

            verify(repositoryRepository, never()).findByIdWithOrganization(anyLong());
        }

        @Test
        @DisplayName("should skip when push is not to default branch")
        void shouldSkipWhenPushIsNotToDefaultBranch() throws Exception {
            var commit = createPushCommit("sha1", "message", List.of("file.txt"), List.of(), List.of());
            var event = createBasicPushEvent("refs/heads/feature-branch", false, List.of(commit));

            Repository repo = createMockRepository(100L, "owner/repo", "main");
            when(repositoryRepository.findByIdWithOrganization(100L)).thenReturn(Optional.of(repo));

            invokeHandleEvent(event);

            verify(commitRepository, never()).upsertCommit(
                anyString(),
                anyString(),
                any(),
                any(),
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
    @DisplayName("webhook processing")
    class WebhookProcessing {

        @Test
        @DisplayName("should process commits via webhook when git is disabled")
        void shouldProcessCommitsViaWebhookWhenGitIsDisabled() throws Exception {
            var commit = createPushCommit(
                "abc123def456789012345678901234567890abcd",
                "feat: add feature",
                List.of("newfile.txt"),
                List.of("existing.txt"),
                List.of()
            );
            var event = createBasicPushEvent("refs/heads/main", false, List.of(commit));

            Repository repo = createMockRepository(100L, "owner/repo", "main");
            when(repositoryRepository.findByIdWithOrganization(100L)).thenReturn(Optional.of(repo));
            when(gitRepositoryManager.isEnabled()).thenReturn(false);

            invokeHandleEvent(event);

            verify(commitRepository).upsertCommit(
                eq("abc123def456789012345678901234567890abcd"),
                eq("feat: add feature"),
                any(), // messageBody
                eq("https://github.com/owner/repo/commit/abc123def456789012345678901234567890abcd"),
                any(Instant.class), // authoredAt
                any(Instant.class), // committedAt
                eq(0), // additions (not available from webhook)
                eq(0), // deletions (not available from webhook)
                eq(2), // changedFiles = 1 added + 1 modified
                any(Instant.class), // lastSyncAt
                eq(100L),
                any(), // authorId
                any(), // committerId
                any(), // authorEmail
                any() // committerEmail
            );
        }

        @Test
        @DisplayName("should process multiple commits")
        void shouldProcessMultipleCommits() throws Exception {
            var commit1 = createPushCommit(
                "sha1aabbccdd112233445566778899aabbccddeeff",
                "first",
                List.of("f1.txt"),
                List.of(),
                List.of()
            );
            var commit2 = createPushCommit(
                "sha2aabbccdd112233445566778899aabbccddeeff",
                "second",
                List.of(),
                List.of("f1.txt"),
                List.of()
            );
            var event = createBasicPushEvent("refs/heads/main", false, List.of(commit1, commit2));

            Repository repo = createMockRepository(100L, "owner/repo", "main");
            when(repositoryRepository.findByIdWithOrganization(100L)).thenReturn(Optional.of(repo));
            when(gitRepositoryManager.isEnabled()).thenReturn(false);

            invokeHandleEvent(event);

            verify(commitRepository, times(2)).upsertCommit(
                anyString(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                any(Integer.class),
                any(Integer.class),
                any(Integer.class),
                any(),
                eq(100L),
                any(),
                any(),
                any(),
                any()
            );
        }

        @Test
        @DisplayName("should resolve author by username")
        void shouldResolveAuthorByUsername() throws Exception {
            var commit = createPushCommit(
                "sha1aabbccdd112233445566778899aabbccddeeff",
                "msg",
                List.of(),
                List.of(),
                List.of()
            );
            var event = createBasicPushEvent("refs/heads/main", false, List.of(commit));

            Repository repo = createMockRepository(100L, "owner/repo", "main");
            when(repositoryRepository.findByIdWithOrganization(100L)).thenReturn(Optional.of(repo));
            when(gitRepositoryManager.isEnabled()).thenReturn(false);

            when(authorResolver.resolveByLogin("authoruser")).thenReturn(42L);
            when(authorResolver.resolveByLogin("committeruser")).thenReturn(43L);

            invokeHandleEvent(event);

            verify(commitRepository).upsertCommit(
                anyString(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                any(Integer.class),
                any(Integer.class),
                any(Integer.class),
                any(),
                eq(100L),
                eq(42L),
                eq(43L),
                any(),
                any()
            );
        }

        @Test
        @DisplayName("should handle commits with null author username")
        void shouldHandleCommitsWithNullAuthorUsername() throws Exception {
            var commit = new GitHubPushEventDTO.PushCommit(
                "sha1aabbccdd112233445566778899aabbccddeeff",
                "tree123",
                "message",
                Instant.parse("2024-01-15T10:30:00Z"),
                "https://github.com/owner/repo/commit/sha1",
                new GitHubPushEventDTO.CommitUser("Author", "author@test.com", null),
                null, // null committer
                List.of("file.txt"),
                List.of(),
                List.of(),
                true
            );
            var event = createBasicPushEvent("refs/heads/main", false, List.of(commit));

            Repository repo = createMockRepository(100L, "owner/repo", "main");
            when(repositoryRepository.findByIdWithOrganization(100L)).thenReturn(Optional.of(repo));
            when(gitRepositoryManager.isEnabled()).thenReturn(false);
            when(authorResolver.resolveByLogin(null)).thenReturn(null);

            invokeHandleEvent(event);

            verify(commitRepository).upsertCommit(
                anyString(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                any(Integer.class),
                any(Integer.class),
                any(Integer.class),
                any(),
                eq(100L),
                eq(null),
                eq(null),
                any(),
                any()
            );
        }

        @Test
        @DisplayName("should count changed files correctly")
        void shouldCountChangedFilesCorrectly() throws Exception {
            var commit = createPushCommit(
                "sha1aabbccdd112233445566778899aabbccddeeff",
                "changes",
                List.of("new1.txt", "new2.txt"), // 2 added
                List.of("mod1.txt"), // 1 modified
                List.of("del1.txt", "del2.txt", "del3.txt") // 3 removed
            );
            var event = createBasicPushEvent("refs/heads/main", false, List.of(commit));

            Repository repo = createMockRepository(100L, "owner/repo", "main");
            when(repositoryRepository.findByIdWithOrganization(100L)).thenReturn(Optional.of(repo));
            when(gitRepositoryManager.isEnabled()).thenReturn(false);

            invokeHandleEvent(event);

            verify(commitRepository).upsertCommit(
                anyString(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                eq(0),
                eq(0),
                eq(6), // 2 + 1 + 3
                any(),
                eq(100L),
                any(),
                any(),
                any(),
                any()
            );
        }

        @Test
        @DisplayName("should extract message headline and body correctly")
        void shouldExtractMessageHeadlineAndBodyCorrectly() throws Exception {
            var commit = createPushCommit(
                "sha1aabbccdd112233445566778899aabbccddeeff",
                "feat: add feature\n\nThis is the body.\nWith multiple lines.",
                List.of("file.txt"),
                List.of(),
                List.of()
            );
            var event = createBasicPushEvent("refs/heads/main", false, List.of(commit));

            Repository repo = createMockRepository(100L, "owner/repo", "main");
            when(repositoryRepository.findByIdWithOrganization(100L)).thenReturn(Optional.of(repo));
            when(gitRepositoryManager.isEnabled()).thenReturn(false);

            invokeHandleEvent(event);

            verify(commitRepository).upsertCommit(
                anyString(),
                eq("feat: add feature"),
                eq("This is the body.\nWith multiple lines."),
                any(),
                any(),
                any(),
                any(Integer.class),
                any(Integer.class),
                any(Integer.class),
                any(),
                eq(100L),
                any(),
                any(),
                any(),
                any()
            );
        }
    }

    @Nested
    @DisplayName("local git processing")
    class LocalGitProcessing {

        @Test
        @DisplayName("should use local git when enabled")
        void shouldUseLocalGitWhenEnabled() throws Exception {
            var commit = createPushCommit(
                "sha1aabbccdd112233445566778899aabbccddeeff",
                "msg",
                List.of(),
                List.of(),
                List.of()
            );
            var event = createBasicPushEvent("refs/heads/main", false, List.of(commit));

            Repository repo = createMockRepository(100L, "owner/repo", "main");
            when(repositoryRepository.findByIdWithOrganization(100L)).thenReturn(Optional.of(repo));
            mockActiveScopeForRepo("owner/repo");
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(tokenService.isConfigured()).thenReturn(true);
            when(tokenService.getInstallationToken(42L)).thenReturn("test-token");
            when(gitRepositoryManager.walkCommits(eq(100L), any(), any())).thenReturn(List.of());

            invokeHandleEvent(event);

            verify(gitRepositoryManager).ensureRepository(
                eq(100L),
                eq("https://github.com/owner/repo.git"),
                eq("test-token")
            );
            verify(gitRepositoryManager).walkCommits(eq(100L), eq("abc123"), eq("def456"));
        }

        @Test
        @DisplayName("should fall back to webhook on git failure")
        void shouldFallBackToWebhookOnGitFailure() throws Exception {
            var commit = createPushCommit(
                "sha1aabbccdd112233445566778899aabbccddeeff",
                "msg",
                List.of("file.txt"),
                List.of(),
                List.of()
            );
            var event = createBasicPushEvent("refs/heads/main", false, List.of(commit));

            Repository repo = createMockRepository(100L, "owner/repo", "main");
            when(repositoryRepository.findByIdWithOrganization(100L)).thenReturn(Optional.of(repo));
            mockActiveScopeForRepo("owner/repo");
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(tokenService.isConfigured()).thenReturn(false);
            when(gitRepositoryManager.ensureRepository(eq(100L), any(), any())).thenThrow(
                new RuntimeException("Git clone failed")
            );

            invokeHandleEvent(event);

            // Should fall back to webhook processing with null stats (preserves existing data)
            verify(commitRepository).upsertCommit(
                anyString(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                eq(null), // additions: null on fallback to preserve richer data
                eq(null), // deletions: null on fallback to preserve richer data
                eq(null), // changedFiles: null on fallback to preserve richer data
                any(),
                eq(100L),
                any(),
                any(),
                any(),
                any()
            );
        }

        @Test
        @DisplayName("should process commit info from local git with file changes")
        void shouldProcessCommitInfoFromLocalGitWithFileChanges() throws Exception {
            var commit = createPushCommit(
                "sha1aabbccdd112233445566778899aabbccddeeff",
                "msg",
                List.of(),
                List.of(),
                List.of()
            );
            var event = createBasicPushEvent("refs/heads/main", false, List.of(commit));

            Repository repo = createMockRepository(100L, "owner/repo", "main");
            when(repositoryRepository.findByIdWithOrganization(100L)).thenReturn(Optional.of(repo));
            mockActiveScopeForRepo("owner/repo");
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(tokenService.isConfigured()).thenReturn(false);

            var fileChange = new GitRepositoryManager.FileChange(
                "src/main.java",
                GitRepositoryManager.ChangeType.ADDED,
                10,
                0,
                10,
                null
            );
            var commitInfo = new GitRepositoryManager.CommitInfo(
                "sha1aabbccdd112233445566778899aabbccddeeff",
                "msg",
                null,
                "Author",
                "author@test.com",
                Instant.parse("2024-01-15T10:30:00Z"),
                "Committer",
                "committer@test.com",
                Instant.parse("2024-01-15T10:30:00Z"),
                10,
                0,
                1,
                List.of(fileChange)
            );
            when(gitRepositoryManager.walkCommits(eq(100L), any(), any())).thenReturn(List.of(commitInfo));
            when(
                commitRepository.existsByShaAndRepositoryId("sha1aabbccdd112233445566778899aabbccddeeff", 100L)
            ).thenReturn(false);

            // After upsertCommit, findByShaAndRepositoryId must return a Commit entity for file changes
            // and for publishCommitCreated (which calls CommitData.from(commit))
            var persistedCommit = mock(
                de.tum.in.www1.hephaestus.gitprovider.commit.Commit.class,
                org.mockito.Mockito.withSettings().lenient()
            );
            when(persistedCommit.getId()).thenReturn(1L);
            when(persistedCommit.getSha()).thenReturn("sha1aabbccdd112233445566778899aabbccddeeff");
            when(persistedCommit.getMessage()).thenReturn("msg");
            when(persistedCommit.getAuthoredAt()).thenReturn(Instant.parse("2024-01-15T10:30:00Z"));
            when(persistedCommit.getRepository()).thenReturn(repo);
            when(
                commitRepository.findByShaAndRepositoryId("sha1aabbccdd112233445566778899aabbccddeeff", 100L)
            ).thenReturn(Optional.of(persistedCommit));

            invokeHandleEvent(event);

            // Should upsert the commit via native SQL
            verify(commitRepository).upsertCommit(
                eq("sha1aabbccdd112233445566778899aabbccddeeff"),
                eq("msg"),
                any(),
                anyString(),
                any(),
                any(),
                eq(10),
                eq(0),
                eq(1),
                any(),
                eq(100L),
                any(),
                any(),
                any(),
                any()
            );
            // Should fetch the persisted commit: once for file changes, once for publishCommitCreated
            verify(commitRepository, times(2)).findByShaAndRepositoryId(
                "sha1aabbccdd112233445566778899aabbccddeeff",
                100L
            );
            verify(commitRepository).save(persistedCommit);
        }

        @Test
        @DisplayName("should skip existing commits in local git mode")
        void shouldSkipExistingCommitsInLocalGitMode() throws Exception {
            var commit = createPushCommit(
                "sha1aabbccdd112233445566778899aabbccddeeff",
                "msg",
                List.of(),
                List.of(),
                List.of()
            );
            var event = createBasicPushEvent("refs/heads/main", false, List.of(commit));

            Repository repo = createMockRepository(100L, "owner/repo", "main");
            when(repositoryRepository.findByIdWithOrganization(100L)).thenReturn(Optional.of(repo));
            mockActiveScopeForRepo("owner/repo");
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(tokenService.isConfigured()).thenReturn(false);

            var commitInfo = new GitRepositoryManager.CommitInfo(
                "sha1aabbccdd112233445566778899aabbccddeeff",
                "msg",
                null,
                "Author",
                "author@test.com",
                Instant.now(),
                "Committer",
                "committer@test.com",
                Instant.now(),
                0,
                0,
                0,
                List.of()
            );
            when(gitRepositoryManager.walkCommits(eq(100L), any(), any())).thenReturn(List.of(commitInfo));
            when(
                commitRepository.existsByShaAndRepositoryId("sha1aabbccdd112233445566778899aabbccddeeff", 100L)
            ).thenReturn(true);

            invokeHandleEvent(event);

            verify(commitRepository, never()).upsertCommit(
                anyString(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyLong(),
                any(),
                any(),
                any(),
                any()
            );
            verify(commitRepository, never()).save(any());
        }

        @Test
        @DisplayName("should fall back to webhook when scope is not active")
        void shouldFallBackToWebhookWhenScopeNotActive() throws Exception {
            var commit = createPushCommit(
                "sha1aabbccdd112233445566778899aabbccddeeff",
                "msg",
                List.of("file.txt"),
                List.of(),
                List.of()
            );
            var event = createBasicPushEvent("refs/heads/main", false, List.of(commit));

            Repository repo = createMockRepository(100L, "owner/repo", "main");
            when(repositoryRepository.findByIdWithOrganization(100L)).thenReturn(Optional.of(repo));
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            // Scope not active: scopeIdResolver returns a scope, but it's inactive
            when(scopeIdResolver.findScopeIdByRepositoryName("owner/repo")).thenReturn(Optional.of(99L));
            when(syncTargetProvider.isScopeActiveForSync(99L)).thenReturn(false);

            invokeHandleEvent(event);

            // Should NOT use local git
            verify(gitRepositoryManager, never()).ensureRepository(anyLong(), anyString(), any());
            verify(gitRepositoryManager, never()).walkCommits(anyLong(), any(), any());

            // Should process via webhook instead (non-fallback: additions=0, not null)
            verify(commitRepository).upsertCommit(
                anyString(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                eq(0),
                eq(0),
                eq(1), // 1 added file
                any(),
                eq(100L),
                any(),
                any(),
                any(),
                any()
            );
        }
    }

    @Nested
    @DisplayName("branch handling")
    class BranchHandling {

        @Test
        @DisplayName("should process pushes to default branch")
        void shouldProcessPushesToDefaultBranch() throws Exception {
            var commit = createPushCommit(
                "sha1aabbccdd112233445566778899aabbccddeeff",
                "msg",
                List.of("f.txt"),
                List.of(),
                List.of()
            );
            var event = createBasicPushEvent("refs/heads/develop", false, List.of(commit));

            Repository repo = createMockRepository(100L, "owner/repo", "develop");
            when(repositoryRepository.findByIdWithOrganization(100L)).thenReturn(Optional.of(repo));
            when(gitRepositoryManager.isEnabled()).thenReturn(false);

            invokeHandleEvent(event);

            verify(commitRepository).upsertCommit(
                anyString(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                any(Integer.class),
                any(Integer.class),
                any(Integer.class),
                any(),
                eq(100L),
                any(),
                any(),
                any(),
                any()
            );
        }

        @Test
        @DisplayName("should handle refs without refs/heads/ prefix")
        void shouldHandleRefsWithoutPrefix() throws Exception {
            // Edge case: ref doesn't start with "refs/heads/"
            var commit = createPushCommit(
                "sha1aabbccdd112233445566778899aabbccddeeff",
                "msg",
                List.of("f.txt"),
                List.of(),
                List.of()
            );
            var event = createBasicPushEvent("main", false, List.of(commit));

            Repository repo = createMockRepository(100L, "owner/repo", "main");
            when(repositoryRepository.findByIdWithOrganization(100L)).thenReturn(Optional.of(repo));
            when(gitRepositoryManager.isEnabled()).thenReturn(false);

            invokeHandleEvent(event);

            verify(commitRepository).upsertCommit(
                anyString(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                any(Integer.class),
                any(Integer.class),
                any(Integer.class),
                any(),
                eq(100L),
                any(),
                any(),
                any(),
                any()
            );
        }
    }

    @Nested
    @DisplayName("DTO contract")
    class DtoContract {

        @Test
        @DisplayName("action should return pushed")
        void actionShouldReturnPushed() {
            var event = createBasicPushEvent("refs/heads/main", false, List.of());
            assertThat(event.action()).isEqualTo("pushed");
        }

        @Test
        @DisplayName("actionType should return PUSHED")
        void actionTypeShouldReturnPushed() {
            var event = createBasicPushEvent("refs/heads/main", false, List.of());
            assertThat(event.actionType()).isEqualTo(
                de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction.Push.PUSHED
            );
        }
    }

    @Nested
    @DisplayName("event publishing")
    class EventPublishing {

        @Test
        @DisplayName("should publish CommitCreated event after webhook processing")
        void shouldPublishCommitCreatedEventAfterWebhookProcessing() throws Exception {
            var commit = createPushCommit(
                "abc123def456789012345678901234567890abcd",
                "feat: publish test",
                List.of("file.txt"),
                List.of(),
                List.of()
            );
            var event = createBasicPushEvent("refs/heads/main", false, List.of(commit));

            Repository repo = createMockRepository(100L, "owner/repo", "main");
            when(repositoryRepository.findByIdWithOrganization(100L)).thenReturn(Optional.of(repo));
            when(gitRepositoryManager.isEnabled()).thenReturn(false);

            // Mock the persisted commit lookup for publishCommitCreated
            var persistedCommit = mock(
                de.tum.in.www1.hephaestus.gitprovider.commit.Commit.class,
                org.mockito.Mockito.withSettings().lenient()
            );
            when(persistedCommit.getId()).thenReturn(1L);
            when(persistedCommit.getSha()).thenReturn("abc123def456789012345678901234567890abcd");
            when(persistedCommit.getMessage()).thenReturn("feat: publish test");
            when(persistedCommit.getAuthoredAt()).thenReturn(Instant.parse("2024-01-15T10:30:00Z"));
            when(persistedCommit.getRepository()).thenReturn(repo);
            when(
                commitRepository.findByShaAndRepositoryId("abc123def456789012345678901234567890abcd", 100L)
            ).thenReturn(Optional.of(persistedCommit));

            invokeHandleEvent(event);

            // Verify event was published
            ArgumentCaptor<DomainEvent.CommitCreated> captor = ArgumentCaptor.forClass(DomainEvent.CommitCreated.class);
            verify(eventPublisher).publishEvent(captor.capture());

            DomainEvent.CommitCreated published = captor.getValue();
            assertThat(published.commit().sha()).isEqualTo("abc123def456789012345678901234567890abcd");
            assertThat(published.commit().message()).isEqualTo("feat: publish test");
            assertThat(published.commit().repositoryId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("should not publish event when commit not found after upsert")
        void shouldNotPublishEventWhenCommitNotFoundAfterUpsert() throws Exception {
            var commit = createPushCommit(
                "abc123def456789012345678901234567890abcd",
                "msg",
                List.of("file.txt"),
                List.of(),
                List.of()
            );
            var event = createBasicPushEvent("refs/heads/main", false, List.of(commit));

            Repository repo = createMockRepository(100L, "owner/repo", "main");
            when(repositoryRepository.findByIdWithOrganization(100L)).thenReturn(Optional.of(repo));
            when(gitRepositoryManager.isEnabled()).thenReturn(false);

            // findByShaAndRepositoryId returns empty — commit not found after upsert
            when(
                commitRepository.findByShaAndRepositoryId("abc123def456789012345678901234567890abcd", 100L)
            ).thenReturn(Optional.empty());

            invokeHandleEvent(event);

            // Verify event was NOT published
            verify(eventPublisher, never()).publishEvent(any(DomainEvent.CommitCreated.class));
        }
    }
}
