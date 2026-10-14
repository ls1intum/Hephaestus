package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmConnection;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmConnectionRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageEvent;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageEventRepository;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRecorder;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageSourceType;
import de.tum.cit.aet.hephaestus.agent.usage.PricingState;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.LlmCatalogTestFixtures;
import de.tum.cit.aet.hephaestus.testconfig.TestEntities;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The absolute-timeout reaper against REAL Postgres. The unit test for this path mocks both the
 * {@code TransactionTemplate} and the {@link de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRecorder},
 * so it cannot see the two things that matter here: a throw in the ledger append genuinely rolls the
 * state transition back, and the recorder's {@code @Transactional(propagation = MANDATORY)} is
 * genuinely satisfied by the reaper's own per-job transaction.
 */
@DisplayName("Stale RUNNING reaper over PostgreSQL Integration")
class AgentJobStaleReapIntegrationTest extends BaseIntegrationTest {

    private AgentJobZombieSweeper sweeper;

    @Autowired
    private WorkerRegistryRepository workerRegistryRepository;

    @Autowired
    private AgentProperties agentProperties;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private AgentJobLifecycleService lifecycleService;

    @Autowired
    private LlmUsageRecorder usageRecorder;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private AgentJobRepository jobRepository;

    @Autowired
    private LlmUsageEventRepository usageEventRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private LlmConnectionRepository connectionRepository;

    @Autowired
    private LlmModelRepository modelRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Workspace workspace;
    private LlmModel instanceModel;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        sweeper = sweeperWithRepository(jobRepository);
        workspace = workspaceRepository.save(TestEntities.activeWorkspace("stale-reap-ws"));
        LlmConnection connection = connectionRepository.save(LlmCatalogTestFixtures.connection("stale-reap"));
        instanceModel =
                modelRepository.save(LlmCatalogTestFixtures.model(connection, "stale-reap-model", "test-model"));
    }

    @Test
    @DisplayName("a genuinely stale RUNNING job reaches TIMED_OUT and its ledger event is committed with it")
    void staleRunningJobIsTimedOutAndBilled() {
        // Past the work timeout, upload grace, and stale-job safety buffer.
        UUID jobId = staleRunningJob(readableSnapshot(), withProxyUsage());

        sweeper.reapStaleRunningJobs();

        AgentJob reaped = jobRepository.findById(jobId).orElseThrow();
        assertThat(reaped.getStatus()).isEqualTo(AgentJobStatus.TIMED_OUT);
        assertThat(reaped.getErrorMessage()).contains("exceeded its timeout");

        LlmUsageEvent event = onlyUsageEvent();
        assertThat(event.getSourceId()).isEqualTo(jobId);
        assertThat(event.getSourceType()).isEqualTo(LlmUsageSourceType.AGENT_JOB);
        assertThat(event.getWorkspace().getId()).isEqualTo(workspace.getId());
        assertThat(event.getInputTokens()).isEqualTo(900L);
        assertThat(event.getOutputTokens()).isEqualTo(400L);
        assertThat(event.getTotalCalls()).isEqualTo(3);
        // NO_CHARGE is a declared price, so this is confirmed $0 — not unpriced.
        assertThat(event.getPricingState()).isEqualTo(PricingState.NO_CHARGE);
    }

    @Test
    @DisplayName("a job whose snapshot this server cannot read still terminalises, billed UNPRICED")
    void unreadableSnapshotStillTerminalisesAndDoesNotWedge() {
        UUID jobId = staleRunningJob(snapshotFromTheFuture(), withProxyUsage());

        sweeper.reapStaleRunningJobs();

        AgentJob reaped = jobRepository.findById(jobId).orElseThrow();
        assertThat(reaped.getStatus())
                .as("an unreadable price must not cost the job its exit from RUNNING")
                .isEqualTo(AgentJobStatus.TIMED_OUT);

        LlmUsageEvent event = onlyUsageEvent();
        assertThat(event.getSourceId()).isEqualTo(jobId);
        assertThat(event.getPricingState()).isEqualTo(PricingState.UNPRICED);
        assertThat(event.getCostUsd()).isNull();
        assertThat(event.getModel()).isNull();
        assertThat(event.getInputTokens()).isEqualTo(900L);

        // And the slot is genuinely released: a second sweep finds nothing left to reap, which is the
        // difference between "recovered" and "wedged in a 2-minute retry loop forever".
        sweeper.reapStaleRunningJobs();
        assertThat(usageEventRepository.findAll()).hasSize(1);
        assertThat(jobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(AgentJobStatus.TIMED_OUT);
    }

    @Test
    void shouldPreserveDeliveredStatusAndNewCommentWhenAnExhaustedCandidateIsStale() {
        UUID jobId = cappedDeliveryJob(workspace, DeliveryStatus.PENDING, "old-comment");
        AgentJobZombieSweeper racingSweeper =
                sweeperAfterEnumeration(() -> assertThat(jobRepository.transitionDeliveryStatusFenced(
                                jobId, DeliveryStatus.DELIVERED, "new-comment", Set.of(DeliveryStatus.PENDING), (short)
                                        3))
                        .isEqualTo(1));

        racingSweeper.recoverStuckDeliveries();

        assertDelivery(jobId, DeliveryStatus.DELIVERED, (short) 3, "new-comment");
    }

    @Test
    void shouldPreserveTheCurrentAttemptWhenAnExhaustedCandidateHasAnOlderGeneration() {
        UUID jobId = cappedDeliveryJob(workspace, DeliveryStatus.PENDING, "old-comment");
        // Arrange a newer generation with the existing CAS, not a sweep beyond the cap or a counter reset.
        AgentJobZombieSweeper racingSweeper =
                sweeperAfterEnumeration(() -> assertThat(jobRepository.claimDeliveryRecoveryAttempt(jobId, (short) 3))
                        .isEqualTo(1));

        racingSweeper.recoverStuckDeliveries();

        assertDelivery(jobId, DeliveryStatus.PENDING, (short) 4, "old-comment");
    }

    @Test
    void shouldFailOnlyTheCurrentCappedDeliveryAndLeaveItUnchangedOnTheNextSweep() {
        UUID jobId = cappedDeliveryJob(workspace, DeliveryStatus.PENDING, "old-comment");
        Workspace otherWorkspace = workspaceRepository.save(TestEntities.activeWorkspace("delivery-other-ws"));
        UUID otherJobId = cappedDeliveryJob(otherWorkspace, DeliveryStatus.DELIVERED, "other-comment");

        sweeper.recoverStuckDeliveries();

        assertDelivery(jobId, DeliveryStatus.FAILED, (short) 3, "old-comment");
        assertDelivery(otherJobId, DeliveryStatus.DELIVERED, (short) 3, "other-comment");

        sweeper.recoverStuckDeliveries();

        assertDelivery(jobId, DeliveryStatus.FAILED, (short) 3, "old-comment");
        assertDelivery(otherJobId, DeliveryStatus.DELIVERED, (short) 3, "other-comment");
    }

    private AgentJobZombieSweeper sweeperWithRepository(AgentJobRepository repository) {
        return new AgentJobZombieSweeper(
                repository,
                workerRegistryRepository,
                agentProperties,
                objectMapper,
                transactionTemplate,
                lifecycleService,
                usageRecorder,
                meterRegistry,
                new AgentJobTelemetry(meterRegistry, Tracer.NOOP));
    }

    private AgentJobZombieSweeper sweeperAfterEnumeration(Runnable competingTransition) {
        AgentJobRepository delegate = mock(AgentJobRepository.class, delegatesTo(jobRepository));
        doAnswer(invocation -> {
                    var candidates = jobRepository.findStuckPendingDeliveries(
                            invocation.getArgument(0, Instant.class), invocation.getArgument(1, Pageable.class));
                    // Commit after the real query, before returning its unchanged scalar projections.
                    transactionTemplate.executeWithoutResult(status -> competingTransition.run());
                    return candidates;
                })
                .doAnswer(delegatesTo(jobRepository))
                .when(delegate)
                .findStuckPendingDeliveries(any(Instant.class), any(Pageable.class));
        return sweeperWithRepository(delegate);
    }

    private UUID cappedDeliveryJob(Workspace owningWorkspace, DeliveryStatus deliveryStatus, String commentId) {
        AgentJob job = new AgentJob();
        job.setWorkspace(owningWorkspace);
        job.setPurpose(AgentPurpose.PRACTICE_REVIEW);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setStatus(AgentJobStatus.COMPLETED);
        job.setConfigSnapshot(readableSnapshot());
        job.setCompletedAt(Instant.now().minus(Duration.ofMinutes(20)));
        job.setDeliveryStatus(deliveryStatus);
        job.setDeliveryAttempts((short) 3);
        job.setDeliveryCommentId(commentId);
        return jobRepository.saveAndFlush(job).getId();
    }

    private void assertDelivery(
            UUID jobId, DeliveryStatus expectedStatus, short expectedAttempts, String expectedComment) {
        AgentJob stored = jobRepository.findById(jobId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(AgentJobStatus.COMPLETED);
        assertThat(stored.getDeliveryStatus()).isEqualTo(expectedStatus);
        assertThat(stored.getDeliveryAttempts()).isEqualTo(expectedAttempts);
        assertThat(stored.getDeliveryCommentId()).isEqualTo(expectedComment);
    }

    private LlmUsageEvent onlyUsageEvent() {
        List<LlmUsageEvent> events = usageEventRepository.findAll();
        assertThat(events).as("the reap must append exactly one ledger event").hasSize(1);
        return events.getFirst();
    }

    private static AgentJob withProxyUsage() {
        AgentJob job = new AgentJob();
        job.setLlmTotalCalls(3);
        job.setLlmTotalInputTokens(900);
        job.setLlmTotalOutputTokens(400);
        job.setLlmTotalReasoningTokens(0);
        job.setLlmCacheReadTokens(0);
        job.setLlmCacheWriteTokens(0);
        return job;
    }

    private JsonNode readableSnapshot() {
        return snapshot().toJson(objectMapper);
    }

    private JsonNode snapshotFromTheFuture() {
        ObjectNode node = (ObjectNode) snapshot().toJson(objectMapper);
        return node.put("schemaVersion", ConfigSnapshot.SCHEMA_VERSION + 1);
    }

    private ConfigSnapshot snapshot() {
        return new ConfigSnapshot(
                        ConfigSnapshot.SCHEMA_VERSION,
                        "openai-completions",
                        "https://api.openai.com/v1",
                        "test-model",
                        null,
                        null,
                        null,
                        null,
                        FundingSource.INSTANCE,
                        instanceModel.getConnection().getId(),
                        instanceModel.getId(),
                        workspace.getId(),
                        600,
                        false,
                        null,
                        null)
                .withPriceSnapshot(new LlmPriceSnapshot(
                        FundingSource.INSTANCE, PricingState.NO_CHARGE, null, null, null, null, null, null));
    }

    private UUID staleRunningJob(JsonNode configSnapshot, AgentJob usage) {
        AgentJob job = new AgentJob();
        job.setWorkspace(workspace);
        job.setPurpose(AgentPurpose.PRACTICE_REVIEW);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setStatus(AgentJobStatus.RUNNING);
        job.setConfigSnapshot(configSnapshot);
        job.setWorkerId("dead-replica");
        job.setStartedAt(Instant.now().minus(Duration.ofMinutes(30)));
        // Non-null executionStartedAt is what makes this attempt billable: it got past preparation and
        // actually ran, so its spend has to be accounted for.
        job.setExecutionStartedAt(Instant.now().minus(Duration.ofMinutes(29)));
        job.setLlmTotalCalls(usage.getLlmTotalCalls());
        job.setLlmTotalInputTokens(usage.getLlmTotalInputTokens());
        job.setLlmTotalOutputTokens(usage.getLlmTotalOutputTokens());
        job.setLlmTotalReasoningTokens(usage.getLlmTotalReasoningTokens());
        job.setLlmCacheReadTokens(usage.getLlmCacheReadTokens());
        job.setLlmCacheWriteTokens(usage.getLlmCacheWriteTokens());
        return jobRepository.saveAndFlush(job).getId();
    }
}
