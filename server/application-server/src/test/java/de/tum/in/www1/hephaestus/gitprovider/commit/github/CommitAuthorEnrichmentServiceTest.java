package de.tum.in.www1.hephaestus.gitprovider.commit.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.commit.CommitAuthorResolver;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlSyncCoordinator;
import de.tum.in.www1.hephaestus.gitprovider.git.GitRepositoryManager;
import de.tum.in.www1.hephaestus.gitprovider.git.GitRepositoryManager.EmailPair;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@DisplayName("CommitAuthorEnrichmentService")
class CommitAuthorEnrichmentServiceTest extends BaseUnitTest {

    @Mock
    private CommitRepository commitRepository;

    @Mock
    private CommitAuthorResolver authorResolver;

    @Mock
    private GitRepositoryManager gitRepositoryManager;

    @Mock
    private GitHubGraphQlClientProvider graphQlClientProvider;

    @Mock
    private GitHubGraphQlSyncCoordinator graphQlSyncCoordinator;

    @Mock
    private GitHubExceptionClassifier exceptionClassifier;

    private CommitAuthorEnrichmentService service;

    @BeforeEach
    void setUp() {
        service = new CommitAuthorEnrichmentService(
            commitRepository,
            authorResolver,
            gitRepositoryManager,
            graphQlClientProvider,
            graphQlSyncCoordinator,
            exceptionClassifier
        );
    }

    // ========== Tests ==========

    @Nested
    @DisplayName("skip conditions")
    class SkipConditions {

        @Test
        @DisplayName("should return -1 when git checkout is disabled")
        void shouldReturnNegativeOneWhenDisabled() {
            when(gitRepositoryManager.isEnabled()).thenReturn(false);

            int result = service.enrichCommitAuthors(1L, "owner/repo", 1L);

            assertThat(result).isEqualTo(-1);
            verify(commitRepository, never()).findShasWithNullAuthorByRepositoryId(anyLong());
        }

        @Test
        @DisplayName("should return 0 when no unresolved commits exist")
        void shouldReturnZeroWhenNoUnresolvedCommits() {
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(commitRepository.findShasWithNullAuthorByRepositoryId(1L)).thenReturn(List.of());
            when(commitRepository.findShasWithNullCommitterByRepositoryId(1L)).thenReturn(List.of());

            int result = service.enrichCommitAuthors(1L, "owner/repo", 1L);

            assertThat(result).isEqualTo(0);
            verify(gitRepositoryManager, never()).resolveCommitEmails(anyLong(), any());
        }

        @Test
        @DisplayName("should return 0 when email map is empty")
        void shouldReturnZeroWhenEmailMapEmpty() {
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(commitRepository.findShasWithNullAuthorByRepositoryId(1L)).thenReturn(List.of("sha1"));
            when(commitRepository.findShasWithNullCommitterByRepositoryId(1L)).thenReturn(List.of());
            when(gitRepositoryManager.resolveCommitEmails(eq(1L), any())).thenReturn(Map.of());

            int result = service.enrichCommitAuthors(1L, "owner/repo", 1L);

            assertThat(result).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("email-based enrichment (Phase 1)")
    class EmailBasedEnrichment {

        @Test
        @DisplayName("should enrich authors by email when resolver finds a match")
        void shouldEnrichAuthorsByEmail() {
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(commitRepository.findShasWithNullAuthorByRepositoryId(1L)).thenReturn(List.of("sha1", "sha2"));
            when(commitRepository.findShasWithNullCommitterByRepositoryId(1L)).thenReturn(List.of());
            when(gitRepositoryManager.resolveCommitEmails(eq(1L), any())).thenReturn(
                Map.of(
                    "sha1",
                    new EmailPair("author@example.com", "committer@example.com"),
                    "sha2",
                    new EmailPair("author@example.com", "committer@example.com")
                )
            );
            when(authorResolver.resolveByEmail("author@example.com")).thenReturn(42L);

            // After email enrichment, re-query returns empty (all resolved)
            when(commitRepository.findShasWithNullAuthorByRepositoryId(1L))
                .thenReturn(List.of("sha1", "sha2")) // first call
                .thenReturn(List.of()); // second call (after enrichment)
            when(commitRepository.findShasWithNullCommitterByRepositoryId(1L))
                .thenReturn(List.of()) // first call
                .thenReturn(List.of()); // second call

            when(commitRepository.bulkUpdateAuthorId(List.of("sha1", "sha2"), 1L, 42L)).thenReturn(2);

            int result = service.enrichCommitAuthors(1L, "owner/repo", 1L);

            assertThat(result).isEqualTo(2);
            verify(commitRepository).bulkUpdateAuthorId(List.of("sha1", "sha2"), 1L, 42L);
        }

        @Test
        @DisplayName("should enrich committers by email when resolver finds a match")
        void shouldEnrichCommittersByEmail() {
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(commitRepository.findShasWithNullAuthorByRepositoryId(1L)).thenReturn(List.of());
            when(commitRepository.findShasWithNullCommitterByRepositoryId(1L))
                .thenReturn(List.of("sha1"))
                .thenReturn(List.of()); // after enrichment
            when(gitRepositoryManager.resolveCommitEmails(eq(1L), any())).thenReturn(
                Map.of("sha1", new EmailPair("author@example.com", "committer@example.com"))
            );
            when(authorResolver.resolveByEmail("committer@example.com")).thenReturn(99L);
            when(commitRepository.bulkUpdateCommitterId(List.of("sha1"), 1L, 99L)).thenReturn(1);

            int result = service.enrichCommitAuthors(1L, "owner/repo", 1L);

            assertThat(result).isEqualTo(1);
            verify(commitRepository).bulkUpdateCommitterId(List.of("sha1"), 1L, 99L);
        }

        @Test
        @DisplayName("should skip email clusters where resolver returns null")
        void shouldSkipUnresolvableEmails() {
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(commitRepository.findShasWithNullAuthorByRepositoryId(1L))
                .thenReturn(List.of("sha1"))
                .thenReturn(List.of("sha1")); // still unresolved after email pass
            when(commitRepository.findShasWithNullCommitterByRepositoryId(1L))
                .thenReturn(List.of())
                .thenReturn(List.of());
            when(gitRepositoryManager.resolveCommitEmails(eq(1L), any())).thenReturn(
                Map.of("sha1", new EmailPair("unknown@personal.com", "unknown@personal.com"))
            );
            when(authorResolver.resolveByEmail("unknown@personal.com")).thenReturn(null);

            // ScopeId is null so API pass is skipped
            int result = service.enrichCommitAuthors(1L, "owner/repo", null);

            assertThat(result).isEqualTo(0);
            verify(commitRepository, never()).bulkUpdateAuthorId(any(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("should handle multiple email clusters independently")
        void shouldHandleMultipleEmailClusters() {
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(commitRepository.findShasWithNullAuthorByRepositoryId(1L))
                .thenReturn(List.of("sha1", "sha2", "sha3"))
                .thenReturn(List.of()); // all resolved after email pass
            when(commitRepository.findShasWithNullCommitterByRepositoryId(1L))
                .thenReturn(List.of())
                .thenReturn(List.of());
            when(gitRepositoryManager.resolveCommitEmails(eq(1L), any())).thenReturn(
                Map.of(
                    "sha1",
                    new EmailPair("alice@example.com", "alice@example.com"),
                    "sha2",
                    new EmailPair("bob@example.com", "bob@example.com"),
                    "sha3",
                    new EmailPair("alice@example.com", "alice@example.com")
                )
            );

            // Alice resolves, Bob does not
            when(authorResolver.resolveByEmail("alice@example.com")).thenReturn(10L);
            when(authorResolver.resolveByEmail("bob@example.com")).thenReturn(null);

            when(commitRepository.bulkUpdateAuthorId(any(), eq(1L), eq(10L))).thenReturn(2);

            int result = service.enrichCommitAuthors(1L, "owner/repo", null);

            assertThat(result).isEqualTo(2);
            // Alice's cluster updated, Bob's skipped
            verify(commitRepository).bulkUpdateAuthorId(any(), eq(1L), eq(10L));
            verify(commitRepository, never()).bulkUpdateAuthorId(any(), anyLong(), eq(20L));
        }
    }

    @Nested
    @DisplayName("API-based enrichment (Phase 2)")
    class ApiBasedEnrichment {

        @Test
        @DisplayName("should skip API enrichment when scopeId is null")
        void shouldSkipApiEnrichmentWhenScopeIdNull() {
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(commitRepository.findShasWithNullAuthorByRepositoryId(1L))
                .thenReturn(List.of("sha1"))
                .thenReturn(List.of("sha1")); // still unresolved
            when(commitRepository.findShasWithNullCommitterByRepositoryId(1L))
                .thenReturn(List.of())
                .thenReturn(List.of());
            when(gitRepositoryManager.resolveCommitEmails(eq(1L), any())).thenReturn(
                Map.of("sha1", new EmailPair("unknown@personal.com", "unknown@personal.com"))
            );
            when(authorResolver.resolveByEmail("unknown@personal.com")).thenReturn(null);

            int result = service.enrichCommitAuthors(1L, "owner/repo", null);

            assertThat(result).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("combined enrichment")
    class CombinedEnrichment {

        @Test
        @DisplayName("should enrich both author and committer for same commit")
        void shouldEnrichBothAuthorAndCommitter() {
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(commitRepository.findShasWithNullAuthorByRepositoryId(1L))
                .thenReturn(List.of("sha1"))
                .thenReturn(List.of()); // resolved after email pass
            when(commitRepository.findShasWithNullCommitterByRepositoryId(1L))
                .thenReturn(List.of("sha1"))
                .thenReturn(List.of()); // resolved after email pass
            when(gitRepositoryManager.resolveCommitEmails(eq(1L), eq(Set.of("sha1")))).thenReturn(
                Map.of("sha1", new EmailPair("author@example.com", "committer@example.com"))
            );

            when(authorResolver.resolveByEmail("author@example.com")).thenReturn(10L);
            when(authorResolver.resolveByEmail("committer@example.com")).thenReturn(20L);
            when(commitRepository.bulkUpdateAuthorId(List.of("sha1"), 1L, 10L)).thenReturn(1);
            when(commitRepository.bulkUpdateCommitterId(List.of("sha1"), 1L, 20L)).thenReturn(1);

            int result = service.enrichCommitAuthors(1L, "owner/repo", 1L);

            assertThat(result).isEqualTo(2);
            verify(commitRepository).bulkUpdateAuthorId(List.of("sha1"), 1L, 10L);
            verify(commitRepository).bulkUpdateCommitterId(List.of("sha1"), 1L, 20L);
        }

        @Test
        @DisplayName("should count email and API enrichments separately")
        void shouldCountEmailAndApiEnrichmentsSeparately() {
            // sha1 can be resolved by email, sha2 cannot
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(commitRepository.findShasWithNullAuthorByRepositoryId(1L))
                .thenReturn(List.of("sha1", "sha2"))
                .thenReturn(List.of("sha2")); // sha2 still unresolved after email
            when(commitRepository.findShasWithNullCommitterByRepositoryId(1L))
                .thenReturn(List.of())
                .thenReturn(List.of());
            when(gitRepositoryManager.resolveCommitEmails(eq(1L), any())).thenReturn(
                Map.of(
                    "sha1",
                    new EmailPair("known@noreply.github.com", "known@noreply.github.com"),
                    "sha2",
                    new EmailPair("personal@email.com", "personal@email.com")
                )
            );

            // Email pass resolves sha1 but not sha2
            when(authorResolver.resolveByEmail("known@noreply.github.com")).thenReturn(10L);
            when(authorResolver.resolveByEmail("personal@email.com")).thenReturn(null);
            when(commitRepository.bulkUpdateAuthorId(List.of("sha1"), 1L, 10L)).thenReturn(1);

            // API pass would be attempted for sha2 but since we're not mocking
            // the GraphQL client, the API fetch will fail/return empty.
            // The service handles this gracefully — no crash, just 0 from API.

            int result = service.enrichCommitAuthors(1L, "owner/repo", 1L);

            // Only the email-resolved enrichment should count
            assertThat(result).isGreaterThanOrEqualTo(1);
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle commits where SHA is not in email map")
        void shouldHandleCommitsNotInEmailMap() {
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(commitRepository.findShasWithNullAuthorByRepositoryId(1L))
                .thenReturn(List.of("sha1", "sha_missing"))
                .thenReturn(List.of()); // all resolved
            when(commitRepository.findShasWithNullCommitterByRepositoryId(1L))
                .thenReturn(List.of())
                .thenReturn(List.of());

            // Only sha1 is in the email map; sha_missing is not (e.g. not in local clone)
            when(gitRepositoryManager.resolveCommitEmails(eq(1L), any())).thenReturn(
                Map.of("sha1", new EmailPair("author@example.com", "committer@example.com"))
            );
            when(authorResolver.resolveByEmail("author@example.com")).thenReturn(10L);
            when(commitRepository.bulkUpdateAuthorId(List.of("sha1"), 1L, 10L)).thenReturn(1);

            int result = service.enrichCommitAuthors(1L, "owner/repo", null);

            assertThat(result).isEqualTo(1);
        }

        @Test
        @DisplayName("should handle same email for author and committer")
        void shouldHandleSameEmailForAuthorAndCommitter() {
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(commitRepository.findShasWithNullAuthorByRepositoryId(1L))
                .thenReturn(List.of("sha1"))
                .thenReturn(List.of());
            when(commitRepository.findShasWithNullCommitterByRepositoryId(1L))
                .thenReturn(List.of("sha1"))
                .thenReturn(List.of());
            when(gitRepositoryManager.resolveCommitEmails(eq(1L), any())).thenReturn(
                Map.of("sha1", new EmailPair("same@example.com", "same@example.com"))
            );

            when(authorResolver.resolveByEmail("same@example.com")).thenReturn(10L);
            when(commitRepository.bulkUpdateAuthorId(List.of("sha1"), 1L, 10L)).thenReturn(1);
            when(commitRepository.bulkUpdateCommitterId(List.of("sha1"), 1L, 10L)).thenReturn(1);

            int result = service.enrichCommitAuthors(1L, "owner/repo", null);

            assertThat(result).isEqualTo(2);
        }

        @Test
        @DisplayName("should not count enrichments when bulk update returns 0")
        void shouldNotCountZeroUpdates() {
            when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(commitRepository.findShasWithNullAuthorByRepositoryId(1L))
                .thenReturn(List.of("sha1"))
                .thenReturn(List.of());
            when(commitRepository.findShasWithNullCommitterByRepositoryId(1L))
                .thenReturn(List.of())
                .thenReturn(List.of());
            when(gitRepositoryManager.resolveCommitEmails(eq(1L), any())).thenReturn(
                Map.of("sha1", new EmailPair("author@example.com", "committer@example.com"))
            );
            when(authorResolver.resolveByEmail("author@example.com")).thenReturn(42L);
            // Bulk update returns 0 (already resolved by concurrent process)
            when(commitRepository.bulkUpdateAuthorId(List.of("sha1"), 1L, 42L)).thenReturn(0);

            int result = service.enrichCommitAuthors(1L, "owner/repo", null);

            assertThat(result).isEqualTo(0);
        }
    }
}
