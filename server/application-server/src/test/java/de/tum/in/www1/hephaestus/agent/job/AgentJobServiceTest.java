package de.tum.in.www1.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.agent.AgentJobType;
import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.agent.config.AgentConfig;
import de.tum.in.www1.hephaestus.agent.config.AgentConfigRepository;
import de.tum.in.www1.hephaestus.agent.handler.JobTypeHandlerRegistry;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobSubmission;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobSubmissionRequest;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxManager;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("AgentJobService")
class AgentJobServiceTest extends BaseUnitTest {

    @Mock
    private AgentJobRepository agentJobRepository;

    @Mock
    private AgentConfigRepository agentConfigRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private JobTypeHandlerRegistry handlerRegistry;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private SandboxManager sandboxManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AgentJobService service;

    private Workspace workspace;
    private AgentConfig enabledConfig;

    @BeforeEach
    void setUp() {
        service = new AgentJobService(
            agentJobRepository,
            agentConfigRepository,
            workspaceRepository,
            handlerRegistry,
            objectMapper,
            eventPublisher,
            transactionTemplate,
            sandboxManager
        );

        workspace = new Workspace();
        workspace.setId(1L);
        workspace.setWorkspaceSlug("test-ws");

        enabledConfig = new AgentConfig();
        enabledConfig.setId(10L);
        enabledConfig.setWorkspace(workspace);
        enabledConfig.setName("test-config");
        enabledConfig.setEnabled(true);
        enabledConfig.setAgentType(AgentType.CLAUDE_CODE);
        enabledConfig.setLlmProvider(LlmProvider.ANTHROPIC);
        enabledConfig.setCredentialMode(CredentialMode.PROXY);
        enabledConfig.setTimeoutSeconds(600);
    }

    private JobSubmission createSubmission() {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("pr_number", 42);
        return new JobSubmission(metadata, "pr_review:owner/repo:42:abc123");
    }

    @Nested
    @DisplayName("submit()")
    class Submit {

        @Test
        @DisplayName("should return empty when no enabled config exists")
        void shouldReturnEmptyWhenNoEnabledConfig() {
            AgentConfig disabledConfig = new AgentConfig();
            disabledConfig.setEnabled(false);
            when(agentConfigRepository.findByWorkspaceId(1L)).thenReturn(List.of(disabledConfig));

            Optional<AgentJob> result = service.submit(
                1L,
                AgentJobType.PULL_REQUEST_REVIEW,
                mock(JobSubmissionRequest.class)
            );

            assertThat(result).isEmpty();
            verify(agentJobRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("should return empty when no configs exist")
        void shouldReturnEmptyWhenNoConfigs() {
            when(agentConfigRepository.findByWorkspaceId(1L)).thenReturn(List.of());

            Optional<AgentJob> result = service.submit(
                1L,
                AgentJobType.PULL_REQUEST_REVIEW,
                mock(JobSubmissionRequest.class)
            );

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return existing job on idempotency match")
        void shouldReturnExistingJobOnIdempotencyMatch() {
            when(agentConfigRepository.findByWorkspaceId(1L)).thenReturn(List.of(enabledConfig));

            JobTypeHandler handler = mock(JobTypeHandler.class);
            when(handlerRegistry.getHandler(AgentJobType.PULL_REQUEST_REVIEW)).thenReturn(handler);
            when(handler.createSubmission(any())).thenReturn(createSubmission());

            AgentJob existingJob = new AgentJob();
            existingJob.prePersist(); // generates ID
            when(
                agentJobRepository.findByWorkspaceIdAndIdempotencyKeyAndStatusIn(
                    eq(1L),
                    eq("pr_review:owner/repo:42:abc123"),
                    any()
                )
            ).thenReturn(Optional.of(existingJob));

            Optional<AgentJob> result = service.submit(
                1L,
                AgentJobType.PULL_REQUEST_REVIEW,
                mock(JobSubmissionRequest.class)
            );

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(existingJob.getId());
            verify(agentJobRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("should create job and publish event on success")
        void shouldCreateJobAndPublishEvent() {
            when(agentConfigRepository.findByWorkspaceId(1L)).thenReturn(List.of(enabledConfig));
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

            JobTypeHandler handler = mock(JobTypeHandler.class);
            when(handlerRegistry.getHandler(AgentJobType.PULL_REQUEST_REVIEW)).thenReturn(handler);
            when(handler.createSubmission(any())).thenReturn(createSubmission());

            when(agentJobRepository.findByWorkspaceIdAndIdempotencyKeyAndStatusIn(anyLong(), any(), any())).thenReturn(
                Optional.empty()
            );

            // Simulate saveAndFlush succeeding
            when(agentJobRepository.saveAndFlush(any(AgentJob.class))).thenAnswer(inv -> {
                AgentJob j = inv.getArgument(0);
                j.prePersist(); // simulate @PrePersist generating UUID + token
                return j;
            });

            Optional<AgentJob> result = service.submit(
                1L,
                AgentJobType.PULL_REQUEST_REVIEW,
                mock(JobSubmissionRequest.class)
            );

            assertThat(result).isPresent();
            AgentJob job = result.get();
            assertThat(job.getWorkspace()).isEqualTo(workspace);
            assertThat(job.getConfig()).isEqualTo(enabledConfig);
            assertThat(job.getJobType()).isEqualTo(AgentJobType.PULL_REQUEST_REVIEW);
            assertThat(job.getIdempotencyKey()).isEqualTo("pr_review:owner/repo:42:abc123");
            assertThat(job.getConfigSnapshot()).isNotNull();

            // Verify event published
            ArgumentCaptor<AgentJobCreatedEvent> eventCaptor = ArgumentCaptor.forClass(AgentJobCreatedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().workspaceId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should copy llmApiKey for API_KEY credential mode")
        void shouldCopyLlmApiKeyForApiKeyMode() {
            enabledConfig.setCredentialMode(CredentialMode.API_KEY);
            enabledConfig.setLlmApiKey("sk-test-key");
            enabledConfig.setAllowInternet(true);

            when(agentConfigRepository.findByWorkspaceId(1L)).thenReturn(List.of(enabledConfig));
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

            JobTypeHandler handler = mock(JobTypeHandler.class);
            when(handlerRegistry.getHandler(AgentJobType.PULL_REQUEST_REVIEW)).thenReturn(handler);
            when(handler.createSubmission(any())).thenReturn(createSubmission());

            when(agentJobRepository.findByWorkspaceIdAndIdempotencyKeyAndStatusIn(anyLong(), any(), any())).thenReturn(
                Optional.empty()
            );
            when(agentJobRepository.saveAndFlush(any())).thenAnswer(inv -> {
                AgentJob j = inv.getArgument(0);
                j.prePersist();
                return j;
            });

            Optional<AgentJob> result = service.submit(
                1L,
                AgentJobType.PULL_REQUEST_REVIEW,
                mock(JobSubmissionRequest.class)
            );

            assertThat(result).isPresent();
            assertThat(result.get().getLlmApiKey()).isEqualTo("sk-test-key");
        }

        @Test
        @DisplayName("should copy llmApiKey for PROXY credential mode (used by proxy for upstream auth)")
        void shouldCopyLlmApiKeyForProxyMode() {
            enabledConfig.setCredentialMode(CredentialMode.PROXY);
            enabledConfig.setLlmApiKey("sk-proxy-key");

            when(agentConfigRepository.findByWorkspaceId(1L)).thenReturn(List.of(enabledConfig));
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

            JobTypeHandler handler = mock(JobTypeHandler.class);
            when(handlerRegistry.getHandler(AgentJobType.PULL_REQUEST_REVIEW)).thenReturn(handler);
            when(handler.createSubmission(any())).thenReturn(createSubmission());

            when(agentJobRepository.findByWorkspaceIdAndIdempotencyKeyAndStatusIn(anyLong(), any(), any())).thenReturn(
                Optional.empty()
            );
            when(agentJobRepository.saveAndFlush(any())).thenAnswer(inv -> {
                AgentJob j = inv.getArgument(0);
                j.prePersist();
                return j;
            });

            Optional<AgentJob> result = service.submit(
                1L,
                AgentJobType.PULL_REQUEST_REVIEW,
                mock(JobSubmissionRequest.class)
            );

            assertThat(result).isPresent();
            assertThat(result.get().getLlmApiKey()).isEqualTo("sk-proxy-key");
        }

        @Test
        @DisplayName("should throw 409 on DataIntegrityViolationException race condition")
        void shouldThrow409OnDataIntegrityViolation() {
            when(agentConfigRepository.findByWorkspaceId(1L)).thenReturn(List.of(enabledConfig));
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

            JobTypeHandler handler = mock(JobTypeHandler.class);
            when(handlerRegistry.getHandler(AgentJobType.PULL_REQUEST_REVIEW)).thenReturn(handler);
            when(handler.createSubmission(any())).thenReturn(createSubmission());

            when(
                agentJobRepository.findByWorkspaceIdAndIdempotencyKeyAndStatusIn(
                    eq(1L),
                    eq("pr_review:owner/repo:42:abc123"),
                    any()
                )
            ).thenReturn(Optional.empty());

            // saveAndFlush throws constraint violation (concurrent duplicate won the race)
            when(agentJobRepository.saveAndFlush(any())).thenThrow(
                new DataIntegrityViolationException("uk_agent_job_idempotency")
            );

            assertThatThrownBy(() ->
                service.submit(1L, AgentJobType.PULL_REQUEST_REVIEW, mock(JobSubmissionRequest.class))
            )
                .isInstanceOf(AgentJobStateConflictException.class)
                .hasMessageContaining("idempotency key");

            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("should pick first enabled config when multiple exist")
        void shouldPickFirstEnabledConfig() {
            AgentConfig disabled = new AgentConfig();
            disabled.setEnabled(false);

            when(agentConfigRepository.findByWorkspaceId(1L)).thenReturn(List.of(disabled, enabledConfig));
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

            JobTypeHandler handler = mock(JobTypeHandler.class);
            when(handlerRegistry.getHandler(AgentJobType.PULL_REQUEST_REVIEW)).thenReturn(handler);
            when(handler.createSubmission(any())).thenReturn(createSubmission());

            when(agentJobRepository.findByWorkspaceIdAndIdempotencyKeyAndStatusIn(anyLong(), any(), any())).thenReturn(
                Optional.empty()
            );
            when(agentJobRepository.saveAndFlush(any())).thenAnswer(inv -> {
                AgentJob j = inv.getArgument(0);
                j.prePersist();
                return j;
            });

            Optional<AgentJob> result = service.submit(
                1L,
                AgentJobType.PULL_REQUEST_REVIEW,
                mock(JobSubmissionRequest.class)
            );

            assertThat(result).isPresent();
            assertThat(result.get().getConfig()).isEqualTo(enabledConfig);
        }
    }

    @Nested
    @DisplayName("cancel()")
    class Cancel {

        private AgentJob createJobWithStatus(AgentJobStatus status) {
            AgentJob job = new AgentJob();
            job.prePersist();
            job.setWorkspace(workspace);
            job.setStatus(status);
            job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
            return job;
        }

        @Test
        @DisplayName("should cancel QUEUED job")
        void shouldCancelQueuedJob() {
            AgentJob job = createJobWithStatus(AgentJobStatus.QUEUED);
            UUID jobId = job.getId();

            when(agentJobRepository.findByIdAndWorkspaceId(jobId, 1L)).thenReturn(Optional.of(job));
            when(
                agentJobRepository.transitionStatus(eq(jobId), eq(AgentJobStatus.CANCELLED), any(), any(), any())
            ).thenReturn(1);

            AgentJob cancelledJob = createJobWithStatus(AgentJobStatus.CANCELLED);
            when(agentJobRepository.findByIdAndWorkspaceId(jobId, 1L))
                .thenReturn(Optional.of(job))
                .thenReturn(Optional.of(cancelledJob));

            AgentJob result = service.cancel(1L, jobId);

            assertThat(result.getStatus()).isEqualTo(AgentJobStatus.CANCELLED);
        }

        @Test
        @DisplayName("should cancel RUNNING job and call sandbox cancel")
        void shouldCancelRunningJobAndCallSandbox() {
            AgentJob job = createJobWithStatus(AgentJobStatus.RUNNING);
            UUID jobId = job.getId();

            when(agentJobRepository.findByIdAndWorkspaceId(jobId, 1L)).thenReturn(Optional.of(job));
            when(
                agentJobRepository.transitionStatus(eq(jobId), eq(AgentJobStatus.CANCELLED), any(), any(), any())
            ).thenReturn(1);

            AgentJob cancelledJob = createJobWithStatus(AgentJobStatus.CANCELLED);
            when(agentJobRepository.findByIdAndWorkspaceId(jobId, 1L))
                .thenReturn(Optional.of(job))
                .thenReturn(Optional.of(cancelledJob));

            service.cancel(1L, jobId);

            verify(sandboxManager).cancel(jobId);
        }

        @Test
        @DisplayName("should be idempotent for already-cancelled job")
        void shouldBeIdempotentForCancelledJob() {
            AgentJob job = createJobWithStatus(AgentJobStatus.CANCELLED);
            UUID jobId = job.getId();

            when(agentJobRepository.findByIdAndWorkspaceId(jobId, 1L)).thenReturn(Optional.of(job));

            AgentJob result = service.cancel(1L, jobId);

            assertThat(result.getStatus()).isEqualTo(AgentJobStatus.CANCELLED);
            verify(agentJobRepository, never()).transitionStatus(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should throw 409 for COMPLETED job")
        void shouldThrow409ForCompletedJob() {
            AgentJob job = createJobWithStatus(AgentJobStatus.COMPLETED);
            UUID jobId = job.getId();

            when(agentJobRepository.findByIdAndWorkspaceId(jobId, 1L)).thenReturn(Optional.of(job));

            assertThatThrownBy(() -> service.cancel(1L, jobId))
                .isInstanceOf(AgentJobStateConflictException.class)
                .hasMessageContaining("COMPLETED");
        }

        @Test
        @DisplayName("should throw 409 for FAILED job")
        void shouldThrow409ForFailedJob() {
            AgentJob job = createJobWithStatus(AgentJobStatus.FAILED);
            UUID jobId = job.getId();

            when(agentJobRepository.findByIdAndWorkspaceId(jobId, 1L)).thenReturn(Optional.of(job));

            assertThatThrownBy(() -> service.cancel(1L, jobId))
                .isInstanceOf(AgentJobStateConflictException.class)
                .hasMessageContaining("FAILED");
        }

        @Test
        @DisplayName("should throw 409 for TIMED_OUT job")
        void shouldThrow409ForTimedOutJob() {
            AgentJob job = createJobWithStatus(AgentJobStatus.TIMED_OUT);
            UUID jobId = job.getId();

            when(agentJobRepository.findByIdAndWorkspaceId(jobId, 1L)).thenReturn(Optional.of(job));

            assertThatThrownBy(() -> service.cancel(1L, jobId)).isInstanceOf(AgentJobStateConflictException.class);
        }

        @Test
        @DisplayName("should throw 404 for non-existent job")
        void shouldThrow404ForNonExistentJob() {
            UUID jobId = UUID.randomUUID();
            when(agentJobRepository.findByIdAndWorkspaceId(jobId, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancel(1L, jobId)).isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("should throw 409 when executor wins the race (updated==0)")
        void shouldThrow409WhenExecutorWinsRace() {
            AgentJob job = createJobWithStatus(AgentJobStatus.RUNNING);
            UUID jobId = job.getId();

            when(agentJobRepository.findByIdAndWorkspaceId(jobId, 1L)).thenReturn(Optional.of(job));
            // Executor already transitioned to COMPLETED
            when(agentJobRepository.transitionStatus(any(), any(), any(), any(), any())).thenReturn(0);

            AgentJob completedJob = createJobWithStatus(AgentJobStatus.COMPLETED);
            when(agentJobRepository.findByIdAndWorkspaceId(jobId, 1L))
                .thenReturn(Optional.of(job))
                .thenReturn(Optional.of(completedJob));

            assertThatThrownBy(() -> service.cancel(1L, jobId))
                .isInstanceOf(AgentJobStateConflictException.class)
                .hasMessageContaining("COMPLETED");

            verify(sandboxManager, never()).cancel(any());
        }
    }
}
