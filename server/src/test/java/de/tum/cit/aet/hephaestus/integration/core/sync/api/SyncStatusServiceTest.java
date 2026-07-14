package de.tum.cit.aet.hephaestus.integration.core.sync.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.api.ConnectionAdminService;
import de.tum.cit.aet.hephaestus.integration.core.framework.IntegrationManifestRegistry;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionSyncDetails;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionSyncStateProvider;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationSyncRunner;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncResourceState;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJob;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobConflictException;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobRepository;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobRequest;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobService;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobStatus;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobTrigger;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobType;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncStateConflictException;
import de.tum.cit.aet.hephaestus.integration.core.sync.activity.ConnectionActivity;
import de.tum.cit.aet.hephaestus.integration.core.sync.activity.ConnectionActivityRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.core.task.AsyncTaskExecutor;

/**
 * Unit coverage for {@link SyncStatusService}: connection-health derivation across every
 * {@code IntegrationState} × job-outcome combination, the trigger idempotent-absorb path, the
 * not-ACTIVE / no-runner 409 paths, and the catalog join.
 */
class SyncStatusServiceTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 1L;
    private static final long CONNECTION_ID = 10L;

    @Mock
    private ConnectionAdminService connectionAdminService;

    @Mock
    private SyncJobService syncJobService;

    @Mock
    private SyncJobRepository syncJobRepository;

    @Mock
    private ConnectionActivityRepository connectionActivityRepository;

    @Mock
    private AsyncTaskExecutor taskExecutor;

    @Mock
    private IntegrationManifestRegistry manifests;

    @Mock
    private ConnectionSyncStateProvider githubProvider;

    @Mock
    private IntegrationSyncRunner githubRunner;

    private Connection connection;
    private SyncStatusService service;

    @BeforeEach
    void setUp() {
        Workspace workspace = new Workspace();
        workspace.setId(WORKSPACE_ID);
        connection = new Connection(
            workspace,
            IntegrationKind.GITHUB,
            "100",
            new ConnectionConfig.GitHubAppConfig(100L, "acme", null, Set.of())
        );
        setConnectionId(connection, CONNECTION_ID);
        connection.setState(IntegrationState.ACTIVE);

        lenient()
            .when(connectionAdminService.findInWorkspaceOrThrow(WORKSPACE_ID, CONNECTION_ID))
            .thenReturn(connection);
        lenient().when(githubProvider.kind()).thenReturn(IntegrationKind.GITHUB);
        lenient().when(githubProvider.describe(any(), anyLong())).thenReturn(ConnectionSyncDetails.empty());
        lenient().when(githubProvider.resources(any(), anyLong())).thenReturn(List.of());
        lenient().when(githubRunner.kind()).thenReturn(IntegrationKind.GITHUB);

        lenient()
            .when(syncJobRepository.findFirstByConnection_IdAndStatusInOrderByCreatedAtDesc(anyLong(), any()))
            .thenReturn(Optional.empty());
        lenient()
            .when(syncJobRepository.findFirstByConnection_IdAndStatusInOrderByFinishedAtDesc(anyLong(), any()))
            .thenReturn(Optional.empty());
        lenient().when(connectionActivityRepository.findById(anyLong())).thenReturn(Optional.empty());

        service = new SyncStatusService(
            connectionAdminService,
            syncJobService,
            syncJobRepository,
            connectionActivityRepository,
            taskExecutor,
            List.of(githubProvider),
            List.of(githubRunner)
        );
    }

    // --- health derivation ---

    @Test
    void getStatus_pendingConnection_healthIsPending() {
        connection.setState(IntegrationState.PENDING);

        assertThat(service.getStatus(WORKSPACE_ID, CONNECTION_ID).health()).isEqualTo(ConnectionHealth.PENDING);
    }

    @Test
    void getStatus_suspendedConnection_healthIsSuspended() {
        connection.setState(IntegrationState.SUSPENDED);

        assertThat(service.getStatus(WORKSPACE_ID, CONNECTION_ID).health()).isEqualTo(ConnectionHealth.SUSPENDED);
    }

    @Test
    void getStatus_activeNoJobsNoResources_healthIsHealthy() {
        assertThat(service.getStatus(WORKSPACE_ID, CONNECTION_ID).health()).isEqualTo(ConnectionHealth.HEALTHY);
    }

    @Test
    void getStatus_activeLastFinishedJobFailed_healthIsFailed() {
        SyncJob failed = terminalJob(SyncJobStatus.FAILED);
        when(
            syncJobRepository.findFirstByConnection_IdAndStatusInOrderByFinishedAtDesc(
                CONNECTION_ID,
                SyncJobStatus.TERMINAL
            )
        ).thenReturn(Optional.of(failed));

        assertThat(service.getStatus(WORKSPACE_ID, CONNECTION_ID).health()).isEqualTo(ConnectionHealth.FAILED);
    }

    @Test
    void getStatus_activeLastFinishedJobSucceededWithWarnings_healthIsDegraded() {
        SyncJob warned = terminalJob(SyncJobStatus.SUCCEEDED_WITH_WARNINGS);
        when(
            syncJobRepository.findFirstByConnection_IdAndStatusInOrderByFinishedAtDesc(
                CONNECTION_ID,
                SyncJobStatus.TERMINAL
            )
        ).thenReturn(Optional.of(warned));

        assertThat(service.getStatus(WORKSPACE_ID, CONNECTION_ID).health()).isEqualTo(ConnectionHealth.DEGRADED);
    }

    @Test
    void getStatus_erroredResourcePresent_healthIsDegraded() {
        when(githubProvider.resources(any(), anyLong())).thenReturn(
            List.of(
                new SyncResourceState(
                    1L,
                    "owner/repo",
                    "repo",
                    SyncResourceState.Type.REPOSITORY,
                    "ERROR",
                    null,
                    null,
                    null,
                    "boom",
                    null,
                    null
                )
            )
        );

        var status = service.getStatus(WORKSPACE_ID, CONNECTION_ID);

        assertThat(status.health()).isEqualTo(ConnectionHealth.DEGRADED);
        assertThat(status.resourceCounts().total()).isEqualTo(1L);
        assertThat(status.resourceCounts().errored()).isEqualTo(1L);
    }

    @Test
    void getStatus_vendorHealthDegraded_healthIsDegraded() {
        when(githubProvider.describe(any(), anyLong())).thenReturn(
            new ConnectionSyncDetails(null, null, null, null, true, "installation suspended")
        );

        assertThat(service.getStatus(WORKSPACE_ID, CONNECTION_ID).health()).isEqualTo(ConnectionHealth.DEGRADED);
    }

    @Test
    void getStatus_noProviderRegistered_gracefullyDegradesToEmptyDetails() {
        SyncStatusService noProviders = new SyncStatusService(
            connectionAdminService,
            syncJobService,
            syncJobRepository,
            connectionActivityRepository,
            taskExecutor,
            List.of(),
            List.of()
        );

        var status = noProviders.getStatus(WORKSPACE_ID, CONNECTION_ID);

        assertThat(status.health()).isEqualTo(ConnectionHealth.HEALTHY);
        assertThat(status.webhookRegistered()).isNull();
        assertThat(status.rateLimit()).isNull();
        assertThat(status.resourceCounts().total()).isZero();
    }

    @Test
    void getStatus_activityRecorded_includesLastEventFieldsFromActivityRepository_notProvider() {
        Instant lastEventAt = Instant.parse("2026-07-14T10:00:00Z");
        when(connectionActivityRepository.findById(CONNECTION_ID)).thenReturn(
            Optional.of(new ConnectionActivity(CONNECTION_ID, WORKSPACE_ID, lastEventAt, "push"))
        );

        var status = service.getStatus(WORKSPACE_ID, CONNECTION_ID);

        assertThat(status.lastEventProcessedAt()).isEqualTo(lastEventAt);
        assertThat(status.lastEventType()).isEqualTo("push");
    }

    @Test
    void getStatus_noActivityRecorded_lastEventFieldsAreNull() {
        var status = service.getStatus(WORKSPACE_ID, CONNECTION_ID);

        assertThat(status.lastEventProcessedAt()).isNull();
        assertThat(status.lastEventType()).isNull();
    }

    // --- trigger ---

    @Test
    void triggerSync_notActiveConnection_throwsStateConflict() {
        connection.setState(IntegrationState.SUSPENDED);

        assertThatThrownBy(() ->
            service.triggerSync(WORKSPACE_ID, CONNECTION_ID, SyncJobType.RECONCILIATION, null)
        ).isInstanceOf(SyncStateConflictException.class);
        verify(syncJobService, never()).beginJob(any());
    }

    @Test
    void triggerSync_noRunnerForKind_throwsSyncNotSupported() {
        SyncStatusService noRunners = new SyncStatusService(
            connectionAdminService,
            syncJobService,
            syncJobRepository,
            connectionActivityRepository,
            taskExecutor,
            List.of(githubProvider),
            List.of()
        );

        assertThatThrownBy(() ->
            noRunners.triggerSync(WORKSPACE_ID, CONNECTION_ID, SyncJobType.RECONCILIATION, null)
        ).isInstanceOf(SyncNotSupportedException.class);
    }

    @Test
    void triggerSync_backfillUnsupportedByRunner_throwsSyncNotSupported() {
        when(githubRunner.supportsBackfill()).thenReturn(false);

        assertThatThrownBy(() ->
            service.triggerSync(WORKSPACE_ID, CONNECTION_ID, SyncJobType.BACKFILL, null)
        ).isInstanceOf(SyncNotSupportedException.class);
    }

    @Test
    void triggerSync_newJob_dispatchesAsyncAndReturnsCreatedTrue() {
        SyncJob created = pendingJob();
        SyncJobService.Started started = new SyncJobService.Started(created, null);
        when(
            syncJobService.beginJob(
                new SyncJobRequest(
                    WORKSPACE_ID,
                    CONNECTION_ID,
                    IntegrationKind.GITHUB,
                    SyncJobType.RECONCILIATION,
                    SyncJobTrigger.MANUAL,
                    42L
                )
            )
        ).thenReturn(started);

        SyncStatusService.TriggerOutcome outcome = service.triggerSync(
            WORKSPACE_ID,
            CONNECTION_ID,
            SyncJobType.RECONCILIATION,
            42L
        );

        assertThat(outcome.created()).isTrue();
        assertThat(outcome.job().id()).isEqualTo(created.getId());
        verify(taskExecutor).execute(any());
    }

    @Test
    void triggerSync_duplicateActiveJob_returnsCreatedFalseWithActiveJob() {
        SyncJob active = pendingJob();
        when(
            syncJobService.beginJob(
                new SyncJobRequest(
                    WORKSPACE_ID,
                    CONNECTION_ID,
                    IntegrationKind.GITHUB,
                    SyncJobType.RECONCILIATION,
                    SyncJobTrigger.MANUAL,
                    null
                )
            )
        ).thenThrow(new SyncJobConflictException(active));

        SyncStatusService.TriggerOutcome outcome = service.triggerSync(
            WORKSPACE_ID,
            CONNECTION_ID,
            SyncJobType.RECONCILIATION,
            null
        );

        assertThat(outcome.created()).isFalse();
        assertThat(outcome.job().id()).isEqualTo(active.getId());
        verify(taskExecutor, never()).execute(any());
    }

    // --- catalog ---

    @Test
    void catalog_joinsManifestRegistryWithExistingConnections() {
        when(connectionAdminService.manifests()).thenReturn(manifests);
        when(manifests.registeredKinds()).thenReturn(Set.of(IntegrationKind.GITHUB, IntegrationKind.SLACK));
        when(manifests.manifestFor(any())).thenReturn(Optional.empty());
        when(connectionAdminService.listForWorkspace(WORKSPACE_ID)).thenReturn(List.of(connection));

        List<IntegrationCatalogEntryDTO> catalog = service.catalog(WORKSPACE_ID);

        assertThat(catalog).hasSize(2);
        IntegrationCatalogEntryDTO githubEntry = catalog
            .stream()
            .filter(e -> e.kind() == IntegrationKind.GITHUB)
            .findFirst()
            .orElseThrow();
        assertThat(githubEntry.connected()).isTrue();
        assertThat(githubEntry.connectionId()).isEqualTo(CONNECTION_ID);

        IntegrationCatalogEntryDTO slackEntry = catalog
            .stream()
            .filter(e -> e.kind() == IntegrationKind.SLACK)
            .findFirst()
            .orElseThrow();
        assertThat(slackEntry.connected()).isFalse();
        assertThat(slackEntry.connectionId()).isNull();
    }

    // --- helpers ---

    private SyncJob terminalJob(SyncJobStatus status) {
        SyncJob job = pendingJob();
        job.setStatus(status);
        job.setFinishedAt(Instant.now());
        return job;
    }

    private SyncJob pendingJob() {
        Workspace ws = new Workspace();
        ws.setId(WORKSPACE_ID);
        SyncJob job = new SyncJob(
            ws,
            connection,
            IntegrationKind.GITHUB,
            SyncJobType.RECONCILIATION,
            SyncJobTrigger.MANUAL,
            null
        );
        job.setId(555L);
        return job;
    }

    private static void setConnectionId(Connection connection, long id) {
        try {
            java.lang.reflect.Field field = Connection.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(connection, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
