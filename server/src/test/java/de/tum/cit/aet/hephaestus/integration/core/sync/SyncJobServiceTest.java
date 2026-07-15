package de.tum.cit.aet.hephaestus.integration.core.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
        lenient().when(connection.getWorkspace()).thenReturn(workspace);
        lenient().when(connection.getKind()).thenReturn(IntegrationKind.GITHUB);
        lenient().when(connection.getState()).thenReturn(IntegrationState.ACTIVE);
        lenient()
            .when(connectionRepository.findByIdAndWorkspaceId(CONNECTION_ID, WORKSPACE_ID))
            .thenReturn(Optional.of(connection));

        // No abandoned jobs by default; individual tests override as needed.
        lenient().when(syncJobRepository.findAbandonedForConnection(anyLong(), anyLong())).thenReturn(List.of());
        lenient().when(syncJobRepository.findAbandoned(anyLong())).thenReturn(List.of());
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
        lenient()
            .when(syncJobRepository.markRunning(anyLong()))
            .thenAnswer(inv -> {
                SyncJob job = store.get((Long) inv.getArgument(0));
                if (job == null || job.getStatus() != SyncJobStatus.PENDING) {
                    return 0;
                }
                job.setStatus(SyncJobStatus.RUNNING);
                job.setStartedAt(Instant.now());
                job.setHeartbeatAt(Instant.now());
                return 1;
            });
        lenient()
            .when(syncJobRepository.findCancelFlags(any()))
            .thenAnswer(inv -> {
                List<Long> ids = inv.getArgument(0);
                return ids
                    .stream()
                    .map(store::get)
                    .filter(java.util.Objects::nonNull)
                    .map(job ->
                        new SyncJobRepository.CancelFlagProjection() {
                            @Override
                            public Long getId() {
                                return job.getId();
                            }

                            @Override
                            public boolean isCancelRequested() {
                                return job.isCancelRequested();
                            }
                        }
                    )
                    .toList();
            });
        lenient()
            .when(
                syncJobRepository.completeActiveJob(anyLong(), any(), any(), any(), any(), any(), any(), anyBoolean())
            )
            .thenAnswer(inv -> {
                SyncJob job = store.get((Long) inv.getArgument(0));
                if (job == null || !SyncJobStatus.ACTIVE.contains(job.getStatus())) {
                    return 0;
                }
                job.setStatus(inv.getArgument(1));
                job.setFinishedAt(Instant.now());
                job.setErrorSummary(inv.getArgument(2));
                job.setItemsProcessed(inv.getArgument(3));
                job.setItemsTotal(inv.getArgument(4));
                Map<String, Object> progress = inv.getArgument(5);
                if (!progress.isEmpty()) {
                    job.setProgress(progress);
                }
                return 1;
            });

        service = new SyncJobService(syncJobRepository, connectionRepository, eventPublisher, transactionTemplate);
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
    void beginJob_connectionNotActive_rejectsBeforeCreatingJob() {
        when(connection.getState()).thenReturn(IntegrationState.UNINSTALLED);

        assertThatThrownBy(() -> service.beginJob(defaultRequest()))
            .isInstanceOf(SyncStateConflictException.class)
            .hasMessageContaining("UNINSTALLED");

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
        when(syncJobRepository.findAbandonedForConnection(CONNECTION_ID, 900)).thenReturn(List.of(abandoned));
        when(syncJobRepository.markAbandoned(7L, "Abandoned: no heartbeat (likely pod restart)", 900)).thenReturn(1);
        // After the reap, the guard-check query sees no more active jobs.
        when(
            syncJobRepository.findFirstByConnection_IdAndStatusInOrderByCreatedAtDesc(
                CONNECTION_ID,
                SyncJobStatus.ACTIVE
            )
        ).thenReturn(Optional.empty());

        SyncJobService.Started started = service.beginJob(defaultRequest());

        verify(syncJobRepository).markAbandoned(7L, "Abandoned: no heartbeat (likely pod restart)", 900);
        assertThat(started.job()).isNotNull();
        assertThat(started.job().getStatus()).isEqualTo(SyncJobStatus.PENDING);
    }

    @Test
    void beginJob_partialIndexRace_reQueriesInFreshTxAndRethrowsWhenNoWinnerVisible() {
        // A DataIntegrityViolation whose fresh-tx re-read still shows NO active row is a genuine
        // integrity error, not a lost race — it must propagate rather than be masked as a 409-absorb.
        when(
            syncJobRepository.findFirstByConnection_IdAndStatusInOrderByCreatedAtDesc(
                CONNECTION_ID,
                SyncJobStatus.ACTIVE
            )
        )
            .thenReturn(Optional.empty())
            .thenReturn(Optional.empty());
        doThrow(new DataIntegrityViolationException("ux_sync_job_active")).when(syncJobRepository).saveAndFlush(any());

        assertThatThrownBy(() -> service.beginJob(defaultRequest())).isInstanceOf(
            DataIntegrityViolationException.class
        );
    }

    // --- outcome mapping ---

    @Test
    void executeBody_normalReturn_marksSucceeded() {
        SyncJobService.Started started = beginTestJob();

        service.executeBody(started, handle -> {});

        assertThat(started.job().getStatus()).isEqualTo(SyncJobStatus.SUCCEEDED);
        assertThat(started.job().getFinishedAt()).isNotNull();
        verify(syncJobRepository).pruneOldJobs(CONNECTION_ID, 50);
        verify(eventPublisher).publishEvent(
            new SyncStateChangedEvent(
                WORKSPACE_ID,
                CONNECTION_ID,
                IntegrationKind.GITHUB,
                SyncStateChangedEvent.Scope.RESOURCES
            )
        );
    }

    @Test
    void executeBody_duplicateDispatch_invokesBodyOnlyOnce() {
        SyncJobService.Started started = beginTestJob();
        java.util.concurrent.atomic.AtomicInteger invocations = new java.util.concurrent.atomic.AtomicInteger();

        service.executeBody(started, handle -> invocations.incrementAndGet());
        service.executeBody(started, handle -> invocations.incrementAndGet());

        assertThat(invocations).hasValue(1);
    }

    @Test
    void executeBody_concurrentDuplicate_doesNotUnregisterTheRunningHandle() throws Exception {
        SyncJobService.Started started = beginTestJob();
        CountDownLatch bodyEntered = new CountDownLatch(1);
        CountDownLatch releaseBody = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var running = executor.submit(() ->
                service.executeBody(started, handle -> {
                    bodyEntered.countDown();
                    try {
                        releaseBody.await(2, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                })
            );
            assertThat(bodyEntered.await(2, TimeUnit.SECONDS)).isTrue();

            service.executeBody(started, handle -> {});
            service.refreshLeases();

            verify(syncJobRepository).touchHeartbeat(List.of(started.job().getId()));
            releaseBody.countDown();
            running.get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void stop_cancelsAndInterruptsRunningJob() throws Exception {
        SyncJobService.Started started = beginTestJob();
        CountDownLatch bodyEntered = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        AtomicBoolean cancellationObserved = new AtomicBoolean();
        service.start();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var running = executor.submit(() ->
                service.executeBody(started, handle -> {
                    bodyEntered.countDown();
                    try {
                        Thread.sleep(Duration.ofMinutes(1));
                    } catch (InterruptedException e) {
                        cancellationObserved.set(handle.isCancellationRequested());
                        interrupted.countDown();
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("provider call interrupted", e);
                    }
                })
            );
            assertThat(bodyEntered.await(2, TimeUnit.SECONDS)).isTrue();

            service.stop();

            running.get(2, TimeUnit.SECONDS);
        }

        assertThat(interrupted.getCount()).isZero();
        assertThat(cancellationObserved).isTrue();
        assertThat(started.job().getStatus()).isEqualTo(SyncJobStatus.CANCELLED);
        verify(syncJobRepository).markCancelRequested(started.job().getId(), SyncJobStatus.ACTIVE);
    }

    @Test
    void executeBody_cancellationObservedByRunner_marksCancelled() {
        SyncJobService.Started started = beginTestJob();

        service.executeBody(started, handle -> {
            started.handle().refreshCancellation(true);
            assertThat(handle.isCancellationRequested()).isTrue();
            // A runner that actually stops in response to the flag reports it — only then is the job CANCELLED.
            handle.reportCancelled();
        });

        assertThat(started.job().getStatus()).isEqualTo(SyncJobStatus.CANCELLED);
    }

    @Test
    void executeBody_cancellationSeenButBodyReturnsWithoutReporting_marksSucceeded() {
        SyncJobService.Started started = beginTestJob();

        // The flag is set and observed, but the body finishes its work normally without reportCancelled():
        // the job is SUCCEEDED, not falsely CANCELLED (the mislabel fix).
        service.executeBody(started, handle -> {
            started.handle().refreshCancellation(true);
            assertThat(handle.isCancellationRequested()).isTrue();
        });

        assertThat(started.job().getStatus()).isEqualTo(SyncJobStatus.SUCCEEDED);
    }

    @Test
    void executeBody_databaseCancellationFromAnotherReplicaWinsAtCompletion() {
        SyncJobService.Started started = beginTestJob();
        when(
            syncJobRepository.completeActiveJob(anyLong(), any(), any(), any(), any(), any(), any(), eq(true))
        ).thenReturn(0);
        when(syncJobRepository.completeCancelRequestedJob(anyLong(), any(), any(), any(), any())).thenReturn(1);

        service.executeBody(started, handle -> {});

        verify(syncJobRepository).completeCancelRequestedJob(
            eq(started.job().getId()),
            any(),
            any(),
            any(),
            eq(SyncJobStatus.ACTIVE)
        );
    }

    @Test
    void executeBody_runnerReportsWarnings_marksSucceededWithWarnings() {
        SyncJobService.Started started = beginTestJob();

        service.executeBody(started, SyncJobHandle::reportWarnings);

        assertThat(started.job().getStatus()).isEqualTo(SyncJobStatus.SUCCEEDED_WITH_WARNINGS);
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

    @Test
    void executeBody_rowReapedTerminalBeforeDispatch_neitherResurrectsNorOverwrites() {
        // The zombie sweep flipped this row to FAILED after beginJob but before the async body ran.
        SyncJobService.Started started = beginTestJob();
        started.job().setStatus(SyncJobStatus.FAILED);
        java.util.concurrent.atomic.AtomicBoolean bodyInvoked = new java.util.concurrent.atomic.AtomicBoolean();

        service.executeBody(started, handle -> bodyInvoked.set(true));

        assertThat(bodyInvoked).isFalse();
        assertThat(started.job().getStatus()).isEqualTo(SyncJobStatus.FAILED);
    }

    @Test
    void executeBody_rowReapedTerminalMidBody_completionWriteIsSkipped() {
        SyncJobService.Started started = beginTestJob();

        service.executeBody(started, handle -> {
            // Simulate the zombie sweep marking this RUNNING row abandoned (FAILED) mid-flight.
            started.job().setStatus(SyncJobStatus.FAILED);
        });

        assertThat(started.job().getStatus()).isEqualTo(SyncJobStatus.FAILED);
    }

    // --- zombie reaping ---

    @Test
    void reapAbandonedJobs_findsAndFailsStaleRunningJobs() {
        SyncJob stale = newJob(8L, SyncJobStatus.RUNNING);
        stale.setHeartbeatAt(Instant.now().minus(java.time.Duration.ofMinutes(30)));
        when(syncJobRepository.findAbandoned(900)).thenReturn(List.of(stale));
        when(syncJobRepository.markAbandoned(8L, "Abandoned: no heartbeat (likely pod restart)", 900)).thenReturn(1);

        int reaped = service.reapAbandonedJobs();

        assertThat(reaped).isEqualTo(1);
        verify(syncJobRepository).markAbandoned(8L, "Abandoned: no heartbeat (likely pod restart)", 900);
        verify(eventPublisher).publishEvent(any(SyncStateChangedEvent.class));
        verify(syncJobRepository, never()).save(any());
    }

    @Test
    void reapAbandonedJobs_noStaleJobs_reapsNothing() {
        when(syncJobRepository.findAbandoned(anyLong())).thenReturn(List.of());

        assertThat(service.reapAbandonedJobs()).isZero();
    }

    @Test
    void reapAbandonedJobs_heartbeatRefreshedAfterCandidateRead_doesNotReapOrPublish() {
        SyncJob candidate = newJob(8L, SyncJobStatus.RUNNING);
        when(syncJobRepository.findAbandoned(900)).thenReturn(List.of(candidate));
        when(syncJobRepository.markAbandoned(8L, "Abandoned: no heartbeat (likely pod restart)", 900)).thenReturn(0);

        assertThat(service.reapAbandonedJobs()).isZero();

        verify(eventPublisher, never()).publishEvent(any(SyncStateChangedEvent.class));
    }

    @Test
    void reapAbandonedJobs_localExecutionCancelsWithoutReleasingActiveFence() throws Exception {
        SyncJobService.Started started = beginTestJob();
        CountDownLatch bodyEntered = new CountDownLatch(1);
        CountDownLatch releaseBody = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        when(syncJobRepository.findAbandoned(900)).thenReturn(List.of(started.job()));

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var running = executor.submit(() ->
                service.executeBody(started, handle -> {
                    bodyEntered.countDown();
                    try {
                        releaseBody.await();
                    } catch (InterruptedException e) {
                        interrupted.set(handle.isCancellationRequested());
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("stale local execution interrupted", e);
                    }
                })
            );
            assertThat(bodyEntered.await(2, TimeUnit.SECONDS)).isTrue();

            try {
                assertThat(service.reapAbandonedJobs()).isZero();
                assertThat(interrupted).isTrue();
                verify(syncJobRepository, never()).markAbandoned(anyLong(), any(), anyLong());
                verify(syncJobRepository).markCancelRequested(started.job().getId(), SyncJobStatus.ACTIVE);
            } finally {
                releaseBody.countDown();
                running.get(2, TimeUnit.SECONDS);
            }
        }

        assertThat(started.job().getStatus()).isEqualTo(SyncJobStatus.CANCELLED);
    }

    // --- lease heartbeat / cancel-flag refresh ---

    @Test
    void refreshLeases_touchesHeartbeatAndRefreshesRegisteredHandlesCancelFlag() {
        SyncJobService.Started started = beginTestJob();
        long jobId = started.job().getId();
        SyncJobRepository.CancelFlagProjection projection = mock(SyncJobRepository.CancelFlagProjection.class);
        when(projection.getId()).thenReturn(jobId);
        when(projection.isCancelRequested()).thenReturn(true);
        when(syncJobRepository.findCancelFlags(List.of(jobId))).thenReturn(List.of(projection));

        // A handle is registered in the in-JVM registry only while executeBody is running, so drive the
        // heartbeat pass from inside the body — the one moment a genuinely-RUNNING job's lease exists.
        service.executeBody(started, handle -> service.refreshLeases());

        verify(syncJobRepository).touchHeartbeat(List.of(jobId));
        assertThat(started.handle().isCancellationRequested()).isTrue();
    }

    @Test
    void refreshLeases_staleFalseNeverClearsCancellation() {
        SyncJobService.Started started = beginTestJob();
        long jobId = started.job().getId();
        SyncJobRepository.CancelFlagProjection projection = mock(SyncJobRepository.CancelFlagProjection.class);
        when(projection.getId()).thenReturn(jobId);
        when(projection.isCancelRequested()).thenReturn(false);
        when(syncJobRepository.findCancelFlags(List.of(jobId))).thenReturn(List.of(projection));

        service.executeBody(started, handle -> {
            handle.refreshCancellation(true);

            service.refreshLeases();

            assertThat(handle.isCancellationRequested()).isTrue();
        });
    }

    @Test
    void refreshLeases_noActiveHandles_skipsWithoutTouchingRepository() {
        service.refreshLeases();

        verify(syncJobRepository, never()).touchHeartbeat(any());
    }

    // --- cooperative cancel request ---

    @Test
    void requestCancel_pendingJob_flipsFlagViaTargetedUpdateWithoutTouchingAnyHandle() {
        SyncJobService.Started started = beginTestJob(); // PENDING: no in-JVM handle registered yet
        long jobId = started.job().getId();
        when(syncJobRepository.findByIdAndWorkspace_Id(jobId, WORKSPACE_ID)).thenReturn(Optional.of(started.job()));
        when(syncJobRepository.markCancelRequested(jobId, SyncJobStatus.ACTIVE)).thenReturn(1);

        service.requestCancel(WORKSPACE_ID, jobId);

        // Targeted flag-only UPDATE; a PENDING job has no registered handle, so nothing local is refreshed.
        verify(syncJobRepository).markCancelRequested(jobId, SyncJobStatus.ACTIVE);
        assertThat(started.handle().isCancellationRequested()).isFalse();
    }

    @Test
    void requestCancel_runningJob_refreshesRegisteredLocalHandleImmediately() {
        SyncJobService.Started started = beginTestJob();
        long jobId = started.job().getId();
        when(syncJobRepository.findByIdAndWorkspace_Id(jobId, WORKSPACE_ID)).thenReturn(Optional.of(started.job()));
        when(syncJobRepository.markCancelRequested(jobId, SyncJobStatus.ACTIVE)).thenReturn(1);

        // The handle is registered only while executeBody runs; issue the cancel from inside the body so it
        // lands on the same JVM that's executing the job and the local handle refreshes without the sweep.
        service.executeBody(started, handle -> {
            service.requestCancel(WORKSPACE_ID, jobId);
            assertThat(handle.isCancellationRequested()).isTrue();
        });
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
