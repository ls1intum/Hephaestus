package de.tum.cit.aet.hephaestus.integration.scm.gitlab.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.consumer.IntegrationNatsConsumer;
import de.tum.cit.aet.hephaestus.integration.core.consumer.NatsConnectionProperties;
import de.tum.cit.aet.hephaestus.integration.core.framework.SyncSchedulerProperties;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncTargetProvider;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.Organization;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.OrganizationRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabRateLimitTracker;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabSyncServiceHolder;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.organization.GitLabGroupSyncService;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.organization.GitLabSyncResult;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitor;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link GitLabWorkspaceInitializationService}.
 */
@Tag("unit")
class GitLabWorkspaceInitializationServiceTest extends BaseUnitTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private RepositoryToMonitorRepository repositoryToMonitorRepository;

    @Mock
    private RepositoryRepository repositoryRepository;

    @Mock
    private IntegrationNatsConsumer natsConsumerService;

    @Mock
    private ObjectProvider<IntegrationNatsConsumer> natsConsumerServiceProvider;

    @Mock
    private SyncTargetProvider syncTargetProvider;

    @Mock
    private ObjectProvider<GitLabSyncServiceHolder> gitLabSyncServiceHolderProvider;

    @Mock
    private ObjectProvider<GitLabWebhookService> gitLabWebhookServiceProvider;

    @Mock
    private ObjectProvider<GitLabRateLimitTracker> rateLimitTrackerProvider;

    @Mock
    private ObjectProvider<GitLabWorkspaceDataSyncTrigger> dataSyncTriggerProvider;

    @Mock
    private GitLabWorkspaceDataSyncTrigger dataSyncTrigger;

    @Mock
    private AsyncTaskExecutor monitoringExecutor;

    @Mock
    private GitLabSyncServiceHolder gitLabSyncServiceHolder;

    @Mock
    private GitLabGroupSyncService gitLabGroupSyncService;

    @Mock
    private GitLabWebhookService gitLabWebhookService;

    @Mock
    private ConnectionService connectionService;

    private GitLabWorkspaceInitializationService initService;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        NatsConnectionProperties natsProperties = new NatsConnectionProperties(
            true,
            "nats://localhost:4222",
            null,
            7,
            null
        );
        SyncSchedulerProperties syncProps = new SyncSchedulerProperties(
            true,
            7,
            "0 0 3 * * *",
            15,
            null,
            null,
            null,
            null
        );

        lenient()
            .doAnswer(invocation -> {
                Consumer<IntegrationNatsConsumer> consumer = invocation.getArgument(0);
                consumer.accept(natsConsumerService);
                return null;
            })
            .when(natsConsumerServiceProvider)
            .ifAvailable(any());
        lenient().when(dataSyncTriggerProvider.getObject()).thenReturn(dataSyncTrigger);

        initService = new GitLabWorkspaceInitializationService(
            workspaceRepository,
            organizationRepository,
            repositoryToMonitorRepository,
            repositoryRepository,
            natsProperties,
            syncProps,
            natsConsumerServiceProvider,
            syncTargetProvider,
            gitLabSyncServiceHolderProvider,
            gitLabWebhookServiceProvider,
            rateLimitTrackerProvider,
            dataSyncTriggerProvider,
            connectionService,
            monitoringExecutor
        );

        workspace = new Workspace();
        workspace.setAccountLogin("my-group/subgroup");
        ReflectionTestUtils.setField(workspace, "id", 1L);

        // GitLab integration metadata lives on a Connection
        // row now, not on Workspace. Each test that triggers initialize() needs to see
        // an active GitLab Connection + bearer token; default-configure that here so the
        // existing test bodies don't have to know about the Connection registry.
        lenient()
            .when(connectionService.findActiveGitLabConfig(anyLong()))
            .thenReturn(
                Optional.of(
                    new ConnectionConfig.GitLabConfig(
                        "https://gitlab.com",
                        null,
                        null,
                        ConnectionConfig.GitLabConfig.SigningMode.PLAINTEXT,
                        Set.of()
                    )
                )
            );
        lenient()
            .when(connectionService.findActiveBearerToken(anyLong(), eq(IntegrationKind.GITLAB)))
            .thenReturn(Optional.of(new BearerToken("glpat-test-token", null)));
    }

    /** Creates a service instance with NATS disabled for testing skip behavior. */
    private GitLabWorkspaceInitializationService createServiceWithNatsDisabled() {
        NatsConnectionProperties disabledNats = new NatsConnectionProperties(
            false,
            "nats://localhost:4222",
            null,
            7,
            null
        );
        SyncSchedulerProperties syncProps = new SyncSchedulerProperties(
            true,
            7,
            "0 0 3 * * *",
            15,
            null,
            null,
            null,
            null
        );
        return new GitLabWorkspaceInitializationService(
            workspaceRepository,
            organizationRepository,
            repositoryToMonitorRepository,
            repositoryRepository,
            disabledNats,
            syncProps,
            natsConsumerServiceProvider,
            syncTargetProvider,
            gitLabSyncServiceHolderProvider,
            gitLabWebhookServiceProvider,
            rateLimitTrackerProvider,
            dataSyncTriggerProvider,
            connectionService,
            monitoringExecutor
        );
    }

    /** Configures the executor mock to run submitted tasks synchronously. */
    private void executeSubmittedTasksSynchronously() {
        when(monitoringExecutor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        });
    }

    private Repository createRepo(String nameWithOwner) {
        Repository repo = new Repository();
        repo.setNameWithOwner(nameWithOwner);
        return repo;
    }

    /** Sets up mocks for a minimal successful discovery (no webhook, no org). */
    private void stubMinimalDiscovery(List<Repository> repos) {
        GitLabSyncResult syncResult = GitLabSyncResult.completed(repos, 1, 0, 0);
        when(gitLabWebhookServiceProvider.getIfAvailable()).thenReturn(null);
        when(gitLabSyncServiceHolderProvider.getIfAvailable()).thenReturn(gitLabSyncServiceHolder);
        when(gitLabSyncServiceHolder.getGroupSyncService()).thenReturn(gitLabGroupSyncService);
        when(gitLabGroupSyncService.syncGroupProjects(eq(1L), eq("my-group/subgroup"), any())).thenReturn(syncResult);
        when(
            organizationRepository.findByLoginIgnoreCaseAndProvider_Type(
                "my-group/subgroup",
                IdentityProviderType.GITLAB
            )
        ).thenReturn(Optional.empty());
        when(repositoryToMonitorRepository.findByWorkspaceId(1L)).thenReturn(List.of());
    }

    @Nested
    class InitializeGuards {

        @Test
        void shouldSkipNonGitLab() {
            // Drop the default GitLab Connection mock — this workspace has no GitLab
            // binding so initialize() must short-circuit before touching any sync service.
            when(connectionService.findActiveGitLabConfig(anyLong())).thenReturn(Optional.empty());

            initService.initialize(workspace);

            verifyNoInteractions(gitLabSyncServiceHolderProvider);
            verifyNoInteractions(gitLabWebhookServiceProvider);
        }

        @Test
        void shouldSkipGitHubApp() {
            // GitHub App workspace = no GitLab Connection at all.
            when(connectionService.findActiveGitLabConfig(anyLong())).thenReturn(Optional.empty());

            initService.initialize(workspace);

            verifyNoInteractions(gitLabSyncServiceHolderProvider);
        }

        @Test
        void shouldSkipNullToken() {
            when(connectionService.findActiveBearerToken(anyLong(), eq(IntegrationKind.GITLAB))).thenReturn(
                Optional.empty()
            );

            initService.initialize(workspace);

            verifyNoInteractions(gitLabSyncServiceHolderProvider);
        }

        @Test
        void shouldSkipBlankToken() {
            when(connectionService.findActiveBearerToken(anyLong(), eq(IntegrationKind.GITLAB))).thenReturn(
                Optional.of(new BearerToken("   ", null))
            );

            initService.initialize(workspace);

            verifyNoInteractions(gitLabSyncServiceHolderProvider);
        }

        @Test
        void shouldSkipNullAccountLogin() {
            workspace.setAccountLogin(null);

            initService.initialize(workspace);

            verifyNoInteractions(gitLabSyncServiceHolderProvider);
        }

        @Test
        void shouldSkipEmptyAccountLogin() {
            workspace.setAccountLogin("");

            initService.initialize(workspace);

            verifyNoInteractions(gitLabSyncServiceHolderProvider);
        }
    }

    @Nested
    class InitializeWebhook {

        @Test
        void shouldContinueWhenTokenRotationFails() {
            when(gitLabWebhookServiceProvider.getIfAvailable()).thenReturn(gitLabWebhookService);
            doThrow(new RuntimeException("rotation failed")).when(gitLabWebhookService).rotateTokenIfNeeded(workspace);
            // Webhook registration should still be attempted after rotation failure
            when(gitLabWebhookService.registerWebhook(workspace)).thenReturn(WebhookSetupResult.success(99L, 42L));
            when(gitLabSyncServiceHolderProvider.getIfAvailable()).thenReturn(null);

            initService.initialize(workspace);

            // registerWebhook was still called despite rotation failure
            verify(gitLabWebhookService).registerWebhook(workspace);
        }

        @Test
        void shouldContinueWhenWebhookRegistrationFails() {
            when(gitLabWebhookServiceProvider.getIfAvailable()).thenReturn(gitLabWebhookService);
            when(gitLabWebhookService.registerWebhook(workspace)).thenThrow(new RuntimeException("webhook failed"));
            // Discovery should still be attempted
            when(gitLabSyncServiceHolderProvider.getIfAvailable()).thenReturn(gitLabSyncServiceHolder);
            when(gitLabSyncServiceHolder.getGroupSyncService()).thenReturn(gitLabGroupSyncService);
            when(gitLabGroupSyncService.syncGroupProjects(eq(1L), eq("my-group/subgroup"), any())).thenReturn(
                GitLabSyncResult.completed(Collections.emptyList(), 1, 0, 0)
            );

            initService.initialize(workspace);

            // Discovery was still attempted
            verify(gitLabGroupSyncService).syncGroupProjects(eq(1L), eq("my-group/subgroup"), any());
        }
    }

    @Nested
    class InitializeDiscovery {

        @Test
        void shouldDiscoverAndCreateMonitors() {
            List<Repository> repos = List.of(createRepo("my-group/project-a"), createRepo("my-group/project-b"));
            GitLabSyncResult syncResult = GitLabSyncResult.completed(repos, 1, 0, 0);

            when(gitLabWebhookServiceProvider.getIfAvailable()).thenReturn(gitLabWebhookService);
            when(gitLabWebhookService.registerWebhook(workspace)).thenReturn(WebhookSetupResult.success(99L, 42L));
            when(gitLabSyncServiceHolderProvider.getIfAvailable()).thenReturn(gitLabSyncServiceHolder);
            when(gitLabSyncServiceHolder.getGroupSyncService()).thenReturn(gitLabGroupSyncService);
            when(gitLabGroupSyncService.syncGroupProjects(eq(1L), eq("my-group/subgroup"), any())).thenReturn(
                syncResult
            );

            Organization organization = new Organization();
            ReflectionTestUtils.setField(organization, "id", 10L);
            organization.setLogin("my-group/subgroup");
            when(
                organizationRepository.findByLoginIgnoreCaseAndProvider_Type(
                    "my-group/subgroup",
                    IdentityProviderType.GITLAB
                )
            ).thenReturn(Optional.of(organization));
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
            when(repositoryToMonitorRepository.findByWorkspaceId(1L)).thenReturn(List.of());

            initService.initialize(workspace);

            // Verify organization linked
            assertThat(workspace.getOrganization()).isEqualTo(organization);

            // Verify monitors created with correct workspace reference
            ArgumentCaptor<RepositoryToMonitor> captor = ArgumentCaptor.forClass(RepositoryToMonitor.class);
            verify(repositoryToMonitorRepository, times(2)).save(captor.capture());

            Set<String> createdNames = captor
                .getAllValues()
                .stream()
                .map(RepositoryToMonitor::getNameWithOwner)
                .collect(Collectors.toSet());
            assertThat(createdNames).containsExactlyInAnyOrder("my-group/project-a", "my-group/project-b");

            // Verify workspace reference set on each monitor
            captor.getAllValues().forEach(monitor -> assertThat(monitor.getWorkspace()).isSameAs(workspace));

            // Verify NATS consumer updated for new monitors
            verify(natsConsumerService).updateScopeConsumer(1L);
        }

        @Test
        void shouldNotCreateMonitorsWhenSyncReturnsEmpty() {
            GitLabSyncResult emptyResult = GitLabSyncResult.completed(Collections.emptyList(), 1, 0, 0);

            when(gitLabWebhookServiceProvider.getIfAvailable()).thenReturn(null);
            when(gitLabSyncServiceHolderProvider.getIfAvailable()).thenReturn(gitLabSyncServiceHolder);
            when(gitLabSyncServiceHolder.getGroupSyncService()).thenReturn(gitLabGroupSyncService);
            when(gitLabGroupSyncService.syncGroupProjects(eq(1L), eq("my-group/subgroup"), any())).thenReturn(
                emptyResult
            );

            initService.initialize(workspace);

            verify(repositoryToMonitorRepository, never()).save(any());
            verify(organizationRepository, never()).findByLoginIgnoreCaseAndProvider_Type(any(), any());
            verifyNoInteractions(natsConsumerService);
        }

        @Test
        void shouldSkipDiscoveryWhenSyncServiceUnavailable() {
            when(gitLabWebhookServiceProvider.getIfAvailable()).thenReturn(null);
            when(gitLabSyncServiceHolderProvider.getIfAvailable()).thenReturn(null);

            initService.initialize(workspace);

            verify(repositoryToMonitorRepository, never()).save(any());
        }

        @Test
        void shouldNotDuplicateExistingMonitors() {
            stubMinimalDiscovery(List.of(createRepo("my-group/existing-project"), createRepo("my-group/new-project")));

            RepositoryToMonitor existingMonitor = new RepositoryToMonitor();
            existingMonitor.setNameWithOwner("my-group/existing-project");
            when(repositoryToMonitorRepository.findByWorkspaceId(1L)).thenReturn(List.of(existingMonitor));

            initService.initialize(workspace);

            ArgumentCaptor<RepositoryToMonitor> captor = ArgumentCaptor.forClass(RepositoryToMonitor.class);
            verify(repositoryToMonitorRepository, times(1)).save(captor.capture());
            assertThat(captor.getValue().getNameWithOwner()).isEqualTo("my-group/new-project");
        }

        @Test
        void shouldCompleteInitializationWhenProjectDiscoveryFails() {
            when(gitLabWebhookServiceProvider.getIfAvailable()).thenReturn(null);
            when(gitLabSyncServiceHolderProvider.getIfAvailable()).thenReturn(gitLabSyncServiceHolder);
            when(gitLabSyncServiceHolder.getGroupSyncService()).thenReturn(gitLabGroupSyncService);
            when(gitLabGroupSyncService.syncGroupProjects(anyLong(), any(), any())).thenThrow(
                new RuntimeException("GraphQL timeout")
            );

            // Should not throw
            initService.initialize(workspace);

            verify(repositoryToMonitorRepository, never()).save(any());
        }

        @Test
        void shouldSkipOrgLinkingWhenAlreadyLinked() {
            Organization existingOrg = new Organization();
            existingOrg.setLogin("my-group/subgroup");
            workspace.setOrganization(existingOrg);

            List<Repository> repos = List.of(createRepo("my-group/project-a"));
            GitLabSyncResult syncResult = GitLabSyncResult.completed(repos, 1, 0, 0);
            when(gitLabWebhookServiceProvider.getIfAvailable()).thenReturn(null);
            when(gitLabSyncServiceHolderProvider.getIfAvailable()).thenReturn(gitLabSyncServiceHolder);
            when(gitLabSyncServiceHolder.getGroupSyncService()).thenReturn(gitLabGroupSyncService);
            when(gitLabGroupSyncService.syncGroupProjects(eq(1L), eq("my-group/subgroup"), any())).thenReturn(
                syncResult
            );
            when(repositoryToMonitorRepository.findByWorkspaceId(1L)).thenReturn(List.of());

            initService.initialize(workspace);

            verify(organizationRepository, never()).findByLoginIgnoreCaseAndProvider_Type(any(), any());
        }

        @Test
        void shouldNotUpdateNatsWhenNoNewMonitors() {
            stubMinimalDiscovery(List.of(createRepo("my-group/project-a")));

            // All monitors already exist
            RepositoryToMonitor existing = new RepositoryToMonitor();
            existing.setNameWithOwner("my-group/project-a");
            when(repositoryToMonitorRepository.findByWorkspaceId(1L)).thenReturn(List.of(existing));

            initService.initialize(workspace);

            verify(natsConsumerService, never()).updateScopeConsumer(anyLong());
        }
    }

    @Nested
    class InitializeAsync {

        @Test
        void shouldSubmitToExecutor() {
            initService.initializeAsync(1L);

            verify(monitoringExecutor).submit(any(Runnable.class));
        }

        @Test
        void shouldStartNatsConsumerAfterInit() {
            executeSubmittedTasksSynchronously();
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

            initService.initializeAsync(1L);

            verify(dataSyncTrigger).syncAllRepositories(1L);
            verify(natsConsumerService).startConsumingScope(1L);
        }

        @Test
        void shouldNotStartNatsWhenDisabled() {
            var disabledService = createServiceWithNatsDisabled();

            when(monitoringExecutor.submit(any(Runnable.class))).thenAnswer(invocation -> {
                Runnable task = invocation.getArgument(0);
                task.run();
                return null;
            });
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

            disabledService.initializeAsync(1L);

            verifyNoInteractions(natsConsumerService);
        }

        @Test
        void shouldSkipWhenNotFound() {
            executeSubmittedTasksSynchronously();
            when(workspaceRepository.findById(99L)).thenReturn(Optional.empty());

            initService.initializeAsync(99L);

            verifyNoInteractions(natsConsumerService);
        }
    }

    @Nested
    class EnsureRepositoryMonitors {

        @Test
        void shouldCreateAllMonitorsAndReturnCount() {
            List<Repository> repos = List.of(
                createRepo("group/repo-1"),
                createRepo("group/repo-2"),
                createRepo("group/repo-3")
            );

            when(repositoryToMonitorRepository.findByWorkspaceId(1L)).thenReturn(List.of());

            int created = initService.ensureRepositoryMonitors(workspace, repos);

            assertThat(created).isEqualTo(3);
            verify(repositoryToMonitorRepository, times(3)).save(any(RepositoryToMonitor.class));
        }

        @Test
        void shouldReturnZeroWhenAllExist() {
            List<Repository> repos = List.of(createRepo("group/repo-1"), createRepo("group/repo-2"));

            RepositoryToMonitor m1 = new RepositoryToMonitor();
            m1.setNameWithOwner("group/repo-1");
            RepositoryToMonitor m2 = new RepositoryToMonitor();
            m2.setNameWithOwner("group/repo-2");
            when(repositoryToMonitorRepository.findByWorkspaceId(1L)).thenReturn(List.of(m1, m2));

            int created = initService.ensureRepositoryMonitors(workspace, repos);

            assertThat(created).isEqualTo(0);
            verify(repositoryToMonitorRepository, never()).save(any());
        }

        @Test
        void shouldSkipNullNameWithOwner() {
            Repository repoWithNullNwo = new Repository();
            // Don't call setNameWithOwner — leaves it as null (bypassing @NonNull setter)
            ReflectionTestUtils.setField(repoWithNullNwo, "nameWithOwner", null);

            List<Repository> repos = List.of(repoWithNullNwo, createRepo("group/repo-1"));
            when(repositoryToMonitorRepository.findByWorkspaceId(1L)).thenReturn(List.of());

            int created = initService.ensureRepositoryMonitors(workspace, repos);

            assertThat(created).isEqualTo(1);
            verify(repositoryToMonitorRepository, times(1)).save(any(RepositoryToMonitor.class));
        }
    }

    @Nested
    class LinkWorkspaceToOrganization {

        @Test
        void shouldSkipWhenAlreadyLinked() {
            Organization existingOrg = new Organization();
            workspace.setOrganization(existingOrg);

            initService.linkWorkspaceToOrganization(workspace);

            verify(organizationRepository, never()).findByLoginIgnoreCaseAndProvider_Type(any(), any());
        }

        @Test
        void shouldSkipWhenBlankLogin() {
            workspace.setAccountLogin("");

            initService.linkWorkspaceToOrganization(workspace);

            verify(organizationRepository, never()).findByLoginIgnoreCaseAndProvider_Type(any(), any());
        }

        @Test
        void shouldLinkOrganization() {
            Organization organization = new Organization();
            ReflectionTestUtils.setField(organization, "id", 10L);

            when(
                organizationRepository.findByLoginIgnoreCaseAndProvider_Type(
                    "my-group/subgroup",
                    IdentityProviderType.GITLAB
                )
            ).thenReturn(Optional.of(organization));
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

            initService.linkWorkspaceToOrganization(workspace);

            // Verify saved via captor
            ArgumentCaptor<Workspace> captor = ArgumentCaptor.forClass(Workspace.class);
            verify(workspaceRepository).save(captor.capture());
            assertThat(captor.getValue().getOrganization()).isEqualTo(organization);

            // Verify in-memory reference updated for subsequent phases
            assertThat(workspace.getOrganization()).isEqualTo(organization);
        }

        @Test
        void shouldNotLinkWhenNotFound() {
            when(
                organizationRepository.findByLoginIgnoreCaseAndProvider_Type(
                    "my-group/subgroup",
                    IdentityProviderType.GITLAB
                )
            ).thenReturn(Optional.empty());

            initService.linkWorkspaceToOrganization(workspace);

            verify(workspaceRepository, never()).save(any());
        }

        @Test
        void shouldNotLinkWhenWorkspaceDeleted() {
            Organization organization = new Organization();
            when(
                organizationRepository.findByLoginIgnoreCaseAndProvider_Type(
                    "my-group/subgroup",
                    IdentityProviderType.GITLAB
                )
            ).thenReturn(Optional.of(organization));
            when(workspaceRepository.findById(1L)).thenReturn(Optional.empty());

            initService.linkWorkspaceToOrganization(workspace);

            verify(workspaceRepository, never()).save(any());
        }
    }
}
