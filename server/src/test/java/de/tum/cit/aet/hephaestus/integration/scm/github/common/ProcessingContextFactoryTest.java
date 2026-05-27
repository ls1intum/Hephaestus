package de.tum.cit.aet.hephaestus.integration.scm.github.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.RepositoryScopeFilter;
import de.tum.cit.aet.hephaestus.integration.core.spi.ScopeIdResolver;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.ProcessingContext;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.Organization;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.github.repository.dto.GitHubRepositoryRefDTO;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ProcessingContextFactory}.
 * <p>
 * These tests verify that the factory correctly applies repository filtering
 * to webhook events, preventing processing of repositories that should be skipped.
 */
@Tag("unit")
class ProcessingContextFactoryTest {

    private RepositoryRepository repositoryRepository;
    private ScopeIdResolver scopeIdResolver;
    private RepositoryScopeFilter repositoryScopeFilter;
    private ProcessingContextFactory factory;

    @BeforeEach
    void setUp() {
        repositoryRepository = mock(RepositoryRepository.class);
        scopeIdResolver = mock(ScopeIdResolver.class);
        repositoryScopeFilter = mock(RepositoryScopeFilter.class);
        factory = new ProcessingContextFactory(repositoryRepository, scopeIdResolver, repositoryScopeFilter);
    }

    @Nested
    class ForWebhookEvent {

        @Test
        void returnsEmpty_whenRepositoryDataMissing() {
            GitHubWebhookEvent event = mock(GitHubWebhookEvent.class);
            when(event.repository()).thenReturn(null);

            Optional<ProcessingContext> result = factory.forWebhookEvent(event);

            assertThat(result).isEmpty();
            verifyNoInteractions(repositoryScopeFilter);
            verifyNoInteractions(repositoryRepository);
        }

        @Test
        void returnsEmpty_whenRepositoryFiltered() {
            String repoFullName = "ls1intum/artemis-ansible-collection";
            GitHubWebhookEvent event = createEventWithRepo(repoFullName);
            when(repositoryScopeFilter.isRepositoryAllowed(repoFullName)).thenReturn(false);

            Optional<ProcessingContext> result = factory.forWebhookEvent(event);

            assertThat(result).isEmpty();
            verify(repositoryScopeFilter).isRepositoryAllowed(repoFullName);
            verifyNoInteractions(repositoryRepository); // Should NOT query DB for filtered repos
        }

        @Test
        void returnsEmpty_whenRepositoryNotInDatabase() {
            String repoFullName = "ls1intum/Hephaestus";
            GitHubWebhookEvent event = createEventWithRepo(repoFullName);
            when(repositoryScopeFilter.isRepositoryAllowed(repoFullName)).thenReturn(true);
            when(repositoryRepository.findByNameWithOwnerWithOrganization(repoFullName)).thenReturn(Optional.empty());

            Optional<ProcessingContext> result = factory.forWebhookEvent(event);

            assertThat(result).isEmpty();
        }

        @Test
        void returnsContext_whenRepositoryAllowedAndExists() {
            String repoFullName = "ls1intum/Hephaestus";
            GitHubWebhookEvent event = createEventWithRepo(repoFullName);
            Repository repository = mock(Repository.class);
            when(repository.getOrganization()).thenReturn(null);
            when(repository.getNameWithOwner()).thenReturn(repoFullName);

            when(repositoryScopeFilter.isRepositoryAllowed(repoFullName)).thenReturn(true);
            when(repositoryRepository.findByNameWithOwnerWithOrganization(repoFullName)).thenReturn(
                Optional.of(repository)
            );
            when(scopeIdResolver.findScopeIdByRepositoryName(repoFullName)).thenReturn(Optional.of(42L));

            Optional<ProcessingContext> result = factory.forWebhookEvent(event);

            assertThat(result).isPresent();
            assertThat(result.get().repository()).isEqualTo(repository);
            assertThat(result.get().scopeId()).isEqualTo(42L);
        }

        @Test
        void checksFilterBeforeDatabase() {
            String repoFullName = "ls1intum/some-repo";
            GitHubWebhookEvent event = createEventWithRepo(repoFullName);
            when(repositoryScopeFilter.isRepositoryAllowed(repoFullName)).thenReturn(false);

            factory.forWebhookEvent(event);

            // Assert - filter should be checked, but repository should NOT be queried
            verify(repositoryScopeFilter).isRepositoryAllowed(repoFullName);
            verifyNoInteractions(repositoryRepository);
        }

        private GitHubWebhookEvent createEventWithRepo(String fullName) {
            GitHubWebhookEvent event = mock(GitHubWebhookEvent.class);
            GitHubRepositoryRefDTO repoInfo = new GitHubRepositoryRefDTO(
                1L,
                "node_id",
                "repo",
                fullName,
                false,
                "url",
                null
            );
            when(event.repository()).thenReturn(repoInfo);
            when(event.action()).thenReturn("opened");
            return event;
        }
    }

    @Nested
    class ScopeIdResolution {

        @Test
        void resolvesScopeId_byOrgLogin_forOrgOwnedRepos() {
            String repoFullName = "ls1intum/Hephaestus";
            String orgLogin = "ls1intum";
            Long expectedScopeId = 100L;

            GitHubWebhookEvent event = createEventWithRepo(repoFullName);
            Repository repository = mock(Repository.class);
            Organization org = mock(Organization.class);
            when(org.getLogin()).thenReturn(orgLogin);
            when(repository.getOrganization()).thenReturn(org);
            when(repository.getNameWithOwner()).thenReturn(repoFullName);

            when(repositoryScopeFilter.isRepositoryAllowed(repoFullName)).thenReturn(true);
            when(repositoryRepository.findByNameWithOwnerWithOrganization(repoFullName)).thenReturn(
                Optional.of(repository)
            );
            when(scopeIdResolver.findScopeIdByOrgLogin(orgLogin)).thenReturn(Optional.of(expectedScopeId));

            Optional<ProcessingContext> result = factory.forWebhookEvent(event);

            assertThat(result).isPresent();
            assertThat(result.get().scopeId()).isEqualTo(expectedScopeId);
            verify(scopeIdResolver).findScopeIdByOrgLogin(orgLogin);
            verify(scopeIdResolver, never()).findScopeIdByRepositoryName(repoFullName);
        }

        @Test
        void resolvesScopeId_byRepoName_forPersonalRepos() {
            String repoFullName = "octocat/hello-world";
            Long expectedScopeId = 200L;

            GitHubWebhookEvent event = createEventWithRepo(repoFullName);
            Repository repository = mock(Repository.class);
            when(repository.getOrganization()).thenReturn(null); // Personal repo - no org!
            when(repository.getNameWithOwner()).thenReturn(repoFullName);

            when(repositoryScopeFilter.isRepositoryAllowed(repoFullName)).thenReturn(true);
            when(repositoryRepository.findByNameWithOwnerWithOrganization(repoFullName)).thenReturn(
                Optional.of(repository)
            );
            when(scopeIdResolver.findScopeIdByRepositoryName(repoFullName)).thenReturn(Optional.of(expectedScopeId));

            Optional<ProcessingContext> result = factory.forWebhookEvent(event);

            assertThat(result).isPresent();
            assertThat(result.get().scopeId()).isEqualTo(expectedScopeId);
            // Should NOT try org lookup for personal repos
            verify(scopeIdResolver, never()).findScopeIdByOrgLogin(org.mockito.ArgumentMatchers.any());
            // Should use repository-based lookup
            verify(scopeIdResolver).findScopeIdByRepositoryName(repoFullName);
        }

        @Test
        void fallsBackToRepoLookup_whenOrgLookupFails() {
            String repoFullName = "some-org/some-repo";
            String orgLogin = "some-org";
            Long expectedScopeId = 300L;

            GitHubWebhookEvent event = createEventWithRepo(repoFullName);
            Repository repository = mock(Repository.class);
            Organization org = mock(Organization.class);
            when(org.getLogin()).thenReturn(orgLogin);
            when(repository.getOrganization()).thenReturn(org);
            when(repository.getNameWithOwner()).thenReturn(repoFullName);

            when(repositoryScopeFilter.isRepositoryAllowed(repoFullName)).thenReturn(true);
            when(repositoryRepository.findByNameWithOwnerWithOrganization(repoFullName)).thenReturn(
                Optional.of(repository)
            );
            // Org lookup fails
            when(scopeIdResolver.findScopeIdByOrgLogin(orgLogin)).thenReturn(Optional.empty());
            // Fallback to repo lookup succeeds
            when(scopeIdResolver.findScopeIdByRepositoryName(repoFullName)).thenReturn(Optional.of(expectedScopeId));

            Optional<ProcessingContext> result = factory.forWebhookEvent(event);

            assertThat(result).isPresent();
            assertThat(result.get().scopeId()).isEqualTo(expectedScopeId);
            // Should try org lookup first
            verify(scopeIdResolver).findScopeIdByOrgLogin(orgLogin);
            // Then fall back to repo lookup
            verify(scopeIdResolver).findScopeIdByRepositoryName(repoFullName);
        }

        @Test
        void returnsNullScopeId_whenNoResolutionSucceeds() {
            String repoFullName = "unknown/unknown-repo";

            GitHubWebhookEvent event = createEventWithRepo(repoFullName);
            Repository repository = mock(Repository.class);
            when(repository.getOrganization()).thenReturn(null);
            when(repository.getNameWithOwner()).thenReturn(repoFullName);

            when(repositoryScopeFilter.isRepositoryAllowed(repoFullName)).thenReturn(true);
            when(repositoryRepository.findByNameWithOwnerWithOrganization(repoFullName)).thenReturn(
                Optional.of(repository)
            );
            when(scopeIdResolver.findScopeIdByRepositoryName(repoFullName)).thenReturn(Optional.empty());

            Optional<ProcessingContext> result = factory.forWebhookEvent(event);

            assertThat(result).isPresent();
            assertThat(result.get().scopeId()).isNull();
        }

        private GitHubWebhookEvent createEventWithRepo(String fullName) {
            GitHubWebhookEvent event = mock(GitHubWebhookEvent.class);
            GitHubRepositoryRefDTO repoInfo = new GitHubRepositoryRefDTO(
                1L,
                "node_id",
                "repo",
                fullName,
                false,
                "url",
                null
            );
            when(event.repository()).thenReturn(repoInfo);
            when(event.action()).thenReturn("opened");
            return event;
        }
    }
}
