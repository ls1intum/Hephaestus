package de.tum.in.www1.hephaestus.gitprovider.organization.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabGroupResponse;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabPageInfo;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabProjectResponse;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.gitlab.GitLabProjectProcessor;
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

@DisplayName("GitLabGroupSyncService")
class GitLabGroupSyncServiceTest extends BaseUnitTest {

    @Mock
    private GitLabGraphQlClientProvider graphQlClientProvider;

    @Mock
    private GitLabGroupProcessor groupProcessor;

    @Mock
    private GitLabProjectProcessor projectProcessor;

    private final GitLabProperties gitLabProperties = new GitLabProperties(
        "https://gitlab.com",
        Duration.ofSeconds(30),
        Duration.ofSeconds(60),
        Duration.ofMillis(10), // fast throttle for tests
        Duration.ofMinutes(5)
    );

    private GitLabGroupSyncService service;

    @BeforeEach
    void setUp() {
        service = new GitLabGroupSyncService(graphQlClientProvider, groupProcessor, projectProcessor, gitLabProperties);
    }

    @Nested
    @DisplayName("syncGroup")
    class SyncGroup {

        @Test
        @DisplayName("null group path returns empty")
        void nullGroupPath_returnsEmpty() {
            Optional<Organization> result = service.syncGroup(1L, null);
            assertThat(result).isEmpty();
            verify(graphQlClientProvider, never()).acquirePermission();
        }

        @Test
        @DisplayName("blank group path returns empty")
        void blankGroupPath_returnsEmpty() {
            Optional<Organization> result = service.syncGroup(1L, "   ");
            assertThat(result).isEmpty();
            verify(graphQlClientProvider, never()).acquirePermission();
        }

        @Test
        @DisplayName("successful sync returns organization")
        void successfulSync_returnsOrganization() {
            Organization org = new Organization();
            org.setId(42L);
            org.setLogin("my-org");

            mockGraphQlGroupResponse(
                "my-org",
                new GitLabGroupResponse(
                    "gid://gitlab/Group/42",
                    "my-org",
                    "My Org",
                    null,
                    "https://gitlab.com/my-org",
                    null,
                    "public"
                )
            );
            when(groupProcessor.process(any(GitLabGroupResponse.class))).thenReturn(org);

            Optional<Organization> result = service.syncGroup(1L, "my-org");

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(42L);
            verify(graphQlClientProvider).acquirePermission();
            verify(graphQlClientProvider).recordSuccess();
        }

        @Test
        @DisplayName("group not found returns empty")
        void groupNotFound_returnsEmpty() {
            mockGraphQlGroupResponse("non-existent", null);

            Optional<Organization> result = service.syncGroup(1L, "non-existent");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("invalid GraphQL response returns empty and records failure")
        void invalidResponse_returnsEmptyAndRecordsFailure() {
            HttpGraphQlClient client = mockClient();
            ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
            when(response.isValid()).thenReturn(false);
            when(response.getErrors()).thenReturn(List.of());

            HttpGraphQlClient.RequestSpec requestSpec = mockRequestSpec(client, response);

            Optional<Organization> result = service.syncGroup(1L, "my-org");

            assertThat(result).isEmpty();
            verify(graphQlClientProvider).recordFailure(any());
        }

        @Test
        @DisplayName("exception returns empty and records failure")
        void exception_returnsEmptyAndRecordsFailure() {
            when(graphQlClientProvider.forScope(1L)).thenThrow(new RuntimeException("connection error"));

            Optional<Organization> result = service.syncGroup(1L, "my-org");

            assertThat(result).isEmpty();
            verify(graphQlClientProvider).recordFailure(any());
        }
    }

    @Nested
    @DisplayName("syncGroupProjects")
    class SyncGroupProjects {

        private Organization org;

        @BeforeEach
        void setUpOrg() {
            org = new Organization();
            org.setId(1L);
        }

        @Test
        @DisplayName("null group path returns aborted result")
        void nullGroupPath_returnsAborted() {
            GitLabSyncResult result = service.syncGroupProjects(1L, null);
            assertThat(result.status()).isEqualTo(GitLabSyncResult.Status.ABORTED_ERROR);
            assertThat(result.synced()).isEmpty();
            verify(graphQlClientProvider, never()).acquirePermission();
        }

        @Test
        @DisplayName("blank group path returns aborted result")
        void blankGroupPath_returnsAborted() {
            GitLabSyncResult result = service.syncGroupProjects(1L, "   ");
            assertThat(result.status()).isEqualTo(GitLabSyncResult.Status.ABORTED_ERROR);
            assertThat(result.synced()).isEmpty();
            verify(graphQlClientProvider, never()).acquirePermission();
        }

        @Test
        @DisplayName("null group on first page aborts sync")
        void nullGroupOnFirstPage_abortsSyncWithEmptyList() {
            ClientGraphQlResponse resp = mock(ClientGraphQlResponse.class);
            when(resp.isValid()).thenReturn(true);

            ClientResponseField groupField = mock(ClientResponseField.class);
            when(groupField.toEntity(GitLabGroupResponse.class)).thenReturn(DEFAULT_GROUP);
            when(resp.field("group")).thenReturn(groupField);

            HttpGraphQlClient client = mockClient();
            HttpGraphQlClient.RequestSpec requestSpec = mock(HttpGraphQlClient.RequestSpec.class);
            when(client.documentName(anyString())).thenReturn(requestSpec);
            when(requestSpec.variable(anyString(), any())).thenReturn(requestSpec);
            when(requestSpec.execute()).thenReturn(Mono.just(resp));

            when(groupProcessor.process(any())).thenReturn(null);
            when(graphQlClientProvider.getRateLimitRemaining(1L)).thenReturn(100);

            GitLabSyncResult result = service.syncGroupProjects(1L, "my-org");

            assertThat(result.synced()).isEmpty();
            verify(projectProcessor, never()).processGraphQlResponse(any(), any());
        }

        @Test
        @DisplayName("empty group returns completed with empty list")
        void emptyGroup_returnsCompleted() {
            ClientGraphQlResponse projectsResp = mockProjectsPageWithGroup(List.of(), null);

            HttpGraphQlClient client = mockClient();
            mockSequentialExecute(client, projectsResp);
            when(groupProcessor.process(any())).thenReturn(org);
            when(graphQlClientProvider.getRateLimitRemaining(1L)).thenReturn(100);

            GitLabSyncResult result = service.syncGroupProjects(1L, "my-org");

            assertThat(result.status()).isEqualTo(GitLabSyncResult.Status.COMPLETED);
            assertThat(result.synced()).isEmpty();
        }

        @Test
        @DisplayName("single page with projects returns all repositories")
        void singlePage_returnsAllProjects() {
            var proj1 = createMinimalProject("gid://gitlab/Project/10", "my-org/proj-a", "proj-a");
            var proj2 = createMinimalProject("gid://gitlab/Project/20", "my-org/proj-b", "proj-b");

            ClientGraphQlResponse projectsResp = mockProjectsPageWithGroup(List.of(proj1, proj2), null);

            HttpGraphQlClient client = mockClient();
            mockSequentialExecute(client, projectsResp);
            when(groupProcessor.process(any())).thenReturn(org);
            when(graphQlClientProvider.getRateLimitRemaining(1L)).thenReturn(100);

            Repository repo1 = new Repository();
            repo1.setId(10L);
            Repository repo2 = new Repository();
            repo2.setId(20L);
            when(projectProcessor.processGraphQlResponse(eq(proj1), any())).thenReturn(repo1);
            when(projectProcessor.processGraphQlResponse(eq(proj2), any())).thenReturn(repo2);

            GitLabSyncResult result = service.syncGroupProjects(1L, "my-org");

            assertThat(result.status()).isEqualTo(GitLabSyncResult.Status.COMPLETED);
            assertThat(result.synced()).hasSize(2);
            assertThat(result.synced()).extracting(Repository::getId).containsExactly(10L, 20L);
            assertThat(result.projectsSkipped()).isZero();
        }

        @Test
        @DisplayName("multi-page pagination fetches all pages")
        void multiPage_fetchesAllPages() {
            var proj1 = createMinimalProject("gid://gitlab/Project/10", "my-org/proj-a", "proj-a");
            var proj2 = createMinimalProject("gid://gitlab/Project/20", "my-org/proj-b", "proj-b");

            ClientGraphQlResponse page1 = mockProjectsPageWithGroup(
                List.of(proj1),
                new GitLabPageInfo(true, "cursor1")
            );
            ClientGraphQlResponse page2 = mockProjectsPage(List.of(proj2), new GitLabPageInfo(false, null));

            HttpGraphQlClient client = mockClient();
            mockSequentialExecute(client, page1, page2);
            when(groupProcessor.process(any())).thenReturn(org);
            when(graphQlClientProvider.getRateLimitRemaining(1L)).thenReturn(100);

            Repository repo1 = new Repository();
            repo1.setId(10L);
            Repository repo2 = new Repository();
            repo2.setId(20L);
            when(projectProcessor.processGraphQlResponse(eq(proj1), any())).thenReturn(repo1);
            when(projectProcessor.processGraphQlResponse(eq(proj2), any())).thenReturn(repo2);

            GitLabSyncResult result = service.syncGroupProjects(1L, "my-org");

            assertThat(result.status()).isEqualTo(GitLabSyncResult.Status.COMPLETED);
            assertThat(result.synced()).hasSize(2);
            assertThat(result.pagesCompleted()).isEqualTo(2);
        }

        @Test
        @DisplayName("null processor result is counted as skipped")
        void nullProcessorResult_countedAsSkipped() {
            var proj1 = createMinimalProject("gid://gitlab/Project/10", "my-org/proj-a", "proj-a");
            var proj2 = createMinimalProject("gid://gitlab/Project/20", "my-org/proj-b", "proj-b");

            ClientGraphQlResponse projectsResp = mockProjectsPageWithGroup(List.of(proj1, proj2), null);

            HttpGraphQlClient client = mockClient();
            mockSequentialExecute(client, projectsResp);
            when(groupProcessor.process(any())).thenReturn(org);
            when(graphQlClientProvider.getRateLimitRemaining(1L)).thenReturn(100);

            Repository repo1 = new Repository();
            repo1.setId(10L);
            when(projectProcessor.processGraphQlResponse(eq(proj1), any())).thenReturn(repo1);
            when(projectProcessor.processGraphQlResponse(eq(proj2), any())).thenReturn(null);

            GitLabSyncResult result = service.syncGroupProjects(1L, "my-org");

            assertThat(result.status()).isEqualTo(GitLabSyncResult.Status.COMPLETED_WITH_ERRORS);
            assertThat(result.synced()).hasSize(1);
            assertThat(result.projectsSkipped()).isEqualTo(1);
        }

        @Test
        @DisplayName("processor exception counts as skipped, does not abort sync")
        void processorException_countedAsSkipped() {
            var proj1 = createMinimalProject("gid://gitlab/Project/10", "my-org/proj-a", "proj-a");
            var proj2 = createMinimalProject("gid://gitlab/Project/20", "my-org/proj-b", "proj-b");

            ClientGraphQlResponse projectsResp = mockProjectsPageWithGroup(List.of(proj1, proj2), null);

            HttpGraphQlClient client = mockClient();
            mockSequentialExecute(client, projectsResp);
            when(groupProcessor.process(any())).thenReturn(org);
            when(graphQlClientProvider.getRateLimitRemaining(1L)).thenReturn(100);

            Repository repo2 = new Repository();
            repo2.setId(20L);
            when(projectProcessor.processGraphQlResponse(eq(proj1), any()))
                .thenThrow(new RuntimeException("DB error"));
            when(projectProcessor.processGraphQlResponse(eq(proj2), any())).thenReturn(repo2);

            GitLabSyncResult result = service.syncGroupProjects(1L, "my-org");

            assertThat(result.status()).isEqualTo(GitLabSyncResult.Status.COMPLETED_WITH_ERRORS);
            assertThat(result.synced()).hasSize(1);
            assertThat(result.synced().get(0).getId()).isEqualTo(20L);
            assertThat(result.projectsSkipped()).isEqualTo(1);
        }

        @Test
        @DisplayName("subgroup project uses its own group, not top-level")
        void subgroupProject_usesOwnGroup() {
            var subGroupResponse = new GitLabGroupResponse(
                "gid://gitlab/Group/99",
                "my-org/sub-team",
                "Sub Team",
                null,
                "https://gitlab.com/my-org/sub-team",
                null,
                "public"
            );

            // Project has its own group (from subgroup)
            var proj1 = new GitLabProjectResponse(
                "gid://gitlab/Project/10",
                "my-org/sub-team/proj-a",
                "proj-a",
                "https://gitlab.com/my-org/sub-team/proj-a",
                null,
                "public",
                false,
                null,
                null,
                subGroupResponse, // subgroup
                null
            );

            ClientGraphQlResponse projectsResp = mockProjectsPageWithGroup(List.of(proj1), null);

            HttpGraphQlClient client = mockClient();
            mockSequentialExecute(client, projectsResp);

            Organization subOrg = new Organization();
            subOrg.setId(99L);
            subOrg.setLogin("my-org/sub-team");

            // First call: top-level group; subsequent calls: subgroup
            when(groupProcessor.process(DEFAULT_GROUP)).thenReturn(org);
            when(groupProcessor.process(subGroupResponse)).thenReturn(subOrg);
            when(graphQlClientProvider.getRateLimitRemaining(1L)).thenReturn(100);

            Repository repo1 = new Repository();
            repo1.setId(10L);
            when(projectProcessor.processGraphQlResponse(eq(proj1), eq(subOrg))).thenReturn(repo1);

            GitLabSyncResult result = service.syncGroupProjects(1L, "my-org");

            assertThat(result.status()).isEqualTo(GitLabSyncResult.Status.COMPLETED);
            assertThat(result.synced()).hasSize(1);
            // Verify processor was called with the subgroup org, not the top-level org
            verify(projectProcessor).processGraphQlResponse(proj1, subOrg);
        }

        @Test
        @DisplayName("invalid first page response returns empty synced list")
        void invalidFirstPage_returnsEmptyList() {
            HttpGraphQlClient client = mockClient();
            ClientGraphQlResponse invalidResp = mock(ClientGraphQlResponse.class);
            when(invalidResp.isValid()).thenReturn(false);
            when(invalidResp.getErrors()).thenReturn(List.of());

            HttpGraphQlClient.RequestSpec requestSpec = mock(HttpGraphQlClient.RequestSpec.class);
            when(client.documentName(anyString())).thenReturn(requestSpec);
            when(requestSpec.variable(anyString(), any())).thenReturn(requestSpec);
            when(requestSpec.execute()).thenReturn(Mono.just(invalidResp));

            when(graphQlClientProvider.getRateLimitRemaining(1L)).thenReturn(100);

            GitLabSyncResult result = service.syncGroupProjects(1L, "my-org");

            assertThat(result.synced()).isEmpty();
            assertThat(result.status()).isEqualTo(GitLabSyncResult.Status.ABORTED_ERROR);
        }

        // -- SyncGroupProjects Helpers --

        private static final GitLabGroupResponse DEFAULT_GROUP = new GitLabGroupResponse(
            "gid://gitlab/Group/1",
            "my-org",
            "My Org",
            null,
            "https://gitlab.com/my-org",
            null,
            "public"
        );

        private GitLabProjectResponse createMinimalProject(String gid, String fullPath, String name) {
            return new GitLabProjectResponse(
                gid,
                fullPath,
                name,
                "https://gitlab.com/" + fullPath,
                null,
                "public",
                false,
                null,
                null,
                null,
                null
            );
        }

        /**
         * Mocks a projects page response that also includes inline group data
         * (used for first page where group is extracted).
         */
        @SuppressWarnings("unchecked")
        private ClientGraphQlResponse mockProjectsPageWithGroup(
            List<GitLabProjectResponse> projects,
            GitLabPageInfo pageInfo
        ) {
            ClientGraphQlResponse resp = mockProjectsPage(projects, pageInfo);

            // Add group field (inlined in GetGroupProjects query)
            ClientResponseField groupField = mock(ClientResponseField.class);
            when(groupField.toEntity(GitLabGroupResponse.class)).thenReturn(DEFAULT_GROUP);
            when(resp.field("group")).thenReturn(groupField);

            return resp;
        }

        @SuppressWarnings("unchecked")
        private ClientGraphQlResponse mockProjectsPage(List<GitLabProjectResponse> projects, GitLabPageInfo pageInfo) {
            ClientGraphQlResponse resp = mock(ClientGraphQlResponse.class);
            when(resp.isValid()).thenReturn(true);

            ClientResponseField nodesField = mock(ClientResponseField.class);
            when(nodesField.<GitLabProjectResponse>toEntityList(any(Class.class))).thenReturn(projects);
            when(resp.field("group.projects.nodes")).thenReturn(nodesField);

            ClientResponseField pageInfoField = mock(ClientResponseField.class);
            when(pageInfoField.<GitLabPageInfo>toEntity(any(Class.class))).thenReturn(pageInfo);
            when(resp.field("group.projects.pageInfo")).thenReturn(pageInfoField);

            return resp;
        }

        @SafeVarargs
        private void mockSequentialExecute(
            HttpGraphQlClient client,
            ClientGraphQlResponse first,
            ClientGraphQlResponse... rest
        ) {
            HttpGraphQlClient.RequestSpec requestSpec = mock(HttpGraphQlClient.RequestSpec.class);
            when(client.documentName(anyString())).thenReturn(requestSpec);
            when(requestSpec.variable(anyString(), any())).thenReturn(requestSpec);

            @SuppressWarnings("unchecked")
            Mono<ClientGraphQlResponse>[] restMonos = new Mono[rest.length];
            for (int i = 0; i < rest.length; i++) {
                restMonos[i] = Mono.just(rest[i]);
            }
            when(requestSpec.execute()).thenReturn(Mono.just(first), restMonos);
        }
    }

    // -- Helpers --

    private HttpGraphQlClient mockClient() {
        HttpGraphQlClient client = mock(HttpGraphQlClient.class);
        when(graphQlClientProvider.forScope(any())).thenReturn(client);
        return client;
    }

    @SuppressWarnings("unchecked")
    private void mockGraphQlGroupResponse(String groupPath, GitLabGroupResponse groupResponse) {
        HttpGraphQlClient client = mockClient();
        ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
        when(response.isValid()).thenReturn(true);

        ClientResponseField groupField = mock(ClientResponseField.class);
        when(groupField.toEntity(GitLabGroupResponse.class)).thenReturn(groupResponse);
        when(response.field("group")).thenReturn(groupField);

        HttpGraphQlClient.RequestSpec requestSpec = mock(HttpGraphQlClient.RequestSpec.class);
        when(client.documentName(anyString())).thenReturn(requestSpec);
        when(requestSpec.variable(anyString(), any())).thenReturn(requestSpec);
        when(requestSpec.execute()).thenReturn(Mono.just(response));
    }

    @SuppressWarnings("unchecked")
    private HttpGraphQlClient.RequestSpec mockRequestSpec(HttpGraphQlClient client, ClientGraphQlResponse response) {
        HttpGraphQlClient.RequestSpec requestSpec = mock(HttpGraphQlClient.RequestSpec.class);
        when(client.documentName(anyString())).thenReturn(requestSpec);
        when(requestSpec.variable(anyString(), any())).thenReturn(requestSpec);
        when(requestSpec.execute()).thenReturn(Mono.just(response));
        return requestSpec;
    }
}
