package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfig;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfigRepository;
import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
import de.tum.cit.aet.hephaestus.agent.handler.JobTypeHandlerRegistry;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.cit.aet.hephaestus.agent.practice.PracticeAgentRequest;
import de.tum.cit.aet.hephaestus.agent.practice.PracticePiAdapter;
import de.tum.cit.aet.hephaestus.agent.practice.PracticeSandboxSpec;
import de.tum.cit.aet.hephaestus.agent.runtime.AgentResult;
import de.tum.cit.aet.hephaestus.agent.runtime.worker.WorkerProperties;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.NetworkPolicy;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxCancelledException;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxManager;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxResult;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SecurityProfile;
import de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetBlockReason;
import de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetService;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUnpricedUsageBlockedException;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRecorder;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class AgentJobExecutorTest extends BaseUnitTest {

    @Mock
    private LlmUsageRecorder usageRecorder;

    @Mock
    private LlmBudgetService llmBudgetService;

    @Mock
    private AgentJobRepository jobRepository;

    @Mock
    private AgentConfigRepository configRepository;

    @Mock
    private JobTypeHandlerRegistry handlerRegistry;

    @Mock
    private PracticePiAdapter practiceAgent;

    @Mock
    private SandboxManager sandboxManager;

    @Mock
    private AsyncTaskExecutor sandboxExecutor;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SimpleMeterRegistry meterRegistry;

    private AgentJobExecutor executor;

    private static final AgentProperties AGENT_PROPS = new AgentProperties(
        true,
        Duration.ofSeconds(1),
        5,
        5,
        Duration.ofSeconds(25)
    );

    private UUID jobId;
    private AgentJob job;
    private AgentConfig config;
    private ConfigSnapshot snapshot;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();

        executor = new AgentJobExecutor(
            AGENT_PROPS,
            jobRepository,
            configRepository,
            handlerRegistry,
            practiceAgent,
            sandboxManager,
            sandboxExecutor,
            transactionTemplate,
            objectMapper,
            meterRegistry,
            usageRecorder,
            llmBudgetService,
            Optional.empty(),
            Optional.empty()
        );

        jobId = UUID.randomUUID();

        config = new AgentConfig();
        config.setId(10L);
        config.setMaxConcurrentJobs(3);

        snapshot = new ConfigSnapshot(
            ConfigSnapshot.SCHEMA_VERSION,
            10L,
            "test-config",
            "anthropic-messages",
            "https://api.anthropic.com",
            "claude-sonnet-4",
            null,
            null,
            null,
            false,
            null,
            null,
            null,
            600,
            false
        );

        job = new AgentJob();
        job.prePersist();
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setConfig(config);
        job.setConfigSnapshot(snapshot.toJson(objectMapper));
        job.setJobToken("test-token");
        job.setStatus(AgentJobStatus.QUEUED);
        job.setWorkspace(workspaceStub());

        // Claim-time budget recheck (#1368 fix wave): default to NONE so every pre-existing claim
        // test keeps its original meaning. An unstubbed mock would return null here (not a boolean),
        // and null != NONE reads as "blocked" — so this default is load-bearing, not decorative.
        lenient().when(llmBudgetService.blockReason(anyLong())).thenReturn(LlmBudgetBlockReason.NONE);

        // Default: transactionTemplate.execute invokes the callback
        lenient()
            .when(transactionTemplate.execute(any()))
            .thenAnswer(inv -> {
                @SuppressWarnings("unchecked")
                TransactionCallback<Object> callback = inv.getArgument(0);
                return callback.doInTransaction(mock(TransactionStatus.class));
            });

        lenient()
            .doAnswer(inv -> {
                @SuppressWarnings("unchecked")
                java.util.function.Consumer<TransactionStatus> callback = inv.getArgument(0);
                callback.accept(mock(TransactionStatus.class));
                return null;
            })
            .when(transactionTemplate)
            .executeWithoutResult(any());

        // readOnlyTx in prepareAndExecute is built from getTransactionManager(); stub the bridge.
        lenient().when(transactionTemplate.getTransactionManager()).thenReturn(transactionManager);
        lenient().when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));

        // Dispatch of a claimed job's execution runs on the sandbox executor; run it inline so
        // processJob() is synchronously observable, mirroring how the old NATS-era executeJob() ran
        // entirely on the calling (test) thread.
        lenient()
            .doAnswer(inv -> {
                Runnable task = inv.getArgument(0);
                task.run();
                return null;
            })
            .when(sandboxExecutor)
            .execute(any());
    }

    private static Workspace workspaceStub() {
        Workspace workspace = new Workspace();
        workspace.setId(99L);
        return workspace;
    }

    /**
     * A terminal-write-stage job the way {@code persistTerminalState} re-reads it: real jobType +
     * workspace, matching the invariant a persisted {@link AgentJob} always has both (never null in
     * production). Needed since #1368's fix wave reads both unconditionally when no agent-reported
     * usage is present, to write the UNPRICED ledger fallback.
     */
    private static AgentJob freshJob() {
        AgentJob freshJob = new AgentJob();
        freshJob.prePersist();
        freshJob.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        freshJob.setWorkspace(workspaceStub());
        return freshJob;
    }

    @Nested
    @DisplayName("Job-scoped cancellation (#1138)")
    class ScopedCancellation {

        @Test
        @DisplayName("cancelLocalJob returns false and does not touch the sandbox for a job this worker doesn't run")
        void cancelLocalJobUnknownIsNoOp() {
            boolean cancelled = executor.cancelLocalJob(UUID.randomUUID(), "user-cancel");

            Assertions.assertThat(cancelled).isFalse();
            verify(sandboxManager, never()).cancel(any());
        }

        @Test
        @DisplayName("cancelInFlight on a worker with no local jobs cancels nothing (no DB-wide sweep)")
        void cancelInFlightEmptyIsNoOp() {
            executor.cancelInFlight(AgentJobCancellationReason.DRAIN_GRACEFUL);

            verify(sandboxManager, never()).cancel(any());
            verify(jobRepository, never()).transitionToCancelled(any(), any(), any(), any(), any());
            // Critically: never queries all RUNNING jobs cluster-wide.
            verify(jobRepository, never()).findByStatus(AgentJobStatus.RUNNING);
        }

        @Test
        @DisplayName("does NOT deliver when the fenced terminal write loses ownership (orphan-requeued)")
        void doesNotDeliverWhenFencedOut() {
            // Worker has identity "test-worker" → terminal writes are fenced to the owner.
            executor = new AgentJobExecutor(
                AGENT_PROPS,
                jobRepository,
                configRepository,
                handlerRegistry,
                practiceAgent,
                sandboxManager,
                sandboxExecutor,
                transactionTemplate,
                objectMapper,
                meterRegistry,
                usageRecorder,
                llmBudgetService,
                Optional.empty(),
                Optional.of(workerProps("test-worker"))
            );

            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.of(job));
            when(configRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(config));
            when(jobRepository.countByConfigIdAndStatusIn(eq(10L), any())).thenReturn(0L);
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            JobTypeHandler handler = setupFullExecution();
            // Fence loses: another worker owns the job now (it was orphan-requeued mid-execution).
            when(jobRepository.transitionStatusOwnedBy(any(), any(), any(), any(), any(), any())).thenReturn(0);

            executor.processJob(jobId);

            // The job is no longer ours — we must not double-deliver the sibling's findings.
            verify(handler, never()).deliver(any());
        }
    }

    @Nested
    @DisplayName("Claim phase")
    class ClaimPhase {

        @Test
        void returnsFalseAndNeverExecutesWhenSkipLockedReturnsEmpty() {
            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.empty());

            boolean claimed = executor.processJob(jobId);

            assertThat(claimed).isFalse();
            verify(sandboxManager, never()).execute(any());
        }

        @Test
        void returnsFalseAndLeavesJobQueuedWhenConcurrencyLimitReached() {
            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.of(job));
            when(configRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(config));
            when(jobRepository.countByConfigIdAndStatusIn(eq(10L), any())).thenReturn(3L); // equals max

            boolean claimed = executor.processJob(jobId);

            assertThat(claimed).isFalse();
            // No status write at all — the row is untouched, still QUEUED; the next poll retries it.
            verify(jobRepository, never()).save(any());
            verify(sandboxManager, never()).execute(any());
        }

        @Test
        void shouldTransitionToRunningOnSuccessfulClaim() {
            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.of(job));
            when(configRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(config));
            when(jobRepository.countByConfigIdAndStatusIn(eq(10L), any())).thenReturn(0L);
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            setupFullExecution();

            boolean claimed = executor.processJob(jobId);

            assertThat(claimed).isTrue();
            // Assert the actual claim contract, not just "save was called": status flips to RUNNING.
            ArgumentCaptor<AgentJob> captured = ArgumentCaptor.forClass(AgentJob.class);
            verify(jobRepository).save(captured.capture());
            assertThat(captured.getValue().getStatus()).isEqualTo(AgentJobStatus.RUNNING);
            assertThat(captured.getValue().getStartedAt()).isNotNull();
        }
    }

    /**
     * Claim-time budget recheck (#1368 fix wave): a workspace can pre-queue jobs faster than the
     * cap updates, so every claim rechecks {@code llmBudgetService.blockReason} before letting a
     * job start — refusing it terminally (never re-considered by a later poll) rather than executing it.
     */
    @Nested
    @DisplayName("Claim-time budget recheck (#1368 fix wave)")
    class ClaimTimeBudgetRecheck {

        @Test
        void refusesAndCancelsWhenBudgetIsExhaustedAtClaimTime() {
            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.of(job));
            when(llmBudgetService.blockReason(99L)).thenReturn(LlmBudgetBlockReason.EXHAUSTED);
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            boolean claimed = executor.processJob(jobId);

            // Refused terminally: never executed, never claimed to RUNNING.
            assertThat(claimed).isFalse();
            verify(sandboxManager, never()).execute(any());
            verify(configRepository, never()).findByIdForUpdate(any());

            ArgumentCaptor<AgentJob> saved = ArgumentCaptor.forClass(AgentJob.class);
            verify(jobRepository).save(saved.capture());
            assertThat(saved.getValue().getStatus()).isEqualTo(AgentJobStatus.CANCELLED);
            assertThat(saved.getValue().getErrorMessage()).isEqualTo("Budget reached.");
            assertThat(saved.getValue().getCancellationReason()).isEqualTo(AgentJobCancellationReason.BUDGET_EXHAUSTED);
        }

        @Test
        void refusesWithADistinctMessageWhenUnpricedUsageIsBlockedByInstancePolicy() {
            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.of(job));
            when(llmBudgetService.blockReason(99L)).thenReturn(LlmBudgetBlockReason.UNPRICED_USAGE_BLOCKED);
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            boolean claimed = executor.processJob(jobId);

            assertThat(claimed).isFalse();
            verify(sandboxManager, never()).execute(any());

            ArgumentCaptor<AgentJob> saved = ArgumentCaptor.forClass(AgentJob.class);
            verify(jobRepository).save(saved.capture());
            assertThat(saved.getValue().getStatus()).isEqualTo(AgentJobStatus.CANCELLED);
            assertThat(saved.getValue().getErrorMessage()).isEqualTo(LlmUnpricedUsageBlockedException.MESSAGE);
            assertThat(saved.getValue().getCancellationReason()).isEqualTo(AgentJobCancellationReason.BUDGET_EXHAUSTED);
        }

        @Test
        void refusedPreStartJobNeverWritesAUsageLedgerEntry() {
            // Rejected-pre-start jobs must leave NO ledger trace at all — distinct from the
            // cancelled-after-start / malformed-usage cases, which DO record UNPRICED (see
            // UnpricedUsageLedgerFallback below).
            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.of(job));
            when(llmBudgetService.blockReason(99L)).thenReturn(LlmBudgetBlockReason.EXHAUSTED);
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            executor.processJob(jobId);

            verify(usageRecorder, never()).record(any(), any());
            verify(usageRecorder, never()).recordUnverifiable(any(), any());
        }

        @Test
        void proceedsToConcurrencyGateWhenBudgetIsNotBlocked() {
            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.of(job));
            when(llmBudgetService.blockReason(99L)).thenReturn(LlmBudgetBlockReason.NONE);
            when(configRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(config));
            when(jobRepository.countByConfigIdAndStatusIn(eq(10L), any())).thenReturn(0L);
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            setupFullExecution();

            boolean claimed = executor.processJob(jobId);

            assertThat(claimed).isTrue();
            ArgumentCaptor<AgentJob> captured = ArgumentCaptor.forClass(AgentJob.class);
            verify(jobRepository).save(captured.capture());
            assertThat(captured.getValue().getStatus()).isEqualTo(AgentJobStatus.RUNNING);
        }
    }

    @Nested
    class ProvenanceDigests {

        @Test
        void areStampedBeforeTheSandboxRuns_soAFailedRunKeepsThem() {
            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.of(job));
            when(configRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(config));
            when(jobRepository.countByConfigIdAndStatusIn(eq(10L), any())).thenReturn(0L);
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            setupFullExecution();

            executor.processJob(jobId);

            // The adapter's prompt digest, and an inputs digest over the merged file set — written first, so an
            // observation can always be tied to what produced it even when the sandbox then fails.
            ArgumentCaptor<String> inputsDigest = ArgumentCaptor.forClass(String.class);
            InOrder order = inOrder(jobRepository, sandboxManager);
            order.verify(jobRepository).updateProvenanceDigests(eq(jobId), eq("prompt-digest"), inputsDigest.capture());
            order.verify(sandboxManager).execute(any());
            assertThat(inputsDigest.getValue()).matches("[0-9a-f]{64}");
        }

        @Test
        void aWriteMatchingNoJobRow_failsTheRunRatherThanBurningTheLlmBudget() {
            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.of(job));
            when(configRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(config));
            when(jobRepository.countByConfigIdAndStatusIn(eq(10L), any())).thenReturn(0L);
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            // Only the prepare chain — the run must abort before the sandbox, so nothing past it is stubbed.
            JobTypeHandler handler = mock(JobTypeHandler.class);
            when(handlerRegistry.getHandler(AgentJobType.PULL_REQUEST_REVIEW)).thenReturn(handler);
            when(handler.prepareInputFiles(any())).thenReturn(Map.of("task.json", "{}".getBytes()));
            when(practiceAgent.buildSandboxSpec(any())).thenReturn(minimalSpec());
            when(jobRepository.updateProvenanceDigests(any(), any(), any())).thenReturn(0); // job row is gone

            executor.processJob(jobId);

            verify(sandboxManager, never()).execute(any());
        }
    }

    @Nested
    class FullExecution {

        @Test
        void shouldCompleteJobSuccessfully() {
            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.of(job));
            when(configRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(config));
            when(jobRepository.countByConfigIdAndStatusIn(eq(10L), any())).thenReturn(0L);
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            setupFullExecution();

            AgentJob freshJob = freshJob();
            when(jobRepository.findById(any(UUID.class))).thenReturn(Optional.of(freshJob));
            when(jobRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
            when(jobRepository.transitionStatus(any(), eq(AgentJobStatus.COMPLETED), any(), any(), any())).thenReturn(
                1
            );

            boolean claimed = executor.processJob(jobId);

            assertThat(claimed).isTrue();
            verify(sandboxManager).execute(any());
            verify(jobRepository).transitionStatus(
                any(),
                eq(AgentJobStatus.COMPLETED),
                any(),
                any(),
                eq(Set.of(AgentJobStatus.RUNNING))
            );
        }

        @Test
        void shouldMarkFailedOnNonZeroExitCode() {
            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.of(job));
            when(configRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(config));
            when(jobRepository.countByConfigIdAndStatusIn(eq(10L), any())).thenReturn(0L);
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SandboxResult failResult = new SandboxResult(1, Map.of(), "error output", false, Duration.ofMinutes(2));
            setupFullExecution(failResult);

            AgentJob freshJob = freshJob();
            when(jobRepository.findById(any(UUID.class))).thenReturn(Optional.of(freshJob));
            when(jobRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
            when(jobRepository.transitionStatus(any(), any(), any(), any(), any())).thenReturn(1);

            executor.processJob(jobId);

            verify(jobRepository).transitionStatus(
                any(),
                eq(AgentJobStatus.FAILED),
                any(),
                any(),
                eq(Set.of(AgentJobStatus.RUNNING))
            );
        }

        @Test
        void emitsEnvelopeMismatchOnExit42() {
            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.of(job));
            when(configRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(config));
            when(jobRepository.countByConfigIdAndStatusIn(eq(10L), any())).thenReturn(0L);
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SandboxResult envelopeMismatch = new SandboxResult(
                42,
                Map.of(),
                "envelope drift",
                false,
                Duration.ofSeconds(5)
            );
            setupFullExecution(envelopeMismatch);

            AgentJob freshJob = freshJob();
            when(jobRepository.findById(any(UUID.class))).thenReturn(Optional.of(freshJob));
            when(jobRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
            when(jobRepository.transitionStatus(any(), any(), any(), any(), any())).thenReturn(1);

            executor.processJob(jobId);

            assertThat(meterRegistry.counter("agent.pi.envelope.mismatch").count()).isEqualTo(1d);
            verify(jobRepository).transitionStatus(
                any(),
                eq(AgentJobStatus.FAILED),
                any(),
                any(),
                eq(Set.of(AgentJobStatus.RUNNING))
            );
        }

        @Test
        void shouldMarkTimedOutOnTimeout() {
            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.of(job));
            when(configRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(config));
            when(jobRepository.countByConfigIdAndStatusIn(eq(10L), any())).thenReturn(0L);
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SandboxResult timeoutResult = new SandboxResult(137, Map.of(), "timed out", true, Duration.ofMinutes(10));
            setupFullExecution(timeoutResult);

            AgentJob freshJob = freshJob();
            when(jobRepository.findById(any(UUID.class))).thenReturn(Optional.of(freshJob));
            when(jobRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
            when(jobRepository.transitionStatus(any(), any(), any(), any(), any())).thenReturn(1);

            executor.processJob(jobId);

            verify(jobRepository).transitionStatus(
                any(),
                eq(AgentJobStatus.TIMED_OUT),
                any(),
                any(),
                eq(Set.of(AgentJobStatus.RUNNING))
            );
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void shouldTransitionToCancelledOnCancellation() {
            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.of(job));
            when(configRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(config));
            when(jobRepository.countByConfigIdAndStatusIn(eq(10L), any())).thenReturn(0L);
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            setupFullExecutionWithException(new SandboxCancelledException("cancelled"));

            executor.processJob(jobId);

            verify(jobRepository).transitionStatus(
                any(),
                eq(AgentJobStatus.CANCELLED),
                any(),
                any(),
                eq(Set.of(AgentJobStatus.RUNNING))
            );
        }

        @Test
        void shouldMarkFailedOnGenericException() {
            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.of(job));
            when(configRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(config));
            when(jobRepository.countByConfigIdAndStatusIn(eq(10L), any())).thenReturn(0L);
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            setupFullExecutionWithException(new RuntimeException("Docker daemon unreachable"));

            executor.processJob(jobId);

            verify(jobRepository).transitionStatus(
                any(),
                eq(AgentJobStatus.FAILED),
                any(),
                any(),
                eq(Set.of(AgentJobStatus.RUNNING))
            );
        }
    }

    /**
     * #1368 fix wave: a job that started executing (past claim+RUNNING) but ends with no
     * parseable usage must still leave a ledger trace — an UNPRICED entry, so the month turns
     * UNVERIFIABLE instead of looking falsely fully accounted for. Never for jobs refused before
     * RUNNING — see {@code ClaimTimeBudgetRecheck#refusedPreStartJobNeverWritesAUsageLedgerEntry}.
     */
    @Nested
    @DisplayName("Unpriced usage ledger fallback (#1368 fix wave)")
    class UnpricedUsageLedgerFallback {

        @Test
        void cancelledAfterStart_recordsAnUnpricedLedgerEntry() {
            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.of(job));
            when(configRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(config));
            when(jobRepository.countByConfigIdAndStatusIn(eq(10L), any())).thenReturn(0L);
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            // The transition wins (job really was RUNNING-and-ours) — the ledger write is gated on this.
            when(jobRepository.transitionStatus(any(), eq(AgentJobStatus.CANCELLED), any(), any(), any())).thenReturn(
                1
            );
            when(jobRepository.findByIdWithWorkspace(jobId)).thenReturn(Optional.of(job));

            setupFullExecutionWithException(new SandboxCancelledException("cancelled"));

            executor.processJob(jobId);

            ArgumentCaptor<LlmUsageRecorder.LlmUsageSample> sample = ArgumentCaptor.forClass(
                LlmUsageRecorder.LlmUsageSample.class
            );
            verify(usageRecorder).recordUnverifiable(eq(99L), sample.capture());
            // job (the claimed entity) has its own id from prePersist() — distinct from the poll's
            // jobId in this fixture; the ledger sourceId must be the entity's real id.
            assertThat(sample.getValue().sourceId()).isEqualTo(job.getId());
            assertThat(sample.getValue().model()).isEqualTo("claude-sonnet-4");
            assertThat(sample.getValue().inputTokens()).isZero();
            assertThat(sample.getValue().totalCalls()).isZero();
            verify(usageRecorder, never()).record(any(), any());
        }

        @Test
        @DisplayName(
            "#1368 fix wave: even when the CAS transition loses the race (a concurrent user-cancel or " +
                "worker-drain already moved the job to CANCELLED), the ledger write is still attempted — " +
                "reaching this handler at all proves the job started executing, and a duplicate write is " +
                "safely swallowed by the ledger's unique source_id constraint"
        )
        void cancelledAfterStart_fenceLost_stillRecordsLedgerEntry() {
            // transitionStatus returns 0 (job no longer RUNNING-and-ours — e.g. a concurrent user-cancel
            // or worker-drain path already won the CAS). Previously this silently skipped the ledger
            // write; now the write is attempted regardless, since only a job that reached sandbox
            // execution ever throws SandboxCancelledException in the first place.
            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.of(job));
            when(configRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(config));
            when(jobRepository.countByConfigIdAndStatusIn(eq(10L), any())).thenReturn(0L);
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(jobRepository.transitionStatus(any(), eq(AgentJobStatus.CANCELLED), any(), any(), any())).thenReturn(
                0
            );
            when(jobRepository.findByIdWithWorkspace(jobId)).thenReturn(Optional.of(job));

            setupFullExecutionWithException(new SandboxCancelledException("cancelled"));

            executor.processJob(jobId);

            ArgumentCaptor<LlmUsageRecorder.LlmUsageSample> sample = ArgumentCaptor.forClass(
                LlmUsageRecorder.LlmUsageSample.class
            );
            verify(usageRecorder).recordUnverifiable(eq(99L), sample.capture());
            assertThat(sample.getValue().sourceId()).isEqualTo(job.getId());
            verify(usageRecorder, never()).record(any(), any());
        }

        @Test
        @DisplayName(
            "#1368 fix wave: worker-drain (cancelInFlight) records an UNPRICED ledger entry for a " +
                "locally-running job, regardless of whether its own CAS transition wins"
        )
        void workerDrain_cancelInFlight_recordsAnUnpricedLedgerEntry() throws Exception {
            java.lang.reflect.Field localRunningJobsField = AgentJobExecutor.class.getDeclaredField("localRunningJobs");
            localRunningJobsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<UUID> localRunningJobs = (Set<UUID>) localRunningJobsField.get(executor);
            localRunningJobs.add(jobId);

            when(jobRepository.findByIdWithWorkspace(jobId)).thenReturn(Optional.of(job));

            executor.cancelInFlight(AgentJobCancellationReason.DRAIN_GRACEFUL);

            verify(sandboxManager).cancel(jobId);
            ArgumentCaptor<LlmUsageRecorder.LlmUsageSample> sample = ArgumentCaptor.forClass(
                LlmUsageRecorder.LlmUsageSample.class
            );
            verify(usageRecorder).recordUnverifiable(eq(99L), sample.capture());
            assertThat(sample.getValue().sourceId()).isEqualTo(job.getId());
            verify(usageRecorder, never()).record(any(), any());
        }

        @Test
        void missingOrMalformedUsageJson_recordsAnUnpricedLedgerEntryOnNormalCompletion() {
            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.of(job));
            when(configRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(config));
            when(jobRepository.countByConfigIdAndStatusIn(eq(10L), any())).thenReturn(0L);
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // setupFullExecution's AgentResult carries no usage — the Pi runner's usage.json was
            // missing/malformed. The sandbox itself still exits 0 (COMPLETED), unlike a hard failure.
            setupFullExecution();

            AgentJob freshJob = freshJob();
            freshJob.setConfigSnapshot(snapshot.toJson(objectMapper));
            when(jobRepository.findById(any(UUID.class))).thenReturn(Optional.of(freshJob));
            when(jobRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
            when(jobRepository.transitionStatus(any(), eq(AgentJobStatus.COMPLETED), any(), any(), any())).thenReturn(
                1
            );

            executor.processJob(jobId);

            ArgumentCaptor<LlmUsageRecorder.LlmUsageSample> sample = ArgumentCaptor.forClass(
                LlmUsageRecorder.LlmUsageSample.class
            );
            verify(usageRecorder).recordUnverifiable(eq(99L), sample.capture());
            assertThat(sample.getValue().sourceId()).isEqualTo(freshJob.getId());
            assertThat(sample.getValue().model()).isEqualTo("claude-sonnet-4");
            assertThat(sample.getValue().totalCalls()).isZero();
            verify(usageRecorder, never()).record(any(), any());
        }
    }

    @Nested
    class LlmProxyRouting {

        @Test
        @DisplayName(
            "the practice request carries the snapshot's resolved behaviour + the job's own token — ONE credential path"
        )
        void requestCarriesSnapshotBehaviourAndJobToken() {
            // #1368 slice 5: no worker-side BYO-LLM override exists anymore — every host (app-server AND
            // worker) reaches the LLM proxy the same way, via the job's own token.
            executor = new AgentJobExecutor(
                AGENT_PROPS,
                jobRepository,
                configRepository,
                handlerRegistry,
                practiceAgent,
                sandboxManager,
                sandboxExecutor,
                transactionTemplate,
                objectMapper,
                meterRegistry,
                usageRecorder,
                llmBudgetService,
                Optional.empty(),
                Optional.of(workerProps("test-worker"))
            );

            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.of(job));
            when(configRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(config));
            when(jobRepository.countByConfigIdAndStatusIn(eq(10L), any())).thenReturn(0L);
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            setupFullExecution();

            AgentJob freshJob = freshJob();
            when(jobRepository.findById(any(UUID.class))).thenReturn(Optional.of(freshJob));
            when(jobRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
            // Worker identity is set ("test-worker"), so the terminal write is fenced to the owner.
            when(jobRepository.transitionStatusOwnedBy(any(), any(), any(), any(), any(), any())).thenReturn(1);

            executor.processJob(jobId);

            ArgumentCaptor<PracticeAgentRequest> captor = ArgumentCaptor.forClass(PracticeAgentRequest.class);
            verify(practiceAgent).buildSandboxSpec(captor.capture());
            PracticeAgentRequest request = captor.getValue();

            assertThat(request.apiProtocol()).isEqualTo("anthropic-messages");
            assertThat(request.upstreamModelId()).isEqualTo("claude-sonnet-4");
            assertThat(request.jobToken()).isEqualTo("test-token");
        }
    }

    @Nested
    @DisplayName("Poll loop capacity math (#1368 NATS→Postgres cutover)")
    class PollLoopCapacity {

        @Test
        @DisplayName("no WorkerCapacityState (worker role config absent) falls back to claimBatchSize")
        void noCapacityStateFallsBackToClaimBatchSize() {
            assertThat(executor.computeCapacity()).isEqualTo(AGENT_PROPS.claimBatchSize());
        }

        @Test
        @DisplayName("free capacity is reviewMax minus jobs already running locally")
        void freeCapacityIsReviewMaxMinusLocalRunning() throws Exception {
            de.tum.cit.aet.hephaestus.agent.runtime.worker.WorkerCapacityState capacityState =
                new de.tum.cit.aet.hephaestus.agent.runtime.worker.WorkerCapacityState(workerProps("w"));
            capacityState.claimReview();
            capacityState.claimReview(); // 2 in flight; reviewMax is 2 (see workerProps)

            executor = new AgentJobExecutor(
                AGENT_PROPS,
                jobRepository,
                configRepository,
                handlerRegistry,
                practiceAgent,
                sandboxManager,
                sandboxExecutor,
                transactionTemplate,
                objectMapper,
                meterRegistry,
                usageRecorder,
                llmBudgetService,
                Optional.of(capacityState),
                Optional.empty()
            );
            // Mirror the two claimReview() calls above by populating localRunningJobs directly —
            // computeCapacity reads localRunningJobs.size(), not the capacity state's own counter.
            java.lang.reflect.Field localRunningJobsField = AgentJobExecutor.class.getDeclaredField("localRunningJobs");
            localRunningJobsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<UUID> localRunningJobs = (Set<UUID>) localRunningJobsField.get(executor);
            localRunningJobs.add(UUID.randomUUID());
            localRunningJobs.add(UUID.randomUUID());

            // reviewMax (2) - localRunning (2) = 0 free capacity.
            assertThat(executor.computeCapacity()).isZero();
        }

        @Test
        @DisplayName("capacity is bounded by claimBatchSize even when the pool has more room")
        void capacityIsBoundedByClaimBatchSize() {
            AgentProperties smallBatch = new AgentProperties(true, Duration.ofSeconds(1), 2, 5, Duration.ofSeconds(25));
            de.tum.cit.aet.hephaestus.agent.runtime.worker.WorkerCapacityState capacityState =
                new de.tum.cit.aet.hephaestus.agent.runtime.worker.WorkerCapacityState(
                    new WorkerProperties(
                        "w",
                        new WorkerProperties.Capacity("10", "1"), // reviewMax=10, far above claimBatchSize=2
                        new WorkerProperties.Drain(Duration.ofMinutes(5)),
                        new WorkerProperties.Heartbeat(Duration.ofSeconds(20)),
                        new WorkerProperties.Control(URI.create("ws://example"), "tok", Duration.ofSeconds(10))
                    )
                );

            executor = new AgentJobExecutor(
                smallBatch,
                jobRepository,
                configRepository,
                handlerRegistry,
                practiceAgent,
                sandboxManager,
                sandboxExecutor,
                transactionTemplate,
                objectMapper,
                meterRegistry,
                usageRecorder,
                llmBudgetService,
                Optional.of(capacityState),
                Optional.empty()
            );

            assertThat(executor.computeCapacity()).isEqualTo(2);
        }

        @Test
        @DisplayName("empty candidate list means no claim is even attempted")
        void emptyPollAttemptsNoClaims() {
            when(jobRepository.findQueuedIdsOldestFirst(anyInt())).thenReturn(List.of());

            List<UUID> candidates = jobRepository.findQueuedIdsOldestFirst(5);

            assertThat(candidates).isEmpty();
            verify(jobRepository, never()).findByIdQueuedForUpdateSkipLocked(any());
        }
    }

    // Helpers

    private JobTypeHandler setupFullExecution() {
        SandboxResult successResult = new SandboxResult(0, Map.of(), "success", false, Duration.ofMinutes(2));
        return setupFullExecution(successResult);
    }

    private JobTypeHandler setupFullExecution(SandboxResult sandboxResult) {
        // Every execution stamps its provenance digests before the sandbox starts, and fails loud if the write
        // matches no row — so the standard path must report the row it updated.
        lenient().when(jobRepository.updateProvenanceDigests(any(), any(), any())).thenReturn(1);
        JobTypeHandler handler = mock(JobTypeHandler.class);
        when(handlerRegistry.getHandler(AgentJobType.PULL_REQUEST_REVIEW)).thenReturn(handler);
        when(handler.prepareInputFiles(any())).thenReturn(Map.of("code.py", "print('hi')".getBytes()));

        PracticeSandboxSpec agentSpec = new PracticeSandboxSpec(
            "ghcr.io/agent:latest",
            List.of("/bin/agent"),
            Map.of("KEY", "value"),
            Map.of("config.json", "{}".getBytes()),
            "/output",
            SecurityProfile.DEFAULT,
            new NetworkPolicy(false, null, "test-token"),
            null,
            "prompt-digest"
        );
        when(practiceAgent.buildSandboxSpec(any())).thenReturn(agentSpec);
        when(practiceAgent.parseResult(any())).thenReturn(new AgentResult(true, Map.of("review", "LGTM")));

        when(sandboxManager.execute(any())).thenReturn(sandboxResult);
        return handler;
    }

    private static PracticeSandboxSpec minimalSpec() {
        return new PracticeSandboxSpec(
            "ghcr.io/agent:latest",
            List.of("/bin/agent"),
            Map.of(),
            Map.of(),
            "/output",
            null,
            null,
            null,
            "prompt-digest"
        );
    }

    private static WorkerProperties workerProps(String workerId) {
        return new WorkerProperties(
            workerId,
            new WorkerProperties.Capacity("2", "1"),
            new WorkerProperties.Drain(Duration.ofMinutes(5)),
            new WorkerProperties.Heartbeat(Duration.ofSeconds(20)),
            new WorkerProperties.Control(URI.create("ws://example"), "tok", Duration.ofSeconds(10))
        );
    }

    private void setupFullExecutionWithException(Exception exception) {
        lenient().when(jobRepository.updateProvenanceDigests(any(), any(), any())).thenReturn(1);
        JobTypeHandler handler = mock(JobTypeHandler.class);
        when(handlerRegistry.getHandler(AgentJobType.PULL_REQUEST_REVIEW)).thenReturn(handler);
        when(handler.prepareInputFiles(any())).thenReturn(Map.of());

        PracticeSandboxSpec agentSpec = new PracticeSandboxSpec(
            "ghcr.io/agent:latest",
            List.of("/bin/agent"),
            Map.of(),
            Map.of(),
            "/output",
            null,
            null,
            null,
            null
        );
        when(practiceAgent.buildSandboxSpec(any())).thenReturn(agentSpec);

        when(sandboxManager.execute(any())).thenThrow(exception);
    }
}
