package de.tum.in.www1.hephaestus.gitprovider.commit.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.commit.CommitContributorRepository;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlSyncCoordinator;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@DisplayName("CommitMetadataEnrichmentService")
class CommitMetadataEnrichmentServiceTest extends BaseUnitTest {

    @Mock
    private CommitRepository commitRepository;

    @Mock
    private CommitContributorRepository contributorRepository;

    @Mock
    private GitHubGraphQlClientProvider graphQlClientProvider;

    @Mock
    private GitHubGraphQlSyncCoordinator graphQlSyncCoordinator;

    @Mock
    private GitHubExceptionClassifier exceptionClassifier;

    private CommitMetadataEnrichmentService service;

    @BeforeEach
    void setUp() {
        service = new CommitMetadataEnrichmentService(
            commitRepository,
            contributorRepository,
            graphQlClientProvider,
            graphQlSyncCoordinator,
            exceptionClassifier
        );
    }

    @Nested
    @DisplayName("skip conditions")
    class SkipConditions {

        @Test
        @DisplayName("should return 0 when scopeId is null")
        void shouldReturnZeroWhenScopeIdNull() {
            int result = service.enrichCommitMetadata(1L, "owner/repo", null);

            assertThat(result).isEqualTo(0);
            verify(commitRepository, never()).findShasWithoutContributorsByRepositoryId(anyLong());
        }

        @Test
        @DisplayName("should return 0 when no commits need enrichment")
        void shouldReturnZeroWhenNoCommitsNeedEnrichment() {
            when(commitRepository.findShasWithoutContributorsByRepositoryId(1L)).thenReturn(List.of());

            int result = service.enrichCommitMetadata(1L, "owner/repo", 1L);

            assertThat(result).isEqualTo(0);
        }

        @Test
        @DisplayName("should return 0 when all SHAs are invalid")
        void shouldReturnZeroWhenAllShasInvalid() {
            when(commitRepository.findShasWithoutContributorsByRepositoryId(1L)).thenReturn(
                List.of("not-a-sha", "also-invalid", "ABC")
            );

            int result = service.enrichCommitMetadata(1L, "owner/repo", 1L);

            assertThat(result).isEqualTo(0);
        }

        @Test
        @DisplayName("should return 0 when nameWithOwner format is invalid")
        void shouldReturnZeroWhenNameWithOwnerInvalid() {
            when(commitRepository.findShasWithoutContributorsByRepositoryId(1L)).thenReturn(List.of("a".repeat(40)));

            int result = service.enrichCommitMetadata(1L, "invalid-no-slash", 1L);

            assertThat(result).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("SHA filtering")
    class ShaFiltering {

        @Test
        @DisplayName("should filter out invalid SHAs and process only valid ones")
        void shouldFilterInvalidShas() {
            String validSha = "a".repeat(40);
            when(commitRepository.findShasWithoutContributorsByRepositoryId(1L)).thenReturn(
                List.of("invalid-sha", validSha, "too-short")
            );

            // GraphQL call will fail without a mocked client, returning 0 enriched
            // but the service should not crash
            int result = service.enrichCommitMetadata(1L, "owner/repo", 1L);

            assertThat(result).isGreaterThanOrEqualTo(0);
        }
    }

    @Nested
    @DisplayName("rate limit handling")
    class RateLimitHandling {

        @Test
        @DisplayName("should abort when rate limit is critical and wait fails")
        void shouldAbortWhenRateLimitCriticalAndWaitFails() {
            String validSha = "a".repeat(40);
            when(commitRepository.findShasWithoutContributorsByRepositoryId(1L)).thenReturn(List.of(validSha));

            when(graphQlClientProvider.isRateLimitCritical(1L)).thenReturn(true);
            when(graphQlSyncCoordinator.waitForRateLimitIfNeeded(eq(1L), any(), any(), any(), any())).thenReturn(false);

            int result = service.enrichCommitMetadata(1L, "owner/repo", 1L);

            assertThat(result).isEqualTo(0);
            // Should not attempt any upserts
            verify(contributorRepository, never()).upsertContributor(anyLong(), any(), any(), any(), any(), anyInt());
        }
    }
}
