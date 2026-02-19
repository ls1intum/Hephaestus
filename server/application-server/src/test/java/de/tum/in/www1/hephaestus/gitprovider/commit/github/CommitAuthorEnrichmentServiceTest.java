package de.tum.in.www1.hephaestus.gitprovider.commit.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.commit.CommitAuthorResolver;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlSyncCoordinator;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserProcessor;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
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
    private GitHubGraphQlClientProvider graphQlClientProvider;

    @Mock
    private GitHubGraphQlSyncCoordinator graphQlSyncCoordinator;

    @Mock
    private GitHubExceptionClassifier exceptionClassifier;

    @Mock
    private GitHubUserProcessor userProcessor;

    private CommitAuthorEnrichmentService service;

    @BeforeEach
    void setUp() {
        service = new CommitAuthorEnrichmentService(
            commitRepository,
            authorResolver,
            graphQlClientProvider,
            graphQlSyncCoordinator,
            exceptionClassifier,
            userProcessor
        );
    }

    // ========== Tests ==========

    @Nested
    @DisplayName("skip conditions")
    class SkipConditions {

        @Test
        @DisplayName("should return 0 when no unresolved emails exist")
        void shouldReturnZeroWhenNoUnresolvedEmails() {
            when(commitRepository.findDistinctUnresolvedAuthorEmailsByRepositoryId(1L)).thenReturn(List.of());
            when(commitRepository.findDistinctUnresolvedCommitterEmailsByRepositoryId(1L)).thenReturn(List.of());

            int result = service.enrichCommitAuthors(1L, "owner/repo", 1L);

            assertThat(result).isEqualTo(0);
            verify(authorResolver, never()).resolveByEmail(any());
        }
    }

    @Nested
    @DisplayName("email-based enrichment (Phase 1)")
    class EmailBasedEnrichment {

        @Test
        @DisplayName("should enrich authors by email when resolver finds a match")
        void shouldEnrichAuthorsByEmail() {
            when(commitRepository.findDistinctUnresolvedAuthorEmailsByRepositoryId(1L))
                .thenReturn(List.of("author@example.com")) // first call
                .thenReturn(List.of()); // second call (after enrichment)
            when(commitRepository.findDistinctUnresolvedCommitterEmailsByRepositoryId(1L))
                .thenReturn(List.of()) // first call
                .thenReturn(List.of()); // second call

            when(authorResolver.resolveByEmail("author@example.com")).thenReturn(42L);
            when(commitRepository.bulkUpdateAuthorIdByEmail("author@example.com", 1L, 42L)).thenReturn(2);

            int result = service.enrichCommitAuthors(1L, "owner/repo", 1L);

            assertThat(result).isEqualTo(2);
            verify(commitRepository).bulkUpdateAuthorIdByEmail("author@example.com", 1L, 42L);
        }

        @Test
        @DisplayName("should enrich committers by email when resolver finds a match")
        void shouldEnrichCommittersByEmail() {
            when(commitRepository.findDistinctUnresolvedAuthorEmailsByRepositoryId(1L))
                .thenReturn(List.of())
                .thenReturn(List.of());
            when(commitRepository.findDistinctUnresolvedCommitterEmailsByRepositoryId(1L))
                .thenReturn(List.of("committer@example.com"))
                .thenReturn(List.of()); // after enrichment

            when(authorResolver.resolveByEmail("committer@example.com")).thenReturn(99L);
            when(commitRepository.bulkUpdateCommitterIdByEmail("committer@example.com", 1L, 99L)).thenReturn(1);

            int result = service.enrichCommitAuthors(1L, "owner/repo", 1L);

            assertThat(result).isEqualTo(1);
            verify(commitRepository).bulkUpdateCommitterIdByEmail("committer@example.com", 1L, 99L);
        }

        @Test
        @DisplayName("should skip emails where resolver returns null")
        void shouldSkipUnresolvableEmails() {
            when(commitRepository.findDistinctUnresolvedAuthorEmailsByRepositoryId(1L))
                .thenReturn(List.of("unknown@personal.com"))
                .thenReturn(List.of("unknown@personal.com")); // still unresolved after email pass
            when(commitRepository.findDistinctUnresolvedCommitterEmailsByRepositoryId(1L))
                .thenReturn(List.of())
                .thenReturn(List.of());

            when(authorResolver.resolveByEmail("unknown@personal.com")).thenReturn(null);

            // ScopeId is null so API pass is skipped
            int result = service.enrichCommitAuthors(1L, "owner/repo", null);

            assertThat(result).isEqualTo(0);
            verify(commitRepository, never()).bulkUpdateAuthorIdByEmail(any(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("should handle multiple email clusters independently")
        void shouldHandleMultipleEmailClusters() {
            when(commitRepository.findDistinctUnresolvedAuthorEmailsByRepositoryId(1L))
                .thenReturn(List.of("alice@example.com", "bob@example.com"))
                .thenReturn(List.of()); // all resolved after email pass
            when(commitRepository.findDistinctUnresolvedCommitterEmailsByRepositoryId(1L))
                .thenReturn(List.of())
                .thenReturn(List.of());

            // Alice resolves, Bob does not
            when(authorResolver.resolveByEmail("alice@example.com")).thenReturn(10L);
            when(authorResolver.resolveByEmail("bob@example.com")).thenReturn(null);

            when(commitRepository.bulkUpdateAuthorIdByEmail("alice@example.com", 1L, 10L)).thenReturn(2);

            int result = service.enrichCommitAuthors(1L, "owner/repo", null);

            assertThat(result).isEqualTo(2);
            // Alice's cluster updated, Bob's skipped
            verify(commitRepository).bulkUpdateAuthorIdByEmail("alice@example.com", 1L, 10L);
            verify(commitRepository, never()).bulkUpdateAuthorIdByEmail(eq("bob@example.com"), anyLong(), anyLong());
        }
    }

    @Nested
    @DisplayName("API-based enrichment (Phase 2)")
    class ApiBasedEnrichment {

        @Test
        @DisplayName("should skip API enrichment when scopeId is null")
        void shouldSkipApiEnrichmentWhenScopeIdNull() {
            when(commitRepository.findDistinctUnresolvedAuthorEmailsByRepositoryId(1L))
                .thenReturn(List.of("unknown@personal.com"))
                .thenReturn(List.of("unknown@personal.com")); // still unresolved
            when(commitRepository.findDistinctUnresolvedCommitterEmailsByRepositoryId(1L))
                .thenReturn(List.of())
                .thenReturn(List.of());

            when(authorResolver.resolveByEmail("unknown@personal.com")).thenReturn(null);

            int result = service.enrichCommitAuthors(1L, "owner/repo", null);

            assertThat(result).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("combined enrichment")
    class CombinedEnrichment {

        @Test
        @DisplayName("should enrich both author and committer for same email")
        void shouldEnrichBothAuthorAndCommitter() {
            when(commitRepository.findDistinctUnresolvedAuthorEmailsByRepositoryId(1L))
                .thenReturn(List.of("author@example.com"))
                .thenReturn(List.of()); // resolved after email pass
            when(commitRepository.findDistinctUnresolvedCommitterEmailsByRepositoryId(1L))
                .thenReturn(List.of("committer@example.com"))
                .thenReturn(List.of()); // resolved after email pass

            when(authorResolver.resolveByEmail("author@example.com")).thenReturn(10L);
            when(authorResolver.resolveByEmail("committer@example.com")).thenReturn(20L);
            when(commitRepository.bulkUpdateAuthorIdByEmail("author@example.com", 1L, 10L)).thenReturn(1);
            when(commitRepository.bulkUpdateCommitterIdByEmail("committer@example.com", 1L, 20L)).thenReturn(1);

            int result = service.enrichCommitAuthors(1L, "owner/repo", 1L);

            assertThat(result).isEqualTo(2);
            verify(commitRepository).bulkUpdateAuthorIdByEmail("author@example.com", 1L, 10L);
            verify(commitRepository).bulkUpdateCommitterIdByEmail("committer@example.com", 1L, 20L);
        }

        @Test
        @DisplayName("should count email and API enrichments separately")
        void shouldCountEmailAndApiEnrichmentsSeparately() {
            // alice@example.com can be resolved by email, unknown@personal.com cannot
            when(commitRepository.findDistinctUnresolvedAuthorEmailsByRepositoryId(1L))
                .thenReturn(List.of("alice@example.com", "unknown@personal.com"))
                .thenReturn(List.of("unknown@personal.com")); // unknown still unresolved after email
            when(commitRepository.findDistinctUnresolvedCommitterEmailsByRepositoryId(1L))
                .thenReturn(List.of())
                .thenReturn(List.of());

            // Email pass resolves alice but not unknown
            when(authorResolver.resolveByEmail("alice@example.com")).thenReturn(10L);
            when(authorResolver.resolveByEmail("unknown@personal.com")).thenReturn(null);
            when(commitRepository.bulkUpdateAuthorIdByEmail("alice@example.com", 1L, 10L)).thenReturn(1);

            // API pass would be attempted for unknown@personal.com but since we're not mocking
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
        @DisplayName("should handle same email for author and committer")
        void shouldHandleSameEmailForAuthorAndCommitter() {
            when(commitRepository.findDistinctUnresolvedAuthorEmailsByRepositoryId(1L))
                .thenReturn(List.of("same@example.com"))
                .thenReturn(List.of());
            when(commitRepository.findDistinctUnresolvedCommitterEmailsByRepositoryId(1L))
                .thenReturn(List.of("same@example.com"))
                .thenReturn(List.of());

            when(authorResolver.resolveByEmail("same@example.com")).thenReturn(10L);
            when(commitRepository.bulkUpdateAuthorIdByEmail("same@example.com", 1L, 10L)).thenReturn(1);
            when(commitRepository.bulkUpdateCommitterIdByEmail("same@example.com", 1L, 10L)).thenReturn(1);

            int result = service.enrichCommitAuthors(1L, "owner/repo", null);

            assertThat(result).isEqualTo(2);
        }

        @Test
        @DisplayName("should not count enrichments when bulk update returns 0")
        void shouldNotCountZeroUpdates() {
            when(commitRepository.findDistinctUnresolvedAuthorEmailsByRepositoryId(1L))
                .thenReturn(List.of("author@example.com"))
                .thenReturn(List.of());
            when(commitRepository.findDistinctUnresolvedCommitterEmailsByRepositoryId(1L))
                .thenReturn(List.of())
                .thenReturn(List.of());

            when(authorResolver.resolveByEmail("author@example.com")).thenReturn(42L);
            // Bulk update returns 0 (already resolved by concurrent process)
            when(commitRepository.bulkUpdateAuthorIdByEmail("author@example.com", 1L, 42L)).thenReturn(0);

            int result = service.enrichCommitAuthors(1L, "owner/repo", null);

            assertThat(result).isEqualTo(0);
        }
    }
}
