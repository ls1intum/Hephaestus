package de.tum.cit.aet.hephaestus.integration.core.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Unit coverage for the job-execution template: the one-active-job guard, outcome mapping
 * (success/cancel/failure), zombie reaping (both the full sweep and the inline per-connection reap),
 * retention pruning, and the lease-heartbeat cancel-flag refresh. Follows {@code AgentJobServiceTest}'s
 * pattern of making the mocked {@link TransactionTemplate} actually invoke its callback.
 */
class SyncJobServiceTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 1L;
    private static final long CONNECTION_ID = 10L;

    @Mock
    private SyncJobRepository syncJobRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private ConnectionRepository connectionRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private TransactionTemplate transactionTemplate;

    private Workspace workspace;
    private Connection connection;
    private SyncJobService service;
    private final Map<Long, SyncJob> store = new HashMap<>();
    private final AtomicLong idSequence = new AtomicLong(100L);

    @BeforeEach
    void setUp() {
        // Run the TransactionTemplate callback inline (execute + executeWithoutResult).
        lenient()
            .when(transactionTemplate.execute(any()))
            .thenAnswer(inv -> {
                TransactionCallback<?> callback = inv.getArgument(0);
                return callback.doInTransaction(mock(TransactionStatus.class));
            });
        lenient()
            .doAnswer(inv -> {
                Consumer<TransactionStatus> action = inv.getArgument(0);
                action.accept(mock(TransactionStatus.class));
                return null;
            })
            .when(transactionTemplate)
            .executeWithoutResult(any());

        // Workspace is a guarded owned-entity (NoMockingOwnedEntitiesTest) — build the real object.
        workspace = new Workspace();
        workspace.setId(WORKSPACE_ID);
        connection = mock(Connection.class);
        lenient().when(connection.getId()).thenReturn(CONNECTION_ID);

        lenient().when(workspaceRepository.getReferenceById(WORKSPACE_ID)).thenReturn(workspace);
        lenient().when(connectionRepository.getReferenceById(CONNECTION_ID)).thenReturn(connection);

        // No abandoned jobs by default; individual tests override as needed.
        lenient().when(syncJobRepository.findAbandonedForConnection(anyLong(), any(), any())).thenReturn(List.of());
        lenient().when(syncJobRepository.findAbandoned(any(), any())).thenReturn(List.of());
        lenient()
            .when(syncJobRepository.findFirstByConnection_IdAndStatusInOrderByCreatedAtDesc(anyLong(), any()))
            .thenReturn(Optional.empty());
        // In-memory "table" backing findById, so markRunning/completeJob/persistProgress (which all
        // re-fetch by id before mutating) operate on the SAME instance the test holds via Started.job().
        lenient()
            .when(syncJobRepository.saveAndFlush(any()))
            .thenAnswer(inv -> {
                SyncJob job = inv.getArgument(0);
                if (job.getId() == null) {
                    job.setId(idSequence.incrementAndGet());
                }
                store.put(job.getId(), job);
                return job;
            });
        lenient()
            .when(syncJobRepository.save(any()))
            .thenAnswer(inv -> {
                SyncJob job = inv.getArgument(0);
                store.put(job.getId(), job);
                return job;
            });
        lenient()
            .when(syncJobRepository.findById(any()))
            .thenAnswer(inv -> Optional.ofNullable(store.get((Long) inv.getArgument(0))));

        service = new SyncJobService(
            syncJobRepository,
            workspaceRepository,
            connectionRepository,
            eventPublisher,
            transactionTemplate
        );
    }

    private SyncJob newJob(long id, SyncJobStatus status) {
        SyncJob job = new SyncJob(
            workspace,
            connection,
            IntegrationKind.GITHUB,
            SyncJobType.INITIAL,
            SyncJobTrigger.MANUAL,
            null
        );
        job.setId(id);
        job.setStatus(status);
        return job;
    }

    // --- one-active-job guard ---

    @Test
    void beginJob_connectionAlreadyHasActiveJob_throwsConflictCarryingThatJob() {
        SyncJob active = newJob(5L, SyncJobStatus.RUNNING);
        when(
            syncJobRepository.findFirstByConnection_IdAndStatusInOrderByCreatedAtDesc(
                CONNECTION_ID,
                SyncJobStatus.ACTIVE
            )
        ).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.beginJob(defaultRequest()))
            .isInstanceOf(SyncJobConflictException.class)
            .satisfies(e -> assertThat(((SyncJobConflictException) e).activeJob()).isSameAs(active));

        verify(syncJobRepository, never()).saveAndFlush(any());
    }

    @Test
    void beginJob_partialIndexRace_translatesDataIntegrityViolationToConflict() {
        SyncJob raced = newJob(6L, SyncJobStatus.PENDING);
        // First read (before insert attempt): no active job visible yet.
        // Second read (after the constraint violation): the concurrent winner is now visible.
        when(
            syncJobRepository.findFirstByConnection_IdAndStatusInOrderByCreatedAtDesc(
                CONNECTION_ID,
                SyncJobStatus.ACTIVE
            )
        )
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(raced));
        // doThrow(...).when(mock) — NOT when(mock.method()).thenThrow(...) — because the latter's argument
        // expression (`saveAndFlush(any())`) is a real invocation that would run the setUp() answer stub
        // (with a null dummy argument) before this line even finishes evaluating, NPE-ing on job.getId().
        doThrow(new DataIntegrityViolationException("ux_sync_job_active")).when(syncJobRepository).saveAndFlush(any());

        assertThatThrownBy(() -> service.beginJob(defaultRequest()))
            .isInstanceOf(SyncJobConflictException.class)
            .satisfies(e -> assertThat(((SyncJobConflictException) e).activeJob()).isSameAs(raced));
    }

    @Test
    void beginJob_inlineReapClearsAbandonedJobBeforeGuardCheck_thenSucceeds() {
        SyncJob abandoned = newJob(7L, SyncJobStatus.RUNNING);
        when(
            syncJobRepository.findAbandonedForConnection(eq(CONNECTION_ID), eq(SyncJobStatus.ACTIVE), any())
        ).thenReturn(List.of(abandoned));
        // After the reap, the guard-check query sees no more active jobs.
        when(
            syncJobRepository.findFirstByConnection_IdAndStatusInOrderByCreatedAtDesc(
                CONNECTION_ID,
                SyncJobStatus.ACTIVE
            )
        ).thenReturn(Optional.empty());

        SyncJobService.Started started = service.beginJob(defaultRequest());

        assertThat(abandoned.getStatus()).isEqualTo(SyncJobStatus.FAILED);
        assertThat(abandoned.getErrorSummary()).contains("Abandoned");
        assertThat(started.job()).isNotNull();
        assertThat(started.job().getStatus()).isEqualTo(SyncJobStatus.PENDING);
    }

    // --- outcome mapping ---

    @Test
    void executeBody_normalReturn_marksSucceeded() {
        SyncJobService.Started started = beginTestJob();

        service.executeBody(started, handle -> {});

        assertThat(started.job().getStatus()).isEqualTo(SyncJobStatus.SUCCEEDED);
        assertThat(started.job().getFinishedAt()).isNotNull();
        verify(syncJobRepository).pruneOldJobs(CONNECTION_ID, 50);
    }

    @Test
    void executeBody_cancellationObservedByRunner_marksCancelled() {
        SyncJobService.Started started = beginTestJob();

        service.executeBody(started, handle -> {
            started.handle().refreshCancellation(true);
            assertThat(handle.isCancellationRequested()).isTrue();
            // Runner exits early once it observes cancellation.
        });

        assertThat(started.job().getStatus()).isEqualTo(SyncJobStatus.CANCELLED);
    }

    @Test
    void executeBody_bodyThrows_marksFailedWithTruncatedErrorSummary() {
        SyncJobService.Started started = beginTestJob();

        service.executeBody(started, handle -> {
            throw new RuntimeException("boom");
        });

        assertThat(started.job().getStatus()).isEqualTo(SyncJobStatus.FAILED);
        assertThat(started.job().getErrorSummary()).contains("RuntimeException").contains("boom");
    }

    @Test
    void executeBody_progressReportedInBody_isFlushedOnTerminalWrite() {
        SyncJobService.Started started = beginTestJob();

        service.executeBody(started, handle ->
            handle.progress(4, 12, java.util.Map.of("currentStep", "pull-requests"))
        );

        assertThat(started.job().getStatus()).isEqualTo(SyncJobStatus.SUCCEEDED);
        assertThat(started.job().getItemsProcessed()).isEqualTo(4);
        assertThat(started.job().getItemsTotal()).isEqualTo(12);
        assertThat(started.job().getProgress()).containsEntry("currentStep", "pull-requests");
    }

    // --- zombie reaping ---

    @Test
    void reapAbandonedJobs_findsAndFailsStaleRunningJobs() {
        SyncJob stale = newJob(8L, SyncJobStatus.RUNNING);
        stale.setHeartbeatAt(Instant.now().minus(java.time.Duration.ofMinutes(30)));
        when(syncJobRepository.findAbandoned(eq(SyncJobStatus.ACTIVE), any())).thenReturn(List.of(stale));

        int reaped = service.reapAbandonedJobs();

        assertThat(reaped).isEqualTo(1);
        assertThat(stale.getStatus()).isEqualTo(SyncJobStatus.FAILED);
        assertThat(stale.getErrorSummary()).contains("Abandoned: no heartbeat");
        verify(syncJobRepository).save(stale);
    }

    @Test
    void reapAbandonedJobs_noStaleJobs_reapsNothing() {
        when(syncJobRepository.findAbandoned(any(), any())).thenReturn(List.of());

        assertThat(service.reapAbandonedJobs()).isZero();
    }

    // --- lease heartbeat / cancel-flag refresh ---

    @Test
    void refreshLeases_touchesHeartbeatAndRefreshesRegisteredHandlesCancelFlag() {
        SyncJobService.Started started = beginTestJob();
        SyncJobRepository.CancelFlagProjection projection = mock(SyncJobRepository.CancelFlagProjection.class);
        when(projection.getId()).thenReturn(started.job().getId());
        when(projection.isCancelRequested()).thenReturn(true);
        when(syncJobRepository.findCancelFlags(List.of(started.job().getId()))).thenReturn(List.of(projection));

        service.refreshLeases();

        verify(syncJobRepository).touchHeartbeat(eq(List.of(started.job().getId())), any(Instant.class));
        assertThat(started.handle().isCancellationRequested()).isTrue();
    }

    @Test
    void refreshLeases_noActiveHandles_skipsWithoutTouchingRepository() {
        service.refreshLeases();

        verify(syncJobRepository, never()).touchHeartbeat(any(), any());
    }

    // --- cooperative cancel request ---

    @Test
    void requestCancel_pendingJob_flipsFlagAndRefreshesLocalHandleImmediately() {
        SyncJobService.Started started = beginTestJob();
        when(syncJobRepository.findByIdAndWorkspace_Id(started.job().getId(), WORKSPACE_ID)).thenReturn(
            Optional.of(started.job())
        );

        service.requestCancel(WORKSPACE_ID, started.job().getId());

        assertThat(started.job().isCancelRequested()).isTrue();
        assertThat(started.handle().isCancellationRequested()).isTrue();
    }

    @Test
    void requestCancel_terminalJob_throwsStateConflict() {
        SyncJob terminal = newJob(9L, SyncJobStatus.SUCCEEDED);
        when(syncJobRepository.findByIdAndWorkspace_Id(9L, WORKSPACE_ID)).thenReturn(Optional.of(terminal));

        assertThatThrownBy(() -> service.requestCancel(WORKSPACE_ID, 9L)).isInstanceOf(
            SyncStateConflictException.class
        );
    }

    @Test
    void requestCancel_unknownJob_throwsEntityNotFound() {
        when(syncJobRepository.findByIdAndWorkspace_Id(999L, WORKSPACE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requestCancel(WORKSPACE_ID, 999L)).isInstanceOf(EntityNotFoundException.class);
    }

    private SyncJobService.Started beginTestJob() {
        return service.beginJob(defaultRequest());
    }

    private static SyncJobRequest defaultRequest() {
        return new SyncJobRequest(
            WORKSPACE_ID,
            CONNECTION_ID,
            IntegrationKind.GITHUB,
            SyncJobType.INITIAL,
            SyncJobTrigger.MANUAL,
            null
        );
    }
}
