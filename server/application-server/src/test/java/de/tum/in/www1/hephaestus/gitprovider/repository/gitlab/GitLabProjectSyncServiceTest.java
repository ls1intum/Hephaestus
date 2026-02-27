package de.tum.in.www1.hephaestus.gitprovider.repository.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Mock
    private GitLabGraphQlClientProvider graphQlClientProvider;

    @Mock
    private GitLabProjectProcessor projectProcessor;

    @Mock
    private GitLabGroupProcessor groupProcessor;

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
        service = new GitLabProjectSyncService(
            graphQlClientProvider,
            projectProcessor,
            groupProcessor,
            gitLabProperties
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
            when(groupProcessor.process(any(GitLabGroupResponse.class))).thenReturn(org);

            Repository repo = new Repository();
            repo.setId(123L);
            repo.setNameWithOwner("my-org/my-project");
            when(projectProcessor.processGraphQlResponse(any(), any())).thenReturn(repo);

            Optional<Repository> result = service.syncProject(1L, "my-org/my-project");

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(123L);
            verify(graphQlClientProvider).acquirePermission();
            verify(graphQlClientProvider).recordSuccess();
            verify(groupProcessor).process(groupData);
            verify(projectProcessor).processGraphQlResponse(projectData, org);
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
            when(projectProcessor.processGraphQlResponse(any(), any())).thenReturn(repo);

            Optional<Repository> result = service.syncProject(1L, "user/personal-project");

            assertThat(result).isPresent();
            verify(groupProcessor, never()).process(any());
            verify(projectProcessor).processGraphQlResponse(projectData, null);
        }

        @Test
        @DisplayName("project not found returns empty")
        void projectNotFound_returnsEmpty() {
            mockGraphQlProjectResponse(null);

            Optional<Repository> result = service.syncProject(1L, "non-existent/project");

            assertThat(result).isEmpty();
            verify(projectProcessor, never()).processGraphQlResponse(any(), any());
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
        @DisplayName("group processor returning null still syncs project with null org")
        void groupProcessorReturnsNull_syncsWithNullOrg() {
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
            when(groupProcessor.process(any())).thenReturn(null); // group processor rejects
            Repository repo = new Repository();
            repo.setId(789L);
            when(projectProcessor.processGraphQlResponse(projectData, null)).thenReturn(repo);

            Optional<Repository> result = service.syncProject(1L, "my-org/proj");

            assertThat(result).isPresent();
            verify(projectProcessor).processGraphQlResponse(projectData, null);
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
