package de.tum.cit.aet.hephaestus.integration.scm.gitlab.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.consumer.IntegrationNatsConsumer;
import de.tum.cit.aet.hephaestus.integration.core.consumer.NatsConnectionProperties;
import de.tum.cit.aet.hephaestus.integration.core.framework.SyncSchedulerProperties;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncResult;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncTargetProvider;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJob;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobRepository;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobRequest;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobService;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobStatus;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobTrigger;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobType;
import de.tum.cit.aet.hephaestus.integration.core.sync.api.SyncStatusService;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.OrganizationRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabRateLimitTracker;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabSyncServiceHolder;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.issue.GitLabIssueSyncService;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.pullrequest.GitLabMergeRequestSyncService;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.team.GitLabTeamSyncService;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitor;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceActorSelector;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor;

/** Runs the real initialization repository loop and job transactions; provider responses are controlled. */
class GitLabLifecycleSyncCancellationIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private RepositoryToMonitorRepository monitorRepository;

    @Autowired
    private ConnectionRepository connectionRepository;

    @Autowired
    private SyncTargetProvider syncTargetProvider;

    @Autowired
    private SyncJobService jobService;

    @Autowired
    private SyncJobRepository jobRepository;

    @Autowired
    private SyncStatusService statusService;

    private final GitLabIssueSyncService issues = mock(GitLabIssueSyncService.class);
    private final GitLabMergeRequestSyncService mergeRequests = mock(GitLabMergeRequestSyncService.class);
    private final GitLabTeamSyncService teams = mock(GitLabTeamSyncService.class);
    private final List<Long> processedRepositories = new ArrayList<>();
    private Workspace workspace;
    private Connection connection;
    private Repository first;
    private Repository second;
    private GitLabWorkspaceDataSyncTrigger trigger;
    private Instant priorSuccessfulAt;

    @BeforeEach
    void setUp() {
        workspace = createWorkspace(
                "initial-cancel",
                "Initial cancellation",
                "initial-group",
                AccountType.ORG,
                persistUser("initial-owner"));
        connection = new Connection(
                workspace,
                IntegrationKind.GITLAB,
                "initial-group",
                new ConnectionConfig.GitLabConfig(
                        "https://gitlab.com",
                        null,
                        null,
                        ConnectionConfig.GitLabConfig.SigningMode.PLAINTEXT,
                        Set.of(),
                        null));
        connection.setState(IntegrationState.ACTIVE);
        connection = connectionRepository.saveAndFlush(connection);
        first = monitoredRepository("initial-group/a", 901L);
        second = monitoredRepository("initial-group/b", 902L);
        assertThat(repositoryRepository.findAllByWorkspaceMonitors(workspace.getId()))
                .extracting(Repository::getId)
                .containsExactly(first.getId(), second.getId());

        jobService.run(
                new SyncJobRequest(
                        workspace.getId(),
                        connection.getId(),
                        IntegrationKind.GITLAB,
                        SyncJobType.RECONCILIATION,
                        SyncJobTrigger.MANUAL,
                        null),
                handle -> {});
        priorSuccessfulAt = Objects.requireNonNull(
                statusService.getStatus(workspace.getId(), connection.getId()).lastSuccessfulSyncAt());

        when(issues.syncIssues(anyLong(), any(Repository.class), any())).thenAnswer(invocation -> {
            Repository repository = invocation.getArgument(1);
            processedRepositories.add(repository.getId());
            return SyncResult.completed(1);
        });
        when(mergeRequests.syncMergeRequests(anyLong(), any(Repository.class), any()))
                .thenReturn(SyncResult.completed(1));
        when(teams.syncTeamsForGroup(workspace.getId(), workspace.getAccountLogin()))
                .thenReturn(new GitLabTeamSyncService.Result(0, true));
        GitLabSyncServiceHolder services = new GitLabSyncServiceHolder(
                null, null, null, null, issues, mergeRequests, teams, null, null, null, null, null, null, null);
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("services", services);
        trigger = triggerWith(beans.getBeanProvider(GitLabSyncServiceHolder.class));
    }

    @Test
    void shouldRecordCancelledWithoutAdvancingSuccessfulTimeWhenNextRepositoryIsSkipped() {
        when(issues.syncIssues(eq(workspace.getId()), any(Repository.class), any()))
                .thenAnswer(invocation -> {
                    Repository repository = invocation.getArgument(1);
                    processedRepositories.add(repository.getId());
                    requestCancellation();
                    return SyncResult.completed(1);
                });

        trigger.syncAllRepositories(workspace.getId());

        assertThat(processedRepositories).containsExactly(first.getId());
        assertRepositoryCompleted(first);
        assertRepositoryUnchanged(second);
        SyncJob job = lastJob();
        assertThat(job.isCancelRequested()).isTrue();
        assertThat(job.getErrorSummary()).isNull();
        assertThat(job.getStatus()).isEqualTo(SyncJobStatus.CANCELLED);
        assertThat(statusService
                        .getStatus(workspace.getId(), connection.getId())
                        .lastSuccessfulSyncAt())
                .isEqualTo(priorSuccessfulAt);
    }

    @Test
    void shouldKeepSuccessfulCompletionWhenNoCancellationIsRequested() {
        trigger.syncAllRepositories(workspace.getId());

        assertThat(processedRepositories).containsExactly(first.getId(), second.getId());
        assertRepositoryCompleted(first);
        assertRepositoryCompleted(second);
        assertSuccessfulJob();
        assertThat(lastJob().isCancelRequested()).isFalse();
    }

    @Test
    void shouldKeepSuccessfulCompletionWhenCancellationArrivesAfterLastRepositoryCheckpoint() {
        when(teams.syncTeamsForGroup(workspace.getId(), workspace.getAccountLogin()))
                .thenAnswer(invocation -> {
                    requestCancellation();
                    return new GitLabTeamSyncService.Result(0, true);
                });

        trigger.syncAllRepositories(workspace.getId());

        assertThat(processedRepositories).containsExactly(first.getId(), second.getId());
        assertRepositoryCompleted(first);
        assertRepositoryCompleted(second);
        assertThat(lastJob().isCancelRequested()).isTrue();
        assertSuccessfulJob();
    }

    @Test
    void shouldKeepRealFailureWhenCancellationWasRequestedButNoCheckpointStoppedWork() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        ObjectProvider<GitLabSyncServiceHolder> services = beans.getBeanProvider(GitLabSyncServiceHolder.class);
        ObjectProvider<GitLabSyncServiceHolder> failingServices = mock();
        when(failingServices.getIfAvailable()).thenAnswer(invocation -> {
            requestCancellation();
            throw new IllegalStateException("Provider services unavailable");
        });
        assertThat(services.getIfAvailable()).isNull();
        trigger = triggerWith(failingServices);

        trigger.syncAllRepositories(workspace.getId());

        assertThat(processedRepositories).isEmpty();
        assertRepositoryUnchanged(first);
        assertRepositoryUnchanged(second);
        SyncJob job = lastJob();
        assertThat(job.isCancelRequested()).isTrue();
        assertThat(job.getStatus()).isEqualTo(SyncJobStatus.FAILED);
        assertThat(job.getErrorSummary()).contains("Provider services unavailable");
        assertThat(statusService
                        .getStatus(workspace.getId(), connection.getId())
                        .lastSuccessfulSyncAt())
                .isEqualTo(priorSuccessfulAt);
    }

    private GitLabWorkspaceDataSyncTrigger triggerWith(ObjectProvider<GitLabSyncServiceHolder> services) {
        StaticListableBeanFactory absent = new StaticListableBeanFactory();
        // Discovery is outside this controlled-loop proof; syncFullData and its persisted lookup run unchanged.
        ConnectionService discoveryConfig = mock(ConnectionService.class);
        GitLabWorkspaceInitializationService initialization = new GitLabWorkspaceInitializationService(
                workspaceRepository,
                organizationRepository,
                monitorRepository,
                repositoryRepository,
                new NatsConnectionProperties(
                        false,
                        "nats://localhost:4222",
                        null,
                        new NatsConnectionProperties.Consumer(Duration.ofSeconds(60))),
                new SyncSchedulerProperties(
                        true,
                        7,
                        "0 0 3 * * *",
                        15,
                        new SyncSchedulerProperties.BackfillProperties(false, 50, 100, 60),
                        new SyncSchedulerProperties.FilterProperties(Set.of(), Set.of(), Set.of()),
                        new SyncSchedulerProperties.DiscussionsProperties(false),
                        new SyncSchedulerProperties.ProjectsProperties(false)),
                absent.getBeanProvider(IntegrationNatsConsumer.class),
                syncTargetProvider,
                services,
                absent.getBeanProvider(GitLabWebhookService.class),
                absent.getBeanProvider(GitLabRateLimitTracker.class),
                absent.getBeanProvider(GitLabWorkspaceDataSyncTrigger.class),
                discoveryConfig,
                mock(GitLabWorkspaceLinkService.class),
                mock(GitLabRepositoryMonitors.class),
                mock(WorkspaceActorSelector.class),
                new ConcurrentTaskExecutor(new SyncTaskExecutor()));
        return new GitLabWorkspaceDataSyncTrigger(
                initialization, workspaceRepository, connectionRepository, jobService);
    }

    private Repository monitoredRepository(String name, long nativeId) {
        Repository repository = new Repository();
        repository.setNativeId(nativeId);
        repository.setProvider(ensureGitLabProvider());
        repository.setName(name.substring(name.lastIndexOf('/') + 1));
        repository.setNameWithOwner(name);
        repository.setHtmlUrl("https://gitlab.com/" + name);
        repository.setVisibility(Repository.Visibility.PRIVATE);
        repository.setDefaultBranch("main");
        repository.setCreatedAt(Instant.now());
        repository.setUpdatedAt(Instant.now());
        repository.setPushedAt(Instant.now());
        repository = repositoryRepository.saveAndFlush(repository);
        RepositoryToMonitor monitor = new RepositoryToMonitor();
        monitor.setWorkspace(workspace);
        monitor.setNativeId(nativeId);
        monitor.setNameWithOwner(name);
        monitorRepository.saveAndFlush(monitor);
        return repository;
    }

    private void requestCancellation() {
        SyncJob active = jobRepository
                .findFirstByConnection_IdAndStatusInOrderByCreatedAtDesc(connection.getId(), SyncJobStatus.ACTIVE)
                .orElseThrow();
        jobService.requestCancel(workspace.getId(), Objects.requireNonNull(active.getId()));
    }

    private SyncJob lastJob() {
        return jobRepository
                .findFirstByConnection_IdAndStatusInOrderByFinishedAtDesc(connection.getId(), SyncJobStatus.TERMINAL)
                .orElseThrow();
    }

    private void assertSuccessfulJob() {
        SyncJob job = lastJob();
        assertThat(job.getStatus()).isEqualTo(SyncJobStatus.SUCCEEDED);
        assertThat(job.getErrorSummary()).isNull();
        assertThat(statusService
                        .getStatus(workspace.getId(), connection.getId())
                        .lastSuccessfulSyncAt())
                .isEqualTo(job.getFinishedAt())
                .isAfter(priorSuccessfulAt);
    }

    private void assertRepositoryCompleted(Repository repository) {
        assertThat(repositoryRepository
                        .findById(repository.getId())
                        .orElseThrow()
                        .getLastSyncAt())
                .isNotNull();
        RepositoryToMonitor monitor = monitorRepository.findByWorkspaceId(workspace.getId()).stream()
                .filter(candidate -> candidate.getNameWithOwner().equals(repository.getNameWithOwner()))
                .findFirst()
                .orElseThrow();
        assertThat(monitor.getIssuesSyncedAt()).isNotNull();
        assertThat(monitor.getPullRequestsSyncedAt()).isNotNull();
        assertThat(monitor.getRepositorySyncedAt()).isNotNull();
        assertThat(monitor.getSyncErrorSummary()).isNull();
    }

    private void assertRepositoryUnchanged(Repository repository) {
        assertThat(repositoryRepository
                        .findById(repository.getId())
                        .orElseThrow()
                        .getLastSyncAt())
                .isNull();
        RepositoryToMonitor monitor = monitorRepository.findByWorkspaceId(workspace.getId()).stream()
                .filter(candidate -> candidate.getNameWithOwner().equals(repository.getNameWithOwner()))
                .findFirst()
                .orElseThrow();
        assertThat(monitor.getIssuesSyncedAt()).isNull();
        assertThat(monitor.getPullRequestsSyncedAt()).isNull();
        assertThat(monitor.getRepositorySyncedAt()).isNull();
        assertThat(monitor.getSyncErrorSummary()).isNull();
    }
}
