package de.tum.in.www1.hephaestus.gitprovider.repository.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabGroupResponse;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabProjectResponse;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.gitlab.GitLabGroupProcessor;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.ClientResponseField;
import org.springframework.graphql.client.HttpGraphQlClient;
import reactor.core.publisher.Mono;

@DisplayName("GitLabProjectSyncService")
class GitLabProjectSyncServiceTest extends BaseUnitTest {

    private static final Long TEST_PROVIDER_ID = 100L;

    @Mock
    private GitLabGraphQlClientProvider graphQlClientProvider;

    @Mock
    private GitLabProjectProcessor projectProcessor;

    @Mock
    private GitLabGroupProcessor groupProcessor;

    @Mock
    private GitProviderRepository gitProviderRepository;

    private final GitLabProperties gitLabProperties = new GitLabProperties(
        "https://gitlab.com",
        Duration.ofSeconds(30),
        Duration.ofSeconds(60),
        Duration.ofMillis(10),
        Duration.ofMinutes(5)
    );

    private GitLabProjectSyncService service;

    @BeforeEach
    void setUp() {
        GitProvider gitLabProvider = mock(GitProvider.class);
        lenient().when(gitLabProvider.getId()).thenReturn(TEST_PROVIDER_ID);
        lenient()
            .when(gitProviderRepository.findByTypeAndServerUrl(GitProviderType.GITLAB, "https://gitlab.com"))
            .thenReturn(Optional.of(gitLabProvider));

        service = new GitLabProjectSyncService(
            graphQlClientProvider,
            projectProcessor,
            groupProcessor,
            gitLabProperties,
            gitProviderRepository
        );
    }

    @Nested
    @DisplayName("syncProject")
    class SyncProject {

        @Test
        @DisplayName("null project path returns empty")
        void nullProjectPath_returnsEmpty() {
            Optional<Repository> result = service.syncProject(1L, null);
            assertThat(result).isEmpty();
            verify(graphQlClientProvider, never()).acquirePermission();
        }

        @Test
        @DisplayName("blank project path returns empty")
        void blankProjectPath_returnsEmpty() {
            Optional<Repository> result = service.syncProject(1L, "  ");
            assertThat(result).isEmpty();
            verify(graphQlClientProvider, never()).acquirePermission();
        }

        @Test
        @DisplayName("successful sync returns repository with parent group")
        void successfulSync_returnsRepositoryWithGroup() {
            var groupData = new GitLabGroupResponse(
                "gid://gitlab/Group/42",
                "my-org",
                "My Org",
                null,
                "https://gitlab.com/my-org",
                null,
                "public"
            );
            var projectData = new GitLabProjectResponse(
                "gid://gitlab/Project/123",
                "my-org/my-project",
                "my-project",
                "https://gitlab.com/my-org/my-project",
                "Description",
                "public",
                false,
                "2024-01-15T10:30:00Z",
                null,
                groupData,
                new GitLabProjectResponse.RepositoryInfo("main")
            );

            mockGraphQlProjectResponse(projectData);

            Organization org = new Organization();
            org.setId(42L);
            when(groupProcessor.process(any(GitLabGroupResponse.class), anyLong())).thenReturn(org);

            Repository repo = new Repository();
            repo.setId(123L);
            repo.setNameWithOwner("my-org/my-project");
            when(projectProcessor.processGraphQlResponse(any(), any(), any())).thenReturn(repo);

            Optional<Repository> result = service.syncProject(1L, "my-org/my-project");

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(123L);
            verify(graphQlClientProvider).acquirePermission();
            verify(graphQlClientProvider).recordSuccess();
            verify(groupProcessor).process(groupData, TEST_PROVIDER_ID);
            verify(projectProcessor).processGraphQlResponse(eq(projectData), eq(org), any());
        }

        @Test
        @DisplayName("project without group syncs with null organization")
        void projectWithoutGroup_syncsWithNullOrg() {
            var projectData = new GitLabProjectResponse(
                "gid://gitlab/Project/456",
                "user/personal-project",
                "personal-project",
                "https://gitlab.com/user/personal-project",
                null,
                "private",
                false,
                null,
                null,
                null, // no group
                null
            );

            mockGraphQlProjectResponse(projectData);

            Repository repo = new Repository();
            repo.setId(456L);
            when(projectProcessor.processGraphQlResponse(any(), any(), any())).thenReturn(repo);

            Optional<Repository> result = service.syncProject(1L, "user/personal-project");

            assertThat(result).isPresent();
            verify(groupProcessor, never()).process(any(), anyLong());
            verify(projectProcessor).processGraphQlResponse(eq(projectData), eq(null), any());
        }

        @Test
        @DisplayName("project not found returns empty")
        void projectNotFound_returnsEmpty() {
            mockGraphQlProjectResponse(null);

            Optional<Repository> result = service.syncProject(1L, "non-existent/project");

            assertThat(result).isEmpty();
            verify(projectProcessor, never()).processGraphQlResponse(any(), any(), any());
        }

        @Test
        @DisplayName("invalid GraphQL response returns empty and records failure")
        void invalidResponse_returnsEmptyAndRecordsFailure() {
            HttpGraphQlClient client = mock(HttpGraphQlClient.class);
            when(graphQlClientProvider.forScope(any())).thenReturn(client);

            ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
            when(response.isValid()).thenReturn(false);
            when(response.getErrors()).thenReturn(List.of());

            HttpGraphQlClient.RequestSpec requestSpec = mock(HttpGraphQlClient.RequestSpec.class);
            when(client.documentName(anyString())).thenReturn(requestSpec);
            when(requestSpec.variable(anyString(), any())).thenReturn(requestSpec);
            when(requestSpec.execute()).thenReturn(Mono.just(response));

            Optional<Repository> result = service.syncProject(1L, "my-org/my-project");

            assertThat(result).isEmpty();
            verify(graphQlClientProvider).recordFailure(any());
        }

        @Test
        @DisplayName("group processor returning null aborts project sync")
        void groupProcessorReturnsNull_abortsSync() {
            var groupData = new GitLabGroupResponse(
                "gid://gitlab/Group/42",
                "my-org",
                "My Org",
                null,
                "https://gitlab.com/my-org",
                null,
                "public"
            );
            var projectData = new GitLabProjectResponse(
                "gid://gitlab/Project/789",
                "my-org/proj",
                "proj",
                "https://gitlab.com/my-org/proj",
                null,
                "public",
                false,
                null,
                null,
                groupData,
                null
            );

            mockGraphQlProjectResponse(projectData);
            when(groupProcessor.process(any(), anyLong())).thenReturn(null); // group processor rejects

            Optional<Repository> result = service.syncProject(1L, "my-org/proj");

            assertThat(result).isEmpty();
            verify(projectProcessor, never()).processGraphQlResponse(any(), any(), any());
        }

        @Test
        @DisplayName("exception returns empty and records failure")
        void exception_returnsEmptyAndRecordsFailure() {
            when(graphQlClientProvider.forScope(any())).thenThrow(new RuntimeException("connection refused"));

            Optional<Repository> result = service.syncProject(1L, "my-org/my-project");

            assertThat(result).isEmpty();
            verify(graphQlClientProvider).recordFailure(any());
        }
    }

    // -- Helpers --

    @SuppressWarnings("unchecked")
    private void mockGraphQlProjectResponse(GitLabProjectResponse projectResponse) {
        HttpGraphQlClient client = mock(HttpGraphQlClient.class);
        when(graphQlClientProvider.forScope(any())).thenReturn(client);

        ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
        when(response.isValid()).thenReturn(true);

        ClientResponseField projectField = mock(ClientResponseField.class);
        when(projectField.toEntity(GitLabProjectResponse.class)).thenReturn(projectResponse);
        when(response.field("project")).thenReturn(projectField);

        HttpGraphQlClient.RequestSpec requestSpec = mock(HttpGraphQlClient.RequestSpec.class);
        when(client.documentName(anyString())).thenReturn(requestSpec);
        when(requestSpec.variable(anyString(), any())).thenReturn(requestSpec);
        when(requestSpec.execute()).thenReturn(Mono.just(response));
    }
}
