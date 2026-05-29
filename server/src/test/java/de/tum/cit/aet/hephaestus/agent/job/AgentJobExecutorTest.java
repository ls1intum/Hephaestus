package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.CredentialMode;
import de.tum.cit.aet.hephaestus.agent.LlmProvider;
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
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.nats.client.Connection;
import io.nats.client.Message;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class AgentJobExecutorTest extends BaseUnitTest {

    @Mock
    private Connection natsConnection;

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

    private static final AgentNatsProperties NATS_PROPS = new AgentNatsProperties(
        true,
        "nats://localhost:4222",
        "AGENT",
        "hephaestus-agent-executor",
        Duration.ofMinutes(70),
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
            natsConnection,
            NATS_PROPS,
            jobRepository,
            configRepository,
            handlerRegistry,
            practiceAgent,
            sandboxManager,
            sandboxExecutor,
            transactionTemplate,
            objectMapper,
            meterRegistry,
            java.util.Optional.empty(),
            java.util.Optional.empty()
        );

        jobId = UUID.randomUUID();

        config = new AgentConfig();
        config.setId(10L);
        config.setMaxConcurrentJobs(3);

        snapshot = new ConfigSnapshot(
            1,
            10L,
            "test-config",
            LlmProvider.ANTHROPIC,
            CredentialMode.PROXY,
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
    }

    private Message createMessage(UUID id) {
        Message msg = mock(Message.class);
        when(msg.getData()).thenReturn(id.toString().getBytes(StandardCharsets.UTF_8));
        return msg;
    }

    @Nested
    class ClaimPhase {

        @Test
        void shouldAckWhenSkipLockedReturnsEmpty() {
            Message msg = createMessage(jobId);
            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.empty());

            executor.executeJob(msg);

            verify(msg).ack();
            verify(sandboxManager, never()).execute(any());
        }

        @Test
        void shouldNakWithDelayWhenConcurrencyLimitReached() {
            Message msg = createMessage(jobId);
            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.of(job));
            when(configRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(config));
            when(jobRepository.countByConfigIdAndStatusIn(eq(10L), any())).thenReturn(3L); // equals max

            executor.executeJob(msg);

            verify(msg, never()).ack();
            verify(msg).nakWithDelay(Duration.ofSeconds(30));
            verify(sandboxManager, never()).execute(any());
        }

        @Test
        void shouldTransitionToRunningOnSuccessfulClaim() {
            Message msg = createMessage(jobId);
            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.of(job));
            when(configRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(config));
            when(jobRepository.countByConfigIdAndStatusIn(eq(10L), any())).thenReturn(0L);
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            setupFullExecution();

            executor.executeJob(msg);

            verify(jobRepository).save(any(AgentJob.class));
        }
    }

    @Nested
    class FullExecution {

        @Test
        void shouldCompleteJobSuccessfully() {
            Message msg = createMessage(jobId);
            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.of(job));
            when(configRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(config));
            when(jobRepository.countByConfigIdAndStatusIn(eq(10L), any())).thenReturn(0L);
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            setupFullExecution();

            AgentJob freshJob = new AgentJob();
            freshJob.prePersist();
            when(jobRepository.findById(any(UUID.class))).thenReturn(Optional.of(freshJob));
            when(jobRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
            when(jobRepository.transitionStatus(any(), eq(AgentJobStatus.COMPLETED), any(), any(), any())).thenReturn(
                1
            );

            executor.executeJob(msg);

            verify(msg).ack();
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
            Message msg = createMessage(jobId);
            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.of(job));
            when(configRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(config));
            when(jobRepository.countByConfigIdAndStatusIn(eq(10L), any())).thenReturn(0L);
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SandboxResult failResult = new SandboxResult(1, Map.of(), "error output", false, Duration.ofMinutes(2));
            setupFullExecution(failResult);

            AgentJob freshJob = new AgentJob();
            freshJob.prePersist();
            when(jobRepository.findById(any(UUID.class))).thenReturn(Optional.of(freshJob));
            when(jobRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
            when(jobRepository.transitionStatus(any(), any(), any(), any(), any())).thenReturn(1);

            executor.executeJob(msg);

            verify(jobRepository).transitionStatus(
                any(),
                eq(AgentJobStatus.FAILED),
                any(),
                any(),
                eq(Set.of(AgentJobStatus.RUNNING))
            );
            verify(msg).ack();
        }

        @Test
        void emitsEnvelopeMismatchOnExit42() {
            Message msg = createMessage(jobId);
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

            AgentJob freshJob = new AgentJob();
            freshJob.prePersist();
            when(jobRepository.findById(any(UUID.class))).thenReturn(Optional.of(freshJob));
            when(jobRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
            when(jobRepository.transitionStatus(any(), any(), any(), any(), any())).thenReturn(1);

            executor.executeJob(msg);

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
            Message msg = createMessage(jobId);
            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.of(job));
            when(configRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(config));
            when(jobRepository.countByConfigIdAndStatusIn(eq(10L), any())).thenReturn(0L);
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SandboxResult timeoutResult = new SandboxResult(137, Map.of(), "timed out", true, Duration.ofMinutes(10));
            setupFullExecution(timeoutResult);

            AgentJob freshJob = new AgentJob();
            freshJob.prePersist();
            when(jobRepository.findById(any(UUID.class))).thenReturn(Optional.of(freshJob));
            when(jobRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
            when(jobRepository.transitionStatus(any(), any(), any(), any(), any())).thenReturn(1);

            executor.executeJob(msg);

            verify(jobRepository).transitionStatus(
                any(),
                eq(AgentJobStatus.TIMED_OUT),
                any(),
                any(),
                eq(Set.of(AgentJobStatus.RUNNING))
            );
            verify(msg).ack();
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void shouldTransitionToCancelledOnCancellation() {
            Message msg = createMessage(jobId);
            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.of(job));
            when(configRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(config));
            when(jobRepository.countByConfigIdAndStatusIn(eq(10L), any())).thenReturn(0L);
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            setupFullExecutionWithException(new SandboxCancelledException("cancelled"));

            executor.executeJob(msg);

            verify(jobRepository).transitionStatus(
                any(),
                eq(AgentJobStatus.CANCELLED),
                any(),
                any(),
                eq(Set.of(AgentJobStatus.RUNNING))
            );
            verify(msg).ack();
        }

        @Test
        void shouldMarkFailedOnGenericException() {
            Message msg = createMessage(jobId);
            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.of(job));
            when(configRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(config));
            when(jobRepository.countByConfigIdAndStatusIn(eq(10L), any())).thenReturn(0L);
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            setupFullExecutionWithException(new RuntimeException("Docker daemon unreachable"));

            executor.executeJob(msg);

            verify(jobRepository).transitionStatus(
                any(),
                eq(AgentJobStatus.FAILED),
                any(),
                any(),
                eq(Set.of(AgentJobStatus.RUNNING))
            );
            verify(msg).ack();
        }
    }

    @Nested
    class MessageHandling {

        @Test
        void shouldAckInvalidPayload() {
            Message msg = mock(Message.class);
            when(msg.getData()).thenReturn("not-a-uuid".getBytes(StandardCharsets.UTF_8));

            executor.executeJob(msg);

            verify(msg).ack();
            verify(sandboxManager, never()).execute(any());
        }
    }

    @Nested
    class WorkerLlmOverride {

        @Test
        void overridesPracticeRequestWhenWorkerLlmConfigured() {
            // Configured worker LLM: PROXY snapshot must be overridden to API_KEY with the
            // worker's apiKey and baseUrl so agent-pi reaches the operator's gateway directly.
            executor = new AgentJobExecutor(
                natsConnection,
                NATS_PROPS,
                jobRepository,
                configRepository,
                handlerRegistry,
                practiceAgent,
                sandboxManager,
                sandboxExecutor,
                transactionTemplate,
                objectMapper,
                meterRegistry,
                Optional.empty(),
                Optional.of(workerPropsWithLlm("https://gpu.ase.cit.tum.de/v1", "operator-key"))
            );

            Message msg = createMessage(jobId);
            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.of(job));
            when(configRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(config));
            when(jobRepository.countByConfigIdAndStatusIn(eq(10L), any())).thenReturn(0L);
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            setupFullExecution();

            AgentJob freshJob = new AgentJob();
            freshJob.prePersist();
            when(jobRepository.findById(any(UUID.class))).thenReturn(Optional.of(freshJob));
            when(jobRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
            when(jobRepository.transitionStatus(any(), any(), any(), any(), any())).thenReturn(1);

            executor.executeJob(msg);

            ArgumentCaptor<PracticeAgentRequest> captor = ArgumentCaptor.forClass(PracticeAgentRequest.class);
            verify(practiceAgent).buildSandboxSpec(captor.capture());
            PracticeAgentRequest request = captor.getValue();

            assertThat(request.credentialMode()).isEqualTo(CredentialMode.API_KEY);
            assertThat(request.credential()).isEqualTo("operator-key");
            assertThat(request.baseUrl()).isEqualTo("https://gpu.ase.cit.tum.de/v1");
            // Snapshot's LLM provider + model still flow through unchanged.
            assertThat(request.llmProvider()).isEqualTo(LlmProvider.ANTHROPIC);
        }

        @Test
        void leavesSnapshotCredentialModeWhenWorkerLlmUnset() {
            executor = new AgentJobExecutor(
                natsConnection,
                NATS_PROPS,
                jobRepository,
                configRepository,
                handlerRegistry,
                practiceAgent,
                sandboxManager,
                sandboxExecutor,
                transactionTemplate,
                objectMapper,
                meterRegistry,
                Optional.empty(),
                Optional.of(workerPropsWithLlm(null, null))
            );

            Message msg = createMessage(jobId);
            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.of(job));
            when(configRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(config));
            when(jobRepository.countByConfigIdAndStatusIn(eq(10L), any())).thenReturn(0L);
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            // Snapshot is PROXY mode — jobToken is required, the existing AgentJob fixture has one.

            setupFullExecution();

            AgentJob freshJob = new AgentJob();
            freshJob.prePersist();
            when(jobRepository.findById(any(UUID.class))).thenReturn(Optional.of(freshJob));
            when(jobRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
            when(jobRepository.transitionStatus(any(), any(), any(), any(), any())).thenReturn(1);

            executor.executeJob(msg);

            ArgumentCaptor<PracticeAgentRequest> captor = ArgumentCaptor.forClass(PracticeAgentRequest.class);
            verify(practiceAgent).buildSandboxSpec(captor.capture());
            PracticeAgentRequest request = captor.getValue();

            assertThat(request.credentialMode()).isEqualTo(CredentialMode.PROXY);
            assertThat(request.baseUrl()).isNull();
        }
    }

    // Helpers

    private void setupFullExecution() {
        SandboxResult successResult = new SandboxResult(0, Map.of(), "success", false, Duration.ofMinutes(2));
        setupFullExecution(successResult);
    }

    private void setupFullExecution(SandboxResult sandboxResult) {
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
            new NetworkPolicy(false, null, "test-token", "anthropic"),
            null
        );
        when(practiceAgent.buildSandboxSpec(any())).thenReturn(agentSpec);
        when(practiceAgent.parseResult(any())).thenReturn(new AgentResult(true, Map.of("review", "LGTM")));

        when(sandboxManager.execute(any())).thenReturn(sandboxResult);
    }

    private static WorkerProperties workerPropsWithLlm(String baseUrl, String apiKey) {
        return new WorkerProperties(
            "test-worker",
            new WorkerProperties.Capacity("2", "1"),
            new WorkerProperties.Drain(Duration.ofMinutes(5)),
            new WorkerProperties.Heartbeat(Duration.ofSeconds(20)),
            new WorkerProperties.Control(URI.create("ws://example"), "tok", Duration.ofSeconds(10)),
            new WorkerProperties.Llm(baseUrl, apiKey)
        );
    }

    private void setupFullExecutionWithException(Exception exception) {
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
            null
        );
        when(practiceAgent.buildSandboxSpec(any())).thenReturn(agentSpec);

        when(sandboxManager.execute(any())).thenThrow(exception);
    }
}
