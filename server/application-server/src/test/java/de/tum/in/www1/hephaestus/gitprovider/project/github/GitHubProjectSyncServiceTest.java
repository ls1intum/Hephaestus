package de.tum.in.www1.hephaestus.gitprovider.project.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.exception.InstallationNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier.Category;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier.ClassificationResult;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.BackfillStateProvider;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPageInfo;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2Connection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2FieldConfigurationConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2StatusUpdateConnection;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.Project;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectFieldRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectFieldValueRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectItemRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectStatusUpdateRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectDTO;
import de.tum.in.www1.hephaestus.gitprovider.sync.SyncResult;
import de.tum.in.www1.hephaestus.gitprovider.sync.SyncSchedulerProperties;
import de.tum.in.www1.hephaestus.gitprovider.sync.SyncSchedulerProperties.BackfillProperties;
import de.tum.in.www1.hephaestus.gitprovider.sync.SyncSchedulerProperties.FilterProperties;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.ClientResponseField;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

/**
 * Unit tests for {@link GitHubProjectSyncService}.
 */
class GitHubProjectSyncServiceTest extends BaseUnitTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectItemRepository projectItemRepository;

    @Mock
    private ProjectFieldRepository projectFieldRepository;

    @Mock
    private ProjectFieldValueRepository projectFieldValueRepository;

    @Mock
    private ProjectStatusUpdateRepository statusUpdateRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private GitHubGraphQlClientProvider graphQlClientProvider;

    @Mock
    private GitHubProjectProcessor projectProcessor;

    @Mock
    private GitHubProjectItemProcessor itemProcessor;

    @Mock
    private GitHubProjectStatusUpdateProcessor statusUpdateProcessor;

    @Mock
    private BackfillStateProvider backfillStateProvider;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private GitHubExceptionClassifier exceptionClassifier;

    @Mock
    private HttpGraphQlClient client;

    @Mock
    private HttpGraphQlClient.RequestSpec requestSpec;

    @Mock
    private PlatformTransactionManager transactionManager;

    private GitHubProjectSyncService service;

    private static final Long SCOPE_ID = 100L;
    private static final String ORG_LOGIN = "test-org";
    private static final Long ORG_ID = 42L;
    private static final Long ORG_DB_ID = 1L;

    private GitHubSyncProperties syncProperties;
    private SyncSchedulerProperties syncSchedulerProperties;

    @BeforeEach
    void setUp() {
        syncProperties = new GitHubSyncProperties(
            Duration.ofSeconds(30), // graphqlTimeout
            Duration.ofSeconds(60), // extendedGraphqlTimeout
            Duration.ofSeconds(120), // backfillGraphqlTimeout
            Duration.ZERO, // paginationThrottle - zero to avoid Thread.sleep
            true, // incrementalSyncEnabled
            Duration.ofMinutes(5), // incrementalSyncBuffer
            10 // backfillPrPageSize
        );

        syncSchedulerProperties = new SyncSchedulerProperties(
            true, // runOnStartup
            7, // timeframeDays
            "0 0 3 * * *", // cron
            15, // cooldownMinutes
            new BackfillProperties(false, 50, 100, 60),
            new FilterProperties(Set.of(), Set.of(), Set.of()) // empty = all allowed
        );

        // TransactionTemplate mocking: execute callbacks immediately
        lenient()
            .when(transactionTemplate.execute(any()))
            .thenAnswer(invocation -> {
                TransactionCallback<?> callback = invocation.getArgument(0);
                return callback.doInTransaction(null);
            });
        lenient()
            .doAnswer(invocation -> {
                java.util.function.Consumer<org.springframework.transaction.TransactionStatus> callback =
                    invocation.getArgument(0);
                callback.accept(null);
                return null;
            })
            .when(transactionTemplate)
            .executeWithoutResult(any());
        lenient().when(transactionTemplate.getTransactionManager()).thenReturn(transactionManager);

        service = new GitHubProjectSyncService(
            projectRepository,
            projectItemRepository,
            projectFieldRepository,
            projectFieldValueRepository,
            statusUpdateRepository,
            organizationRepository,
            graphQlClientProvider,
            projectProcessor,
            itemProcessor,
            statusUpdateProcessor,
            backfillStateProvider,
            transactionTemplate,
            syncProperties,
            syncSchedulerProperties,
            exceptionClassifier
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // Helper methods
    // ═══════════════════════════════════════════════════════════════

    private Organization createOrganization() {
        Organization org = new Organization();
        org.setId(ORG_DB_ID);
        org.setGithubId(ORG_ID);
        org.setLogin(ORG_LOGIN);
        org.setName("Test Organization");
        org.setHtmlUrl("https://github.com/test-org");
        return org;
    }

    private Project createProject(Long id, String nodeId, int number) {
        Project project = new Project();
        project.setId(id);
        project.setNodeId(nodeId);
        project.setOwnerType(Project.OwnerType.ORGANIZATION);
        project.setOwnerId(ORG_DB_ID);
        project.setNumber(number);
        project.setTitle("Project " + number);
        return project;
    }

    private GHProjectV2 createGraphQlProject(long databaseId, String nodeId, int number) {
        GHProjectV2 ghProject = new GHProjectV2();
        ghProject.setFullDatabaseId(BigInteger.valueOf(databaseId));
        ghProject.setId(nodeId);
        ghProject.setNumber(number);
        ghProject.setTitle("Project " + number);
        ghProject.setClosed(false);
        ghProject.setPublic(true);
        ghProject.setTemplate(false);
        return ghProject;
    }

    private void mockGraphQlClientForScope() {
        when(graphQlClientProvider.forScope(SCOPE_ID)).thenReturn(client);
    }

    private void mockGraphQlRequestChain() {
        mockGraphQlRequestChain(anyString());
    }

    private void mockGraphQlRequestChain(String documentName) {
        when(client.documentName(documentName)).thenReturn(requestSpec);
        when(requestSpec.variable(anyString(), any())).thenReturn(requestSpec);
    }

    private ClientGraphQlResponse mockValidGraphQlResponse(String fieldPath, Object entity) {
        ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
        ClientResponseField field = mock(ClientResponseField.class);
        lenient().when(response.isValid()).thenReturn(true);
        lenient().when(response.field(fieldPath)).thenReturn(field);
        lenient().when(field.toEntity(any(Class.class))).thenReturn(entity);
        return response;
    }

    private ClientGraphQlResponse mockInvalidGraphQlResponse() {
        ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
        when(response.isValid()).thenReturn(false);
        when(response.getErrors()).thenReturn(List.of());
        return response;
    }

    private GHProjectV2Connection createProjectConnection(List<GHProjectV2> nodes, boolean hasNextPage) {
        GHPageInfo pageInfo = new GHPageInfo(hasNextPage ? "cursor1" : null, hasNextPage, false, null);
        GHProjectV2Connection connection = new GHProjectV2Connection();
        connection.setNodes(nodes);
        connection.setPageInfo(pageInfo);
        return connection;
    }

    // ═══════════════════════════════════════════════════════════════
    // syncProjectsForOrganization tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("syncProjectsForOrganization")
    class SyncProjectsForOrganization {

        @Test
        @DisplayName("should return completed(0) when orgLogin is null")
        void shouldReturnCompletedZeroWhenOrgLoginIsNull() {
            // Act
            SyncResult result = service.syncProjectsForOrganization(SCOPE_ID, null);

            // Assert
            assertThat(result.status()).isEqualTo(SyncResult.Status.COMPLETED);
            assertThat(result.count()).isZero();
        }

        @Test
        @DisplayName("should return completed(0) when orgLogin is blank")
        void shouldReturnCompletedZeroWhenOrgLoginIsBlank() {
            // Act
            SyncResult result = service.syncProjectsForOrganization(SCOPE_ID, "   ");

            // Assert
            assertThat(result.status()).isEqualTo(SyncResult.Status.COMPLETED);
            assertThat(result.count()).isZero();
        }

        @Test
        @DisplayName("should return completed(0) when organization is not found")
        void shouldReturnCompletedZeroWhenOrganizationNotFound() {
            // Arrange
            when(organizationRepository.findByLoginIgnoreCase(ORG_LOGIN)).thenReturn(Optional.empty());

            // Act
            SyncResult result = service.syncProjectsForOrganization(SCOPE_ID, ORG_LOGIN);

            // Assert
            assertThat(result.status()).isEqualTo(SyncResult.Status.COMPLETED);
            assertThat(result.count()).isZero();
        }

        @Test
        @DisplayName("should sync a single page of projects successfully")
        void shouldSyncSinglePageOfProjectsSuccessfully() {
            // Arrange
            Organization org = createOrganization();
            when(organizationRepository.findByLoginIgnoreCase(ORG_LOGIN)).thenReturn(Optional.of(org));
            mockGraphQlClientForScope();
            mockGraphQlRequestChain("GetOrganizationProjects");

            GHProjectV2 ghProject = createGraphQlProject(100L, "PVT_node1", 1);
            GHProjectV2Connection connection = createProjectConnection(List.of(ghProject), false);
            ClientGraphQlResponse response = mockValidGraphQlResponse("organization.projectsV2", connection);
            when(requestSpec.execute()).thenReturn(Mono.just(response));

            Project processedProject = createProject(100L, "PVT_node1", 1);
            when(projectRepository.findById(100L)).thenReturn(Optional.empty());
            when(
                projectProcessor.process(
                    any(GitHubProjectDTO.class),
                    eq(Project.OwnerType.ORGANIZATION),
                    eq(ORG_DB_ID),
                    any(ProcessingContext.class)
                )
            ).thenReturn(processedProject);
            when(projectRepository.save(any(Project.class))).thenReturn(processedProject);

            // Act
            SyncResult result = service.syncProjectsForOrganization(SCOPE_ID, ORG_LOGIN);

            // Assert
            assertThat(result.status()).isEqualTo(SyncResult.Status.COMPLETED);
            assertThat(result.count()).isEqualTo(1);
            verify(projectProcessor).process(
                any(GitHubProjectDTO.class),
                eq(Project.OwnerType.ORGANIZATION),
                eq(ORG_DB_ID),
                any(ProcessingContext.class)
            );
            verify(projectRepository).save(any(Project.class));
        }

        @Test
        @DisplayName("should skip projects within cooldown period")
        void shouldSkipProjectsWithinCooldownPeriod() {
            // Arrange
            Organization org = createOrganization();
            when(organizationRepository.findByLoginIgnoreCase(ORG_LOGIN)).thenReturn(Optional.of(org));
            mockGraphQlClientForScope();
            mockGraphQlRequestChain("GetOrganizationProjects");

            GHProjectV2 ghProject = createGraphQlProject(100L, "PVT_node1", 1);
            GHProjectV2Connection connection = createProjectConnection(List.of(ghProject), false);
            ClientGraphQlResponse response = mockValidGraphQlResponse("organization.projectsV2", connection);
            when(requestSpec.execute()).thenReturn(Mono.just(response));

            // Project was synced 5 minutes ago (within 15-minute cooldown)
            Project existingProject = createProject(100L, "PVT_node1", 1);
            existingProject.setLastSyncAt(Instant.now().minusSeconds(5 * 60));
            when(projectRepository.findById(100L)).thenReturn(Optional.of(existingProject));

            // Act
            SyncResult result = service.syncProjectsForOrganization(SCOPE_ID, ORG_LOGIN);

            // Assert
            assertThat(result.status()).isEqualTo(SyncResult.Status.COMPLETED);
            assertThat(result.count()).isZero(); // Skipped due to cooldown
            verify(projectProcessor, never()).process(any(), any(), any(), any());
        }

        @Test
        @DisplayName("should abort with ABORTED_RATE_LIMIT when rate limit is critical")
        void shouldAbortWhenRateLimitCritical() {
            // Arrange
            Organization org = createOrganization();
            when(organizationRepository.findByLoginIgnoreCase(ORG_LOGIN)).thenReturn(Optional.of(org));
            mockGraphQlClientForScope();
            mockGraphQlRequestChain("GetOrganizationProjects");

            GHProjectV2 ghProject = createGraphQlProject(100L, "PVT_node1", 1);
            GHProjectV2Connection connection = createProjectConnection(List.of(ghProject), true);
            ClientGraphQlResponse response = mockValidGraphQlResponse("organization.projectsV2", connection);
            when(requestSpec.execute()).thenReturn(Mono.just(response));

            // Rate limit becomes critical after first response
            when(graphQlClientProvider.isRateLimitCritical(SCOPE_ID)).thenReturn(true);

            // Act
            SyncResult result = service.syncProjectsForOrganization(SCOPE_ID, ORG_LOGIN);

            // Assert
            assertThat(result.status()).isEqualTo(SyncResult.Status.ABORTED_RATE_LIMIT);
            verify(graphQlClientProvider).trackRateLimit(eq(SCOPE_ID), any(ClientGraphQlResponse.class));
        }

        @Test
        @DisplayName("should abort with ABORTED_ERROR when GraphQL response is invalid")
        void shouldAbortWhenGraphQlResponseInvalid() {
            // Arrange
            Organization org = createOrganization();
            when(organizationRepository.findByLoginIgnoreCase(ORG_LOGIN)).thenReturn(Optional.of(org));
            mockGraphQlClientForScope();
            mockGraphQlRequestChain("GetOrganizationProjects");

            ClientGraphQlResponse invalidResponse = mockInvalidGraphQlResponse();
            when(requestSpec.execute()).thenReturn(Mono.just(invalidResponse));

            // Act
            SyncResult result = service.syncProjectsForOrganization(SCOPE_ID, ORG_LOGIN);

            // Assert
            assertThat(result.status()).isEqualTo(SyncResult.Status.ABORTED_ERROR);
            assertThat(result.count()).isZero();
        }

        @Test
        @DisplayName("should rethrow InstallationNotFoundException")
        void shouldRethrowInstallationNotFoundException() {
            // Arrange
            Organization org = createOrganization();
            when(organizationRepository.findByLoginIgnoreCase(ORG_LOGIN)).thenReturn(Optional.of(org));
            mockGraphQlClientForScope();
            mockGraphQlRequestChain("GetOrganizationProjects");

            when(requestSpec.execute()).thenReturn(Mono.error(new InstallationNotFoundException(SCOPE_ID)));

            // Act & Assert
            assertThatThrownBy(() -> service.syncProjectsForOrganization(SCOPE_ID, ORG_LOGIN)).isInstanceOf(
                InstallationNotFoundException.class
            );
        }

        @Test
        @DisplayName("should remove stale projects when sync completes normally")
        void shouldRemoveStaleProjectsOnCompleteSync() {
            // Arrange
            Organization org = createOrganization();
            when(organizationRepository.findByLoginIgnoreCase(ORG_LOGIN)).thenReturn(Optional.of(org));
            mockGraphQlClientForScope();
            mockGraphQlRequestChain("GetOrganizationProjects");

            // Return one project from GraphQL
            GHProjectV2 ghProject = createGraphQlProject(100L, "PVT_node1", 1);
            GHProjectV2Connection connection = createProjectConnection(List.of(ghProject), false);
            ClientGraphQlResponse response = mockValidGraphQlResponse("organization.projectsV2", connection);
            when(requestSpec.execute()).thenReturn(Mono.just(response));

            Project processedProject = createProject(100L, "PVT_node1", 1);
            when(projectRepository.findById(100L)).thenReturn(Optional.empty());
            when(projectProcessor.process(any(GitHubProjectDTO.class), any(), any(), any())).thenReturn(
                processedProject
            );
            when(projectRepository.save(any(Project.class))).thenReturn(processedProject);

            // Database has a stale project that was not returned by GraphQL
            Project staleProject = createProject(999L, "PVT_stale", 99);
            when(projectRepository.findAllByOwnerTypeAndOwnerId(Project.OwnerType.ORGANIZATION, ORG_DB_ID)).thenReturn(
                List.of(processedProject, staleProject)
            );

            // Act
            SyncResult result = service.syncProjectsForOrganization(SCOPE_ID, ORG_LOGIN);

            // Assert
            assertThat(result.status()).isEqualTo(SyncResult.Status.COMPLETED);
            verify(projectProcessor).delete(eq(999L), any(ProcessingContext.class));
            verify(projectProcessor, never()).delete(eq(100L), any(ProcessingContext.class));
        }

        @Test
        @DisplayName("should not remove stale projects when sync was aborted")
        void shouldNotRemoveStaleProjectsOnAbortedSync() {
            // Arrange
            Organization org = createOrganization();
            when(organizationRepository.findByLoginIgnoreCase(ORG_LOGIN)).thenReturn(Optional.of(org));
            mockGraphQlClientForScope();
            mockGraphQlRequestChain("GetOrganizationProjects");

            // First page has next page, but rate limit becomes critical
            GHProjectV2 ghProject = createGraphQlProject(100L, "PVT_node1", 1);
            GHProjectV2Connection connection = createProjectConnection(List.of(ghProject), true);
            ClientGraphQlResponse response = mockValidGraphQlResponse("organization.projectsV2", connection);
            when(requestSpec.execute()).thenReturn(Mono.just(response));
            when(graphQlClientProvider.isRateLimitCritical(SCOPE_ID)).thenReturn(true);

            // Act
            SyncResult result = service.syncProjectsForOrganization(SCOPE_ID, ORG_LOGIN);

            // Assert
            assertThat(result.status()).isEqualTo(SyncResult.Status.ABORTED_RATE_LIMIT);
            verify(projectProcessor, never()).delete(anyLong(), any(ProcessingContext.class));
        }

        @Test
        @DisplayName("should abort with ABORTED_RATE_LIMIT when exception is classified as RATE_LIMITED")
        void shouldAbortRateLimitOnRateLimitedException() {
            // Arrange
            Organization org = createOrganization();
            when(organizationRepository.findByLoginIgnoreCase(ORG_LOGIN)).thenReturn(Optional.of(org));
            mockGraphQlClientForScope();
            mockGraphQlRequestChain("GetOrganizationProjects");

            RuntimeException rateLimitEx = new RuntimeException("Rate limited");
            when(requestSpec.execute()).thenReturn(Mono.error(rateLimitEx));
            when(exceptionClassifier.classifyWithDetails(rateLimitEx)).thenReturn(
                ClassificationResult.of(Category.RATE_LIMITED, "Rate limited")
            );

            // Act
            SyncResult result = service.syncProjectsForOrganization(SCOPE_ID, ORG_LOGIN);

            // Assert
            assertThat(result.status()).isEqualTo(SyncResult.Status.ABORTED_RATE_LIMIT);
        }

        @Test
        @DisplayName("should filter projects based on allowed-projects configuration")
        void shouldFilterProjectsBasedOnAllowedProjectsConfig() {
            // Arrange
            syncSchedulerProperties = new SyncSchedulerProperties(
                true,
                7,
                "0 0 3 * * *",
                15,
                new BackfillProperties(false, 50, 100, 60),
                new FilterProperties(Set.of(), Set.of(), Set.of("test-org/1")) // Only project #1
            );
            service = new GitHubProjectSyncService(
                projectRepository,
                projectItemRepository,
                projectFieldRepository,
                projectFieldValueRepository,
                statusUpdateRepository,
                organizationRepository,
                graphQlClientProvider,
                projectProcessor,
                itemProcessor,
                statusUpdateProcessor,
                backfillStateProvider,
                transactionTemplate,
                syncProperties,
                syncSchedulerProperties,
                exceptionClassifier
            );

            Organization org = createOrganization();
            when(organizationRepository.findByLoginIgnoreCase(ORG_LOGIN)).thenReturn(Optional.of(org));
            mockGraphQlClientForScope();
            mockGraphQlRequestChain("GetOrganizationProjects");

            GHProjectV2 allowedProject = createGraphQlProject(100L, "PVT_node1", 1);
            GHProjectV2 filteredProject = createGraphQlProject(200L, "PVT_node2", 2);
            GHProjectV2Connection connection = createProjectConnection(List.of(allowedProject, filteredProject), false);
            ClientGraphQlResponse response = mockValidGraphQlResponse("organization.projectsV2", connection);
            when(requestSpec.execute()).thenReturn(Mono.just(response));

            Project processedProject = createProject(100L, "PVT_node1", 1);
            when(projectRepository.findById(100L)).thenReturn(Optional.empty());
            when(projectProcessor.process(any(GitHubProjectDTO.class), any(), any(), any())).thenReturn(
                processedProject
            );
            when(projectRepository.save(any(Project.class))).thenReturn(processedProject);

            // Act
            SyncResult result = service.syncProjectsForOrganization(SCOPE_ID, ORG_LOGIN);

            // Assert
            assertThat(result.status()).isEqualTo(SyncResult.Status.COMPLETED);
            assertThat(result.count()).isEqualTo(1); // Only 1 project synced, #2 filtered
        }

        @Test
        @DisplayName("should rethrow exception when classified as AUTH_ERROR")
        void shouldRethrowWhenExceptionClassifiedAsAuthError() {
            // Arrange
            Organization org = createOrganization();
            when(organizationRepository.findByLoginIgnoreCase(ORG_LOGIN)).thenReturn(Optional.of(org));
            mockGraphQlClientForScope();
            mockGraphQlRequestChain("GetOrganizationProjects");

            RuntimeException authEx = new RuntimeException("Bad credentials");
            when(requestSpec.execute()).thenReturn(Mono.error(authEx));
            when(exceptionClassifier.classifyWithDetails(authEx)).thenReturn(
                ClassificationResult.of(Category.AUTH_ERROR, "Auth failed")
            );

            // Act & Assert
            assertThatThrownBy(() -> service.syncProjectsForOrganization(SCOPE_ID, ORG_LOGIN)).isSameAs(authEx);
        }

        @Test
        @DisplayName("should process multiple pages of projects")
        void shouldProcessMultiplePagesOfProjects() {
            // Arrange
            Organization org = createOrganization();
            when(organizationRepository.findByLoginIgnoreCase(ORG_LOGIN)).thenReturn(Optional.of(org));
            mockGraphQlClientForScope();
            mockGraphQlRequestChain("GetOrganizationProjects");

            // Page 1: hasNextPage=true
            GHProjectV2 project1 = createGraphQlProject(100L, "PVT_1", 1);
            GHProjectV2Connection page1 = createProjectConnection(List.of(project1), true);
            ClientGraphQlResponse response1 = mockValidGraphQlResponse("organization.projectsV2", page1);

            // Page 2: hasNextPage=false
            GHProjectV2 project2 = createGraphQlProject(200L, "PVT_2", 2);
            GHProjectV2Connection page2 = createProjectConnection(List.of(project2), false);
            ClientGraphQlResponse response2 = mockValidGraphQlResponse("organization.projectsV2", page2);

            when(requestSpec.execute()).thenReturn(Mono.just(response1)).thenReturn(Mono.just(response2));

            Project p1 = createProject(100L, "PVT_1", 1);
            Project p2 = createProject(200L, "PVT_2", 2);
            when(projectRepository.findById(100L)).thenReturn(Optional.empty());
            when(projectRepository.findById(200L)).thenReturn(Optional.empty());
            when(projectProcessor.process(any(GitHubProjectDTO.class), any(), any(), any()))
                .thenReturn(p1)
                .thenReturn(p2);
            when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            SyncResult result = service.syncProjectsForOrganization(SCOPE_ID, ORG_LOGIN);

            // Assert
            assertThat(result.status()).isEqualTo(SyncResult.Status.COMPLETED);
            assertThat(result.count()).isEqualTo(2);
            verify(projectProcessor, times(2)).process(any(GitHubProjectDTO.class), any(), any(), any());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // syncDraftIssues tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("syncDraftIssues")
    class SyncDraftIssues {

        @Test
        @DisplayName("should return completed(0) when project is null")
        void shouldReturnCompletedZeroWhenProjectIsNull() {
            // Act
            SyncResult result = service.syncDraftIssues(SCOPE_ID, null);

            // Assert
            assertThat(result.status()).isEqualTo(SyncResult.Status.COMPLETED);
            assertThat(result.count()).isZero();
        }

        @Test
        @DisplayName("should return completed(0) when project nodeId is null")
        void shouldReturnCompletedZeroWhenProjectNodeIdIsNull() {
            // Arrange
            Project project = new Project();
            project.setId(1L);
            project.setNodeId(null);

            // Act
            SyncResult result = service.syncDraftIssues(SCOPE_ID, project);

            // Assert
            assertThat(result.status()).isEqualTo(SyncResult.Status.COMPLETED);
            assertThat(result.count()).isZero();
        }

        @Test
        @DisplayName("should return completed when all phases encounter null data")
        void shouldCompleteWithAllPhasesWhenEmpty() {
            // Arrange
            Project project = createProject(10L, "PVT_node10", 1);
            mockGraphQlClientForScope();
            mockGraphQlRequestChain();

            // Phase 1: Fields - return empty connection (success)
            ClientGraphQlResponse fieldsResponse = mockValidGraphQlResponse("node.fields", null);
            // Phase 2: Status updates - return empty connection (success)
            ClientGraphQlResponse statusUpdatesResponse = mockValidGraphQlResponse("node.statusUpdates", null);
            // Phase 3: Items - return empty connection
            ClientGraphQlResponse itemsResponse = mockValidGraphQlResponse("node.items", null);

            when(requestSpec.execute())
                .thenReturn(Mono.just(fieldsResponse))
                .thenReturn(Mono.just(statusUpdatesResponse))
                .thenReturn(Mono.just(itemsResponse));

            when(backfillStateProvider.getProjectItemSyncCursor(10L)).thenReturn(Optional.empty());
            when(backfillStateProvider.getProjectItemsSyncedAt(10L)).thenReturn(Optional.empty());

            // Act
            SyncResult result = service.syncDraftIssues(SCOPE_ID, project);

            // Assert
            assertThat(result.status()).isEqualTo(SyncResult.Status.COMPLETED);
            assertThat(result.count()).isZero();
            assertThat(result.fieldsSynced()).isTrue();
            assertThat(result.statusUpdatesSynced()).isTrue();
            assertThat(result.itemsSynced()).isTrue();
        }

        @Test
        @DisplayName("should return COMPLETED_WITH_WARNINGS when field sync fails but items succeed")
        void shouldReturnCompletedWithWarningsWhenFieldSyncFails() {
            // Arrange
            Project project = createProject(10L, "PVT_node10", 1);
            mockGraphQlClientForScope();
            mockGraphQlRequestChain();

            // Phase 1: Fields - return invalid response (fail)
            ClientGraphQlResponse invalidFieldsResponse = mockInvalidGraphQlResponse();
            // Phase 2: Status updates - return empty (success)
            ClientGraphQlResponse statusUpdatesResponse = mockValidGraphQlResponse("node.statusUpdates", null);
            // Phase 3: Items - return empty (success)
            ClientGraphQlResponse itemsResponse = mockValidGraphQlResponse("node.items", null);

            when(requestSpec.execute())
                .thenReturn(Mono.just(invalidFieldsResponse))
                .thenReturn(Mono.just(statusUpdatesResponse))
                .thenReturn(Mono.just(itemsResponse));

            when(backfillStateProvider.getProjectItemSyncCursor(10L)).thenReturn(Optional.empty());
            when(backfillStateProvider.getProjectItemsSyncedAt(10L)).thenReturn(Optional.empty());

            // Act
            SyncResult result = service.syncDraftIssues(SCOPE_ID, project);

            // Assert
            assertThat(result.status()).isEqualTo(SyncResult.Status.COMPLETED_WITH_WARNINGS);
            assertThat(result.fieldsSynced()).isFalse();
            assertThat(result.statusUpdatesSynced()).isTrue();
            assertThat(result.itemsSynced()).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // getProjectsNeedingItemSync tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getProjectsNeedingItemSync")
    class GetProjectsNeedingItemSync {

        @Test
        @DisplayName("should delegate to repository with correct cooldown threshold")
        void shouldDelegateToRepositoryWithCorrectCooldownThreshold() {
            // Arrange
            Long organizationId = 42L;
            Project project1 = createProject(1L, "PVT_1", 1);
            Project project2 = createProject(2L, "PVT_2", 2);
            when(projectRepository.findProjectsNeedingItemSync(eq(organizationId), any(Instant.class))).thenReturn(
                List.of(project1, project2)
            );

            // Act
            List<Project> result = service.getProjectsNeedingItemSync(organizationId);

            // Assert
            assertThat(result).hasSize(2);
            var instantCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(projectRepository).findProjectsNeedingItemSync(eq(organizationId), instantCaptor.capture());
            Instant capturedThreshold = instantCaptor.getValue();
            // The threshold should be approximately now minus the cooldown period
            assertThat(capturedThreshold).isBefore(Instant.now());
            assertThat(capturedThreshold).isAfter(
                Instant.now().minusSeconds(syncSchedulerProperties.cooldownMinutes() * 60L + 5)
            );
        }

        @Test
        @DisplayName("should return empty list when no projects need sync")
        void shouldReturnEmptyListWhenNoProjectsNeedSync() {
            // Arrange
            Long organizationId = 42L;
            when(projectRepository.findProjectsNeedingItemSync(eq(organizationId), any(Instant.class))).thenReturn(
                List.of()
            );

            // Act
            List<Project> result = service.getProjectsNeedingItemSync(organizationId);

            // Assert
            assertThat(result).isEmpty();
        }
    }
}
