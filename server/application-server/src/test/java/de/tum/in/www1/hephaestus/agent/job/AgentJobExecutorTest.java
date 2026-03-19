package de.tum.in.www1.hephaestus.agent.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.AgentJobType;
import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.agent.adapter.AgentAdapterRegistry;
import de.tum.in.www1.hephaestus.agent.adapter.AgentResult;
import de.tum.in.www1.hephaestus.agent.adapter.spi.AgentAdapter;
import de.tum.in.www1.hephaestus.agent.adapter.spi.AgentSandboxSpec;
import de.tum.in.www1.hephaestus.agent.config.AgentConfig;
import de.tum.in.www1.hephaestus.agent.config.AgentConfigRepository;
import de.tum.in.www1.hephaestus.agent.config.ConfigSnapshot;
import de.tum.in.www1.hephaestus.agent.handler.JobTypeHandlerRegistry;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.NetworkPolicy;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxCancelledException;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxManager;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxResult;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SecurityProfile;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.nats.client.Connection;
import io.nats.client.Message;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("AgentJobExecutor")
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
    private AgentAdapterRegistry adapterRegistry;

    @Mock
    private SandboxManager sandboxManager;

    @Mock
    private AsyncTaskExecutor sandboxExecutor;

    @Mock
    private TransactionTemplate transactionTemplate;

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
            adapterRegistry,
            sandboxManager,
            sandboxExecutor,
            transactionTemplate,
            objectMapper,
            meterRegistry
        );

        jobId = UUID.randomUUID();

        config = new AgentConfig();
        config.setId(10L);
        config.setMaxConcurrentJobs(3);

        snapshot = new ConfigSnapshot(
            1,
            10L,
            "test-config",
            AgentType.CLAUDE_CODE,
            LlmProvider.ANTHROPIC,
            CredentialMode.PROXY,
            null,
            600,
            false
        );

        // Build a real AgentJob instead of a mock
        job = new AgentJob();
        job.prePersist(); // generate ID + token
        // Override the random ID with our test ID via reflection-free approach:
        // We use the mock for findByIdQueuedForUpdateSkipLocked which returns our job
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
    }

    private Message createMessage(UUID id) {
        Message msg = mock(Message.class);
        when(msg.getData()).thenReturn(id.toString().getBytes(StandardCharsets.UTF_8));
        return msg;
    }

    @Nested
    @DisplayName("Claim phase")
    class ClaimPhase {

        @Test
        @DisplayName("should ack and return when SKIP LOCKED returns empty")
        void shouldAckWhenSkipLockedReturnsEmpty() {
            Message msg = createMessage(jobId);
            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.empty());

            executor.executeJob(msg);

            verify(msg).ack();
            verify(sandboxManager, never()).execute(any());
        }

        @Test
        @DisplayName("should NAK with delay when concurrency limit reached")
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
        @DisplayName("should transition to RUNNING on successful claim")
        void shouldTransitionToRunningOnSuccessfulClaim() {
            Message msg = createMessage(jobId);
            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.of(job));
            when(configRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(config));
            when(jobRepository.countByConfigIdAndStatusIn(eq(10L), any())).thenReturn(0L);
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            setupFullExecution();

            executor.executeJob(msg);

            // The real job object should have RUNNING status set during claim
            verify(jobRepository).save(any(AgentJob.class));
        }
    }

    @Nested
    @DisplayName("Full execution")
    class FullExecution {

        @Test
        @DisplayName("should complete job successfully and verify COMPLETED transition")
        void shouldCompleteJobSuccessfully() {
            Message msg = createMessage(jobId);
            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.of(job));
            when(configRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(config));
            when(jobRepository.countByConfigIdAndStatusIn(eq(10L), any())).thenReturn(0L);
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            setupFullExecution();

            // For the complete phase
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
        @DisplayName("should mark FAILED on non-zero exit code")
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
        @DisplayName("should mark TIMED_OUT on timeout")
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
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should transition to CANCELLED on SandboxCancelledException")
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
        @DisplayName("should mark FAILED on generic exception (only from RUNNING)")
        void shouldMarkFailedOnGenericException() {
            Message msg = createMessage(jobId);
            when(jobRepository.findByIdQueuedForUpdateSkipLocked(jobId)).thenReturn(Optional.of(job));
            when(configRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(config));
            when(jobRepository.countByConfigIdAndStatusIn(eq(10L), any())).thenReturn(0L);
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            setupFullExecutionWithException(new RuntimeException("Docker daemon unreachable"));

            executor.executeJob(msg);

            // Bug fix: only transition from RUNNING, not QUEUED
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
    @DisplayName("Message handling")
    class MessageHandling {

        @Test
        @DisplayName("should ack invalid UUID payload to discard")
        void shouldAckInvalidPayload() {
            Message msg = mock(Message.class);
            when(msg.getData()).thenReturn("not-a-uuid".getBytes(StandardCharsets.UTF_8));

            executor.executeJob(msg);

            verify(msg).ack();
            verify(sandboxManager, never()).execute(any());
        }
    }

    // ── Helpers ──

    private void setupFullExecution() {
        SandboxResult successResult = new SandboxResult(0, Map.of(), "success", false, Duration.ofMinutes(2));
        setupFullExecution(successResult);
    }

    private void setupFullExecution(SandboxResult sandboxResult) {
        JobTypeHandler handler = mock(JobTypeHandler.class);
        when(handlerRegistry.getHandler(AgentJobType.PULL_REQUEST_REVIEW)).thenReturn(handler);
        when(handler.prepareInputFiles(any())).thenReturn(Map.of("code.py", "print('hi')".getBytes()));
        when(handler.buildPrompt(any())).thenReturn("Review this code");

        AgentAdapter adapter = mock(AgentAdapter.class);
        when(adapterRegistry.getAdapter(AgentType.CLAUDE_CODE)).thenReturn(adapter);

        AgentSandboxSpec agentSpec = new AgentSandboxSpec(
            "ghcr.io/agent:latest",
            List.of("/bin/agent"),
            Map.of("KEY", "value"),
            Map.of("config.json", "{}".getBytes()),
            "/output",
            SecurityProfile.DEFAULT,
            new NetworkPolicy(false, null, "test-token", "anthropic")
        );
        when(adapter.buildSandboxSpec(any())).thenReturn(agentSpec);
        when(adapter.parseResult(any())).thenReturn(new AgentResult(true, Map.of("review", "LGTM")));

        when(sandboxManager.execute(any())).thenReturn(sandboxResult);
    }

    private void setupFullExecutionWithException(Exception exception) {
        JobTypeHandler handler = mock(JobTypeHandler.class);
        when(handlerRegistry.getHandler(AgentJobType.PULL_REQUEST_REVIEW)).thenReturn(handler);
        when(handler.prepareInputFiles(any())).thenReturn(Map.of());
        when(handler.buildPrompt(any())).thenReturn("Review this code");

        AgentAdapter adapter = mock(AgentAdapter.class);
        when(adapterRegistry.getAdapter(AgentType.CLAUDE_CODE)).thenReturn(adapter);

        AgentSandboxSpec agentSpec = new AgentSandboxSpec(
            "ghcr.io/agent:latest",
            List.of("/bin/agent"),
            Map.of(),
            Map.of(),
            "/output",
            null,
            null
        );
        when(adapter.buildSandboxSpec(any())).thenReturn(agentSpec);

        when(sandboxManager.execute(any())).thenThrow(exception);
    }
}
