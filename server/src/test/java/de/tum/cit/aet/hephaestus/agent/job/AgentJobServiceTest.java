package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
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
import de.tum.cit.aet.hephaestus.agent.handler.JobTypeHandlerRegistry;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmission;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmissionRequest;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxManager;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class AgentJobServiceTest extends BaseUnitTest {

    @Mock
    private AgentJobRepository agentJobRepository;

    @Mock
    private AgentConfigRepository agentConfigRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private ReviewableArtifactLoader artifactLoader;

    @Mock
    private de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService connectionService;

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
            artifactLoader,
            connectionService,
            handlerRegistry,
            objectMapper,
            eventPublisher,
            transactionTemplate,
            new PracticeReviewProperties(false, true, false, "", 15, false, false, false),
            sandboxManager,
            java.util.Optional.empty()
        );

        workspace = new Workspace();
        workspace.setId(1L);
        workspace.setWorkspaceSlug("test-ws");

        enabledConfig = new AgentConfig();
        enabledConfig.setId(10L);
        enabledConfig.setWorkspace(workspace);
        enabledConfig.setName("test-config");
        enabledConfig.setEnabled(true);
        enabledConfig.setLlmProvider(LlmProvider.ANTHROPIC);
        enabledConfig.setCredentialMode(CredentialMode.PROXY);
        enabledConfig.setTimeoutSeconds(600);
    }

    private JobSubmission createSubmission() {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("pr_number", 42);
        // Shipped 5-segment grammar: <type>:<nameWithOwner>:<number>:<phase>:<freshness>
        // (PullRequestReviewHandler emits the trigger-event phase before the head SHA).
        return new JobSubmission(metadata, "pr_review:owner/repo:42:authoring:abc123");
    }

    @Nested
    class Submit {

        @BeforeEach
        @SuppressWarnings("unchecked")
        void setUpSubmit() {
            // Make transactionTemplate.execute() actually invoke the callback
            // (submitForConfig uses transactionTemplate.execute() for per-config isolation)
            lenient()
                .when(transactionTemplate.execute(any()))
                .thenAnswer(inv -> {
                    TransactionCallback<?> callback = inv.getArgument(0);
                    return callback.doInTransaction(mock(TransactionStatus.class));
                });
        }

        @Test
        void shouldReturnEmptyWhenNoEnabledConfig() {
            AgentConfig disabledConfig = new AgentConfig();
            disabledConfig.setEnabled(false);
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
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
        void shouldReturnEmptyWhenNoConfigs() {
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
            when(agentConfigRepository.findByWorkspaceId(1L)).thenReturn(List.of());

            Optional<AgentJob> result = service.submit(
                1L,
                AgentJobType.PULL_REQUEST_REVIEW,
                mock(JobSubmissionRequest.class)
            );

            assertThat(result).isEmpty();
        }

        @Test
        void shouldSubmitOnlyBoundConfigAndSuppressFanOut() {
            workspace.setPracticeConfigId(10L);
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
            when(agentConfigRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Optional.of(enabledConfig));

            JobTypeHandler handler = mock(JobTypeHandler.class);
            when(handlerRegistry.getHandler(AgentJobType.PULL_REQUEST_REVIEW)).thenReturn(handler);
            when(handler.createSubmission(any())).thenReturn(createSubmission());
            when(agentJobRepository.findByWorkspaceIdAndIdempotencyKeyAndStatusIn(anyLong(), any(), any())).thenReturn(
                Optional.empty()
            );
            when(agentJobRepository.saveAndFlush(any(AgentJob.class))).thenAnswer(inv -> {
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
            // Fan-out is suppressed when a config is bound: the all-enabled lookup is never consulted.
            verify(agentConfigRepository, never()).findByWorkspaceId(anyLong());
            verify(agentJobRepository).saveAndFlush(any());
        }

        @Test
        void shouldSubmitNothingWhenBoundConfigDisabled() {
            AgentConfig disabled = new AgentConfig();
            disabled.setId(10L);
            disabled.setEnabled(false);
            workspace.setPracticeConfigId(10L);
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
            when(agentConfigRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Optional.of(disabled));

            Optional<AgentJob> result = service.submit(
                1L,
                AgentJobType.PULL_REQUEST_REVIEW,
                mock(JobSubmissionRequest.class)
            );

            assertThat(result).isEmpty();
            verify(agentJobRepository, never()).saveAndFlush(any());
        }

        @Test
        void shouldSubmitNothingWhenBoundConfigIsForeign() {
            // Stale/foreign binding: config 10 is not in this workspace. The scoped finder returns
            // empty — we must pause (no job), NOT silently fall back to fan-out, and never touch the
            // all-enabled lookup. This pins the tenancy boundary the scalar FK relies on.
            workspace.setPracticeConfigId(10L);
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
            when(agentConfigRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Optional.empty());

            Optional<AgentJob> result = service.submit(
                1L,
                AgentJobType.PULL_REQUEST_REVIEW,
                mock(JobSubmissionRequest.class)
            );

            assertThat(result).isEmpty();
            verify(agentJobRepository, never()).saveAndFlush(any());
            verify(agentConfigRepository, never()).findByWorkspaceId(anyLong());
        }

        @Test
        void shouldReturnExistingJobOnIdempotencyMatch() {
            when(agentConfigRepository.findByWorkspaceId(1L)).thenReturn(List.of(enabledConfig));
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

            JobTypeHandler handler = mock(JobTypeHandler.class);
            when(handlerRegistry.getHandler(AgentJobType.PULL_REQUEST_REVIEW)).thenReturn(handler);
            when(handler.createSubmission(any())).thenReturn(createSubmission());

            AgentJob existingJob = new AgentJob();
            existingJob.prePersist(); // generates ID
            when(
                agentJobRepository.findByWorkspaceIdAndIdempotencyKeyAndStatusIn(
                    eq(1L),
                    eq("pr_review:owner/repo:42:authoring:abc123:config:10"),
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
            assertThat(job.getIdempotencyKey()).isEqualTo("pr_review:owner/repo:42:authoring:abc123:config:10");
            assertThat(job.getConfigSnapshot()).isNotNull();

            // Verify event published
            ArgumentCaptor<AgentJobCreatedEvent> eventCaptor = ArgumentCaptor.forClass(AgentJobCreatedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().workspaceId()).isEqualTo(1L);
        }

        @Test
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
        void shouldReturnEmptyOnDataIntegrityViolation() {
            when(agentConfigRepository.findByWorkspaceId(1L)).thenReturn(List.of(enabledConfig));
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

            JobTypeHandler handler = mock(JobTypeHandler.class);
            when(handlerRegistry.getHandler(AgentJobType.PULL_REQUEST_REVIEW)).thenReturn(handler);
            when(handler.createSubmission(any())).thenReturn(createSubmission());

            when(
                agentJobRepository.findByWorkspaceIdAndIdempotencyKeyAndStatusIn(
                    eq(1L),
                    eq("pr_review:owner/repo:42:authoring:abc123:config:10"),
                    any()
                )
            ).thenReturn(Optional.empty());

            // saveAndFlush throws constraint violation (concurrent duplicate won the race)
            when(agentJobRepository.saveAndFlush(any())).thenThrow(
                new DataIntegrityViolationException("uk_agent_job_idempotency")
            );

            // submitForConfig catches DataIntegrityViolationException and returns null,
            // which results in Optional.empty() from submit()
            Optional<AgentJob> result = service.submit(
                1L,
                AgentJobType.PULL_REQUEST_REVIEW,
                mock(JobSubmissionRequest.class)
            );

            assertThat(result).isEmpty();
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
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

        @Test
        void shouldSkipSubmissionWhenCooldownActive() {
            // Default workspace inherits the property cooldownMinutes=15, so the cooldown branch runs. A
            // recent job for the same (PR, phase)/config → submission is skipped (no new job persisted).
            when(agentConfigRepository.findByWorkspaceId(1L)).thenReturn(List.of(enabledConfig));
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

            JobTypeHandler handler = mock(JobTypeHandler.class);
            when(handlerRegistry.getHandler(AgentJobType.PULL_REQUEST_REVIEW)).thenReturn(handler);
            when(handler.createSubmission(any())).thenReturn(createSubmission());
            when(agentJobRepository.findByWorkspaceIdAndIdempotencyKeyAndStatusIn(anyLong(), any(), any())).thenReturn(
                Optional.empty()
            );

            AgentJob recent = new AgentJob();
            recent.prePersist();
            when(agentJobRepository.findRecentJobByKeyPrefix(eq(1L), any(), any())).thenReturn(Optional.of(recent));

            Optional<AgentJob> result = service.submit(
                1L,
                AgentJobType.PULL_REQUEST_REVIEW,
                mock(JobSubmissionRequest.class)
            );

            assertThat(result).isEmpty();
            verify(agentJobRepository, never()).saveAndFlush(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        void shouldCreateJobWhenCooldownElapsed() {
            // Cooldown lookup returns empty (no recent job within the window) → the job is created.
            when(agentConfigRepository.findByWorkspaceId(1L)).thenReturn(List.of(enabledConfig));
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

            JobTypeHandler handler = mock(JobTypeHandler.class);
            when(handlerRegistry.getHandler(AgentJobType.PULL_REQUEST_REVIEW)).thenReturn(handler);
            when(handler.createSubmission(any())).thenReturn(createSubmission());
            when(agentJobRepository.findByWorkspaceIdAndIdempotencyKeyAndStatusIn(anyLong(), any(), any())).thenReturn(
                Optional.empty()
            );
            when(agentJobRepository.findRecentJobByKeyPrefix(eq(1L), any(), any())).thenReturn(Optional.empty());
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
            verify(agentJobRepository).saveAndFlush(any());
        }

        @Test
        void shouldEscapeLikeWildcardsInCooldownPrefix() {
            // A repo name with a LIKE single-char wildcard ('_') must be escaped, or the cooldown prefix
            // would spuriously match unrelated keys (e.g. "my_org" matching "myXorg"). Capture the prefix
            // passed to the LIKE query (ESCAPE '\') and assert the '_' is backslash-escaped.
            when(agentConfigRepository.findByWorkspaceId(1L)).thenReturn(List.of(enabledConfig));
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

            ObjectNode metadata = objectMapper.createObjectNode();
            JobSubmission underscoreKey = new JobSubmission(metadata, "pr_review:my_org/my%repo:42:authoring:abc123");
            JobTypeHandler handler = mock(JobTypeHandler.class);
            when(handlerRegistry.getHandler(AgentJobType.PULL_REQUEST_REVIEW)).thenReturn(handler);
            when(handler.createSubmission(any())).thenReturn(underscoreKey);
            when(agentJobRepository.findByWorkspaceIdAndIdempotencyKeyAndStatusIn(anyLong(), any(), any())).thenReturn(
                Optional.empty()
            );
            when(agentJobRepository.findRecentJobByKeyPrefix(eq(1L), any(), any())).thenReturn(Optional.empty());
            when(agentJobRepository.saveAndFlush(any())).thenAnswer(inv -> {
                AgentJob j = inv.getArgument(0);
                j.prePersist();
                return j;
            });

            service.submit(1L, AgentJobType.PULL_REQUEST_REVIEW, mock(JobSubmissionRequest.class));

            ArgumentCaptor<String> prefix = ArgumentCaptor.forClass(String.class);
            verify(agentJobRepository).findRecentJobByKeyPrefix(eq(1L), prefix.capture(), any());
            // Phase preserved, freshness stripped, both LIKE metacharacters escaped, config scope appended.
            assertThat(prefix.getValue()).isEqualTo("pr\\_review:my\\_org/my\\%repo:42:authoring:%:config:10");
        }
    }

    @Nested
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
        void shouldBeIdempotentForCancelledJob() {
            AgentJob job = createJobWithStatus(AgentJobStatus.CANCELLED);
            UUID jobId = job.getId();

            when(agentJobRepository.findByIdAndWorkspaceId(jobId, 1L)).thenReturn(Optional.of(job));

            AgentJob result = service.cancel(1L, jobId);

            assertThat(result.getStatus()).isEqualTo(AgentJobStatus.CANCELLED);
            verify(agentJobRepository, never()).transitionStatus(any(), any(), any(), any(), any());
        }

        @Test
        void shouldThrow409ForCompletedJob() {
            AgentJob job = createJobWithStatus(AgentJobStatus.COMPLETED);
            UUID jobId = job.getId();

            when(agentJobRepository.findByIdAndWorkspaceId(jobId, 1L)).thenReturn(Optional.of(job));

            assertThatThrownBy(() -> service.cancel(1L, jobId))
                .isInstanceOf(AgentJobStateConflictException.class)
                .hasMessageContaining("COMPLETED");
        }

        @Test
        void shouldThrow409ForFailedJob() {
            AgentJob job = createJobWithStatus(AgentJobStatus.FAILED);
            UUID jobId = job.getId();

            when(agentJobRepository.findByIdAndWorkspaceId(jobId, 1L)).thenReturn(Optional.of(job));

            assertThatThrownBy(() -> service.cancel(1L, jobId))
                .isInstanceOf(AgentJobStateConflictException.class)
                .hasMessageContaining("FAILED");
        }

        @Test
        void shouldThrow409ForTimedOutJob() {
            AgentJob job = createJobWithStatus(AgentJobStatus.TIMED_OUT);
            UUID jobId = job.getId();

            when(agentJobRepository.findByIdAndWorkspaceId(jobId, 1L)).thenReturn(Optional.of(job));

            assertThatThrownBy(() -> service.cancel(1L, jobId)).isInstanceOf(AgentJobStateConflictException.class);
        }

        @Test
        void shouldThrow404ForNonExistentJob() {
            UUID jobId = UUID.randomUUID();
            when(agentJobRepository.findByIdAndWorkspaceId(jobId, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancel(1L, jobId)).isInstanceOf(EntityNotFoundException.class);
        }

        @Test
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

    @Nested
    class RetryDelivery {

        private static final Long WORKSPACE_ID = 1L;

        private AgentJob completedJob;
        private UUID jobId;
        private JobTypeHandler handler;

        @BeforeEach
        @SuppressWarnings("unchecked")
        void setUpRetryDelivery() {
            // Make transactionTemplate.execute() actually invoke the callback
            when(transactionTemplate.execute(any())).thenAnswer(inv -> {
                TransactionCallback<?> callback = inv.getArgument(0);
                return callback.doInTransaction(mock(TransactionStatus.class));
            });

            // Make transactionTemplate.executeWithoutResult() invoke the consumer (lenient:
            // not all tests reach the delivery path that calls executeWithoutResult)
            lenient()
                .doAnswer(inv -> {
                    Consumer<TransactionStatus> action = inv.getArgument(0);
                    action.accept(mock(TransactionStatus.class));
                    return null;
                })
                .when(transactionTemplate)
                .executeWithoutResult(any());

            completedJob = new AgentJob();
            completedJob.prePersist();
            completedJob.setWorkspace(workspace);
            completedJob.setStatus(AgentJobStatus.COMPLETED);
            completedJob.setJobType(AgentJobType.PULL_REQUEST_REVIEW);

            jobId = completedJob.getId();

            handler = mock(JobTypeHandler.class);
            // Lenient: not all tests reach the delivery path that calls getHandler
            lenient().when(handlerRegistry.getHandler(AgentJobType.PULL_REQUEST_REVIEW)).thenReturn(handler);
        }

        @Test
        void shouldThrow404WhenJobNotFound() {
            when(agentJobRepository.findByIdAndWorkspaceId(jobId, WORKSPACE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.retryDelivery(WORKSPACE_ID, jobId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("AgentJob");
        }

        @Test
        void shouldThrow409WhenCasTransitionReturnsZero() {
            when(agentJobRepository.findByIdAndWorkspaceId(jobId, WORKSPACE_ID)).thenReturn(Optional.of(completedJob));
            when(agentJobRepository.transitionDeliveryStatus(eq(jobId), eq(DeliveryStatus.PENDING), any())).thenReturn(
                0
            );

            assertThatThrownBy(() -> service.retryDelivery(WORKSPACE_ID, jobId))
                .isInstanceOf(AgentJobStateConflictException.class)
                .hasMessageContaining("delivery status FAILED");

            verify(handler, never()).deliver(any());
        }

        @Test
        void shouldDeliverAndUpdateStatusOnSuccess() {
            when(agentJobRepository.findByIdAndWorkspaceId(jobId, WORKSPACE_ID)).thenReturn(Optional.of(completedJob));
            when(agentJobRepository.transitionDeliveryStatus(eq(jobId), eq(DeliveryStatus.PENDING), any())).thenReturn(
                1
            );
            when(agentJobRepository.findById(jobId)).thenReturn(Optional.of(completedJob));

            AgentJob result = service.retryDelivery(WORKSPACE_ID, jobId);

            verify(handler).deliver(completedJob);
            verify(agentJobRepository).updateDeliveryStatus(
                eq(jobId),
                eq(DeliveryStatus.DELIVERED),
                eq(completedJob.getDeliveryCommentId())
            );
            assertThat(result.getId()).isEqualTo(jobId);
        }

        @Test
        void shouldRevertToFailedOnDeliveryException() {
            when(agentJobRepository.findByIdAndWorkspaceId(jobId, WORKSPACE_ID)).thenReturn(Optional.of(completedJob));
            when(agentJobRepository.transitionDeliveryStatus(eq(jobId), eq(DeliveryStatus.PENDING), any())).thenReturn(
                1
            );
            when(agentJobRepository.findById(jobId)).thenReturn(Optional.of(completedJob));

            doThrow(new RuntimeException("GitHub API rate limited")).when(handler).deliver(completedJob);

            assertThatThrownBy(() -> service.retryDelivery(WORKSPACE_ID, jobId))
                .isInstanceOf(AgentJobStateConflictException.class)
                .hasMessageContaining("Delivery retry failed")
                .hasMessageContaining("GitHub API rate limited");

            verify(agentJobRepository).updateDeliveryStatus(
                eq(jobId),
                eq(DeliveryStatus.FAILED),
                eq(completedJob.getDeliveryCommentId())
            );
        }
    }

    @Nested
    class CooldownKeyPrefix {

        @Test
        void prKeyStripsFreshnessKeepsPhase() {
            // Shipped 5-segment PR key: <type>:<nameWithOwner>:<number>:<phase>:<sha>. Only the trailing
            // SHA is stripped → cooldown scopes per (PR, phase), so an authoring re-trigger is cooled
            // down but a later merge retrospective (different phase) is NOT.
            assertThat(AgentJobService.extractCooldownKeyPrefix("pr_review:owner/repo:42:authoring:abc123")).isEqualTo(
                "pr_review:owner/repo:42:authoring:"
            );
        }

        @Test
        void issueKeyStripsFreshnessKeepsPhase() {
            // Shipped 5-segment issue key: the trailing segment is the disposable updatedAt version, the
            // 4th is the phase. A regression that dropped the freshness segment AND the phase would
            // collapse cooldown back to per-repo; pin that the (issue number, phase) scope survives.
            assertThat(
                AgentJobService.extractCooldownKeyPrefix("issue_review:owner/repo:12:IssueOpened:1700000000000")
            ).isEqualTo("issue_review:owner/repo:12:IssueOpened:");
        }
    }

    @Nested
    class DevTriggerSupport {

        @Test
        void buildReviewRequestReturnsNullWhenBranchInfoMissing() {
            de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest pr =
                new de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest();
            pr.setId(5L);
            // headRefOid/headRefName/baseRefName all null → nothing to clone or diff.
            assertThat(service.buildReviewRequest(pr, "PullRequestMerged")).isNull();
        }

        @Test
        void buildReviewRequestBuildsDetachedRequestWhenBranchInfoPresent() {
            de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest pr =
                new de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest();
            pr.setId(5L);
            pr.setHeadRefOid("abc123");
            pr.setHeadRefName("feature/test");
            pr.setBaseRefName("main");
            de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository repo =
                new de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository();
            repo.setId(100L);
            repo.setNameWithOwner("owner/repo");
            pr.setRepository(repo);

            var request = service.buildReviewRequest(pr, "PullRequestMerged");

            assertThat(request).isNotNull();
            assertThat(request.headRefOid()).isEqualTo("abc123");
            assertThat(request.triggerEvent()).isEqualTo("PullRequestMerged");
        }

        @Test
        void buildIssueRequestReturnsNullWhenRepositoryMissing() {
            de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue issue =
                new de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue();
            issue.setId(7L);
            assertThat(service.buildIssueRequest(issue, "IssueClosed")).isNull();
        }

        @Test
        @SuppressWarnings("unchecked")
        void submitPreparedRunsSubmitAndRendersNoConfigMessage() {
            // submitPrepared is invoked by the controller AFTER the prep transaction commits, so submit() runs
            // outside any outer transaction (SYSTEMIC #5). With no enabled config it renders the no-job message.
            lenient()
                .when(transactionTemplate.execute(any()))
                .thenAnswer(inv -> {
                    TransactionCallback<?> callback = inv.getArgument(0);
                    return callback.doInTransaction(mock(TransactionStatus.class));
                });
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
            when(agentConfigRepository.findByWorkspaceId(1L)).thenReturn(List.of());

            String result = service.submitPrepared(
                1L,
                AgentJobType.PULL_REQUEST_REVIEW,
                mock(JobSubmissionRequest.class)
            );

            assertThat(result).isEqualTo("No job created (no enabled agent config?)");
        }
    }
}
