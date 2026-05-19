package de.tum.in.www1.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabRateLimitTracker;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncServiceHolder;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.organization.gitlab.GitLabGroupSyncService;
import de.tum.in.www1.hephaestus.gitprovider.organization.gitlab.GitLabSyncResult;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.sync.NatsConsumerService;
import de.tum.in.www1.hephaestus.gitprovider.sync.NatsProperties;
import de.tum.in.www1.hephaestus.gitprovider.sync.SyncSchedulerProperties;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
@DisplayName("GitLabWorkspaceInitializationService")
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
    private NatsConsumerService natsConsumerService;

    @Mock
    private SyncTargetProvider syncTargetProvider;

    @Mock
    private ObjectProvider<GitLabSyncServiceHolder> gitLabSyncServiceHolderProvider;

    @Mock
    private ObjectProvider<GitLabWebhookService> gitLabWebhookServiceProvider;

    @Mock
    private ObjectProvider<GitLabRateLimitTracker> rateLimitTrackerProvider;

    @Mock
    private AsyncTaskExecutor monitoringExecutor;

    @Mock
    private GitLabSyncServiceHolder gitLabSyncServiceHolder;

    @Mock
    private GitLabGroupSyncService gitLabGroupSyncService;

    @Mock
    private GitLabWebhookService gitLabWebhookService;

    private GitLabWorkspaceInitializationService initService;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        NatsProperties natsProperties = new NatsProperties(true, "nats://localhost:4222", null, 7, null);
        SyncSchedulerProperties syncProps = new SyncSchedulerProperties(true, 7, "0 0 3 * * *", 15, null, null);

        initService = new GitLabWorkspaceInitializationService(
            workspaceRepository,
            organizationRepository,
            repositoryToMonitorRepository,
            repositoryRepository,
            natsProperties,
            syncProps,
            natsConsumerService,
            syncTargetProvider,
            gitLabSyncServiceHolderProvider,
            gitLabWebhookServiceProvider,
            rateLimitTrackerProvider,
            monitoringExecutor
        );

        workspace = new Workspace();
        workspace.setGitProviderMode(Workspace.GitProviderMode.GITLAB_PAT);
        workspace.setAccountLogin("my-group/subgroup");
        workspace.setPersonalAccessToken("glpat-test-token");
        ReflectionTestUtils.setField(workspace, "id", 1L);
    }

    /** Creates a service instance with NATS disabled for testing skip behavior. */
    private GitLabWorkspaceInitializationService createServiceWithNatsDisabled() {
        NatsProperties disabledNats = new NatsProperties(false, "nats://localhost:4222", null, 7, null);
        SyncSchedulerProperties syncProps = new SyncSchedulerProperties(true, 7, "0 0 3 * * *", 15, null, null);
        return new GitLabWorkspaceInitializationService(
            workspaceRepository,
            organizationRepository,
            repositoryToMonitorRepository,
            repositoryRepository,
            disabledNats,
            syncProps,
            natsConsumerService,
            syncTargetProvider,
            gitLabSyncServiceHolderProvider,
            gitLabWebhookServiceProvider,
            rateLimitTrackerProvider,
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
        when(gitLabGroupSyncService.syncGroupProjects(1L, "my-group/subgroup")).thenReturn(syncResult);
        when(organizationRepository.findByLoginIgnoreCase("my-group/subgroup")).thenReturn(Optional.empty());
        when(repositoryToMonitorRepository.findByWorkspaceId(1L)).thenReturn(List.of());
    }

    @Nested
    @DisplayName("initialize — guard clauses")
    class InitializeGuards {

        @Test
        @DisplayName("should skip non-GitLab workspaces")
        void shouldSkipNonGitLab() {
            workspace.setGitProviderMode(Workspace.GitProviderMode.PAT_ORG);

            initService.initialize(workspace);

            verifyNoInteractions(gitLabSyncServiceHolderProvider);
            verifyNoInteractions(gitLabWebhookServiceProvider);
        }

        @Test
        @DisplayName("should skip GitHub App installations")
        void shouldSkipGitHubApp() {
            workspace.setGitProviderMode(Workspace.GitProviderMode.GITHUB_APP_INSTALLATION);

            initService.initialize(workspace);

            verifyNoInteractions(gitLabSyncServiceHolderProvider);
        }

        @Test
        @DisplayName("should skip when PAT is null")
        void shouldSkipNullToken() {
            workspace.setPersonalAccessToken(null);

            initService.initialize(workspace);

            verifyNoInteractions(gitLabSyncServiceHolderProvider);
        }

        @Test
        @DisplayName("should skip when PAT is blank")
        void shouldSkipBlankToken() {
            workspace.setPersonalAccessToken("   ");

            initService.initialize(workspace);

            verifyNoInteractions(gitLabSyncServiceHolderProvider);
        }

        @Test
        @DisplayName("should skip when accountLogin is null")
        void shouldSkipNullAccountLogin() {
            workspace.setAccountLogin(null);

            initService.initialize(workspace);

            verifyNoInteractions(gitLabSyncServiceHolderProvider);
        }

        @Test
        @DisplayName("should skip when accountLogin is empty")
        void shouldSkipEmptyAccountLogin() {
            workspace.setAccountLogin("");

            initService.initialize(workspace);

            verifyNoInteractions(gitLabSyncServiceHolderProvider);
        }
    }

    @Nested
    @DisplayName("initialize — webhook setup")
    class InitializeWebhook {

        @Test
        @DisplayName("should continue discovery when token rotation throws")
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
        @DisplayName("should continue discovery when webhook registration throws")
        void shouldContinueWhenWebhookRegistrationFails() {
            when(gitLabWebhookServiceProvider.getIfAvailable()).thenReturn(gitLabWebhookService);
            when(gitLabWebhookService.registerWebhook(workspace)).thenThrow(new RuntimeException("webhook failed"));
            // Discovery should still be attempted
            when(gitLabSyncServiceHolderProvider.getIfAvailable()).thenReturn(gitLabSyncServiceHolder);
            when(gitLabSyncServiceHolder.getGroupSyncService()).thenReturn(gitLabGroupSyncService);
            when(gitLabGroupSyncService.syncGroupProjects(1L, "my-group/subgroup")).thenReturn(
                GitLabSyncResult.completed(Collections.emptyList(), 1, 0, 0)
            );

            initService.initialize(workspace);

            // Discovery was still attempted
            verify(gitLabGroupSyncService).syncGroupProjects(1L, "my-group/subgroup");
        }
    }

    @Nested
    @DisplayName("initialize — discovery and monitors")
    class InitializeDiscovery {

        @Test
        @DisplayName("should discover projects, link org, and create monitors")
        void shouldDiscoverAndCreateMonitors() {
            List<Repository> repos = List.of(createRepo("my-group/project-a"), createRepo("my-group/project-b"));
            GitLabSyncResult syncResult = GitLabSyncResult.completed(repos, 1, 0, 0);

            when(gitLabWebhookServiceProvider.getIfAvailable()).thenReturn(gitLabWebhookService);
            when(gitLabWebhookService.registerWebhook(workspace)).thenReturn(WebhookSetupResult.success(99L, 42L));
            when(gitLabSyncServiceHolderProvider.getIfAvailable()).thenReturn(gitLabSyncServiceHolder);
            when(gitLabSyncServiceHolder.getGroupSyncService()).thenReturn(gitLabGroupSyncService);
            when(gitLabGroupSyncService.syncGroupProjects(1L, "my-group/subgroup")).thenReturn(syncResult);

            Organization organization = new Organization();
            ReflectionTestUtils.setField(organization, "id", 10L);
            organization.setLogin("my-group/subgroup");
            when(organizationRepository.findByLoginIgnoreCase("my-group/subgroup")).thenReturn(
                Optional.of(organization)
            );
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
        @DisplayName("should not create monitors when sync returns empty")
        void shouldNotCreateMonitorsWhenSyncReturnsEmpty() {
            GitLabSyncResult emptyResult = GitLabSyncResult.completed(Collections.emptyList(), 1, 0, 0);

            when(gitLabWebhookServiceProvider.getIfAvailable()).thenReturn(null);
            when(gitLabSyncServiceHolderProvider.getIfAvailable()).thenReturn(gitLabSyncServiceHolder);
            when(gitLabSyncServiceHolder.getGroupSyncService()).thenReturn(gitLabGroupSyncService);
            when(gitLabGroupSyncService.syncGroupProjects(1L, "my-group/subgroup")).thenReturn(emptyResult);

            initService.initialize(workspace);

            verify(repositoryToMonitorRepository, never()).save(any());
            verify(organizationRepository, never()).findByLoginIgnoreCase(any());
            verifyNoInteractions(natsConsumerService);
        }

        @Test
        @DisplayName("should skip discovery when sync service unavailable")
        void shouldSkipDiscoveryWhenSyncServiceUnavailable() {
            when(gitLabWebhookServiceProvider.getIfAvailable()).thenReturn(null);
            when(gitLabSyncServiceHolderProvider.getIfAvailable()).thenReturn(null);

            initService.initialize(workspace);

            verify(repositoryToMonitorRepository, never()).save(any());
        }

        @Test
        @DisplayName("should not duplicate existing monitors")
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
        @DisplayName("should complete initialization when project discovery fails")
        void shouldCompleteInitializationWhenProjectDiscoveryFails() {
            when(gitLabWebhookServiceProvider.getIfAvailable()).thenReturn(null);
            when(gitLabSyncServiceHolderProvider.getIfAvailable()).thenReturn(gitLabSyncServiceHolder);
            when(gitLabSyncServiceHolder.getGroupSyncService()).thenReturn(gitLabGroupSyncService);
            when(gitLabGroupSyncService.syncGroupProjects(anyLong(), any())).thenThrow(
                new RuntimeException("GraphQL timeout")
            );

            // Should not throw
            initService.initialize(workspace);

            verify(repositoryToMonitorRepository, never()).save(any());
        }

        @Test
        @DisplayName("should not link organization when already linked")
        void shouldSkipOrgLinkingWhenAlreadyLinked() {
            Organization existingOrg = new Organization();
            existingOrg.setLogin("my-group/subgroup");
            workspace.setOrganization(existingOrg);

            List<Repository> repos = List.of(createRepo("my-group/project-a"));
            GitLabSyncResult syncResult = GitLabSyncResult.completed(repos, 1, 0, 0);
            when(gitLabWebhookServiceProvider.getIfAvailable()).thenReturn(null);
            when(gitLabSyncServiceHolderProvider.getIfAvailable()).thenReturn(gitLabSyncServiceHolder);
            when(gitLabSyncServiceHolder.getGroupSyncService()).thenReturn(gitLabGroupSyncService);
            when(gitLabGroupSyncService.syncGroupProjects(1L, "my-group/subgroup")).thenReturn(syncResult);
            when(repositoryToMonitorRepository.findByWorkspaceId(1L)).thenReturn(List.of());

            initService.initialize(workspace);

            verify(organizationRepository, never()).findByLoginIgnoreCase(any());
        }

        @Test
        @DisplayName("should not update NATS when no new monitors created")
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
    @DisplayName("initializeAsync")
    class InitializeAsync {

        @Test
        @DisplayName("should submit task to executor")
        void shouldSubmitToExecutor() {
            initService.initializeAsync(1L);

            verify(monitoringExecutor).submit(any(Runnable.class));
        }

        @Test
        @DisplayName("should start NATS consumer after initialization")
        void shouldStartNatsConsumerAfterInit() {
            executeSubmittedTasksSynchronously();
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
            when(gitLabWebhookServiceProvider.getIfAvailable()).thenReturn(null);
            when(gitLabSyncServiceHolderProvider.getIfAvailable()).thenReturn(null);

            initService.initializeAsync(1L);

            verify(natsConsumerService).startConsumingScope(1L);
        }

        @Test
        @DisplayName("should not start NATS when NATS is disabled")
        void shouldNotStartNatsWhenDisabled() {
            var disabledService = createServiceWithNatsDisabled();

            when(monitoringExecutor.submit(any(Runnable.class))).thenAnswer(invocation -> {
                Runnable task = invocation.getArgument(0);
                task.run();
                return null;
            });
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
            when(gitLabWebhookServiceProvider.getIfAvailable()).thenReturn(null);
            when(gitLabSyncServiceHolderProvider.getIfAvailable()).thenReturn(null);

            disabledService.initializeAsync(1L);

            verifyNoInteractions(natsConsumerService);
        }

        @Test
        @DisplayName("should skip when workspace not found")
        void shouldSkipWhenNotFound() {
            executeSubmittedTasksSynchronously();
            when(workspaceRepository.findById(99L)).thenReturn(Optional.empty());

            initService.initializeAsync(99L);

            verifyNoInteractions(natsConsumerService);
        }
    }

    @Nested
    @DisplayName("ensureRepositoryMonitors")
    class EnsureRepositoryMonitors {

        @Test
        @DisplayName("should create monitors for all repos and return count")
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
        @DisplayName("should return zero when all monitors already exist")
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
        @DisplayName("should skip repos with null nameWithOwner")
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
    @DisplayName("linkWorkspaceToOrganization")
    class LinkWorkspaceToOrganization {

        @Test
        @DisplayName("should skip when organization already linked")
        void shouldSkipWhenAlreadyLinked() {
            Organization existingOrg = new Organization();
            workspace.setOrganization(existingOrg);

            initService.linkWorkspaceToOrganization(workspace);

            verify(organizationRepository, never()).findByLoginIgnoreCase(any());
        }

        @Test
        @DisplayName("should skip when accountLogin is blank")
        void shouldSkipWhenBlankLogin() {
            workspace.setAccountLogin("");

            initService.linkWorkspaceToOrganization(workspace);

            verify(organizationRepository, never()).findByLoginIgnoreCase(any());
        }

        @Test
        @DisplayName("should link organization and update in-memory reference")
        void shouldLinkOrganization() {
            Organization organization = new Organization();
            ReflectionTestUtils.setField(organization, "id", 10L);

            when(organizationRepository.findByLoginIgnoreCase("my-group/subgroup")).thenReturn(
                Optional.of(organization)
            );
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
        @DisplayName("should not link when organization not found")
        void shouldNotLinkWhenNotFound() {
            when(organizationRepository.findByLoginIgnoreCase("my-group/subgroup")).thenReturn(Optional.empty());

            initService.linkWorkspaceToOrganization(workspace);

            verify(workspaceRepository, never()).save(any());
        }

        @Test
        @DisplayName("should not link when workspace re-read returns empty")
        void shouldNotLinkWhenWorkspaceDeleted() {
            Organization organization = new Organization();
            when(organizationRepository.findByLoginIgnoreCase("my-group/subgroup")).thenReturn(
                Optional.of(organization)
            );
            when(workspaceRepository.findById(1L)).thenReturn(Optional.empty());

            initService.linkWorkspaceToOrganization(workspace);

            verify(workspaceRepository, never()).save(any());
        }
    }
}
