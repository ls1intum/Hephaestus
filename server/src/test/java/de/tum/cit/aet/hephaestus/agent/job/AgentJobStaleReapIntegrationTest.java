package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;

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
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageSourceType;
import de.tum.cit.aet.hephaestus.agent.usage.PricingState;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.LlmCatalogTestFixtures;
import de.tum.cit.aet.hephaestus.testconfig.TestEntities;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The absolute-timeout reaper against REAL Postgres — the half of {@link AgentJobZombieSweeper} that
 * {@link AgentOrphanRecoveryIntegrationTest} does not drive.
 *
 * <p>The unit test for this path mocks both the {@code TransactionTemplate} and the
 * {@link de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRecorder}, which is exactly what makes it
 * unable to see the two things that matter here:
 *
 * <ol>
 *   <li>The per-job transaction boundary is REAL. The reaper's state transition and its ledger append
 *       are one unit, so a throw in the append genuinely rolls the transition back. A mocked
 *       {@code TransactionTemplate} that merely runs the callback inline cannot roll anything back,
 *       so under mocks a wedged job still looks reaped.</li>
 *   <li>{@code LlmUsageRecorder}'s {@code @Transactional(propagation = MANDATORY)} is genuinely
 *       satisfied. A mocked recorder has no propagation, so nothing proves the reaper actually calls
 *       it from inside a transaction rather than from the bare sweep loop.</li>
 * </ol>
 *
 * <p>This is the counterpart of {@code MentorTurnPersistenceIntegrationTest} for the agent-job side.
 */
@DisplayName("Stale RUNNING reaper over PostgreSQL Integration")
class AgentJobStaleReapIntegrationTest extends BaseIntegrationTest {

    @DynamicPropertySource
    static void agentProperties(DynamicPropertyRegistry registry) {
        registry.add("hephaestus.agent.enabled", () -> "true");
        // Keep the executor's background poll loop quiescent; every sweep here is driven explicitly.
        registry.add("hephaestus.agent.poll-interval", () -> "1h");
    }

    @Autowired
    private AgentJobZombieSweeper sweeper;

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
        workspace = workspaceRepository.save(TestEntities.activeWorkspace("stale-reap-ws"));
        LlmConnection connection = connectionRepository.save(LlmCatalogTestFixtures.connection("stale-reap"));
        instanceModel = modelRepository.save(
            LlmCatalogTestFixtures.model(connection, "stale-reap-model", "test-model")
        );
    }

    @Test
    @DisplayName("a genuinely stale RUNNING job reaches TIMED_OUT and its ledger event is committed with it")
    void staleRunningJobIsTimedOutAndBilled() {
        // 20 minutes into a 600s timeout (+5min buffer = 15min) — past the cutoff.
        UUID jobId = staleRunningJob(readableSnapshot(), withProxyUsage());

        sweeper.reapStaleRunningJobs();

        AgentJob reaped = jobRepository.findById(jobId).orElseThrow();
        assertThat(reaped.getStatus()).isEqualTo(AgentJobStatus.TIMED_OUT);
        assertThat(reaped.getErrorMessage()).contains("Reaped");

        // The ledger event landed in the SAME committed transaction — the recorder's MANDATORY
        // propagation is satisfied only because the reaper calls it from inside its per-job transaction.
        LlmUsageEvent event = onlyUsageEvent();
        assertThat(event.getSourceId()).isEqualTo(jobId);
        assertThat(event.getSourceType()).isEqualTo(LlmUsageSourceType.AGENT_JOB);
        assertThat(event.getWorkspace().getId()).isEqualTo(workspace.getId());
        // The proxy's counts survive the reap; billing this attempt as zero would understate the month.
        assertThat(event.getInputTokens()).isEqualTo(900L);
        assertThat(event.getOutputTokens()).isEqualTo(400L);
        assertThat(event.getTotalCalls()).isEqualTo(3);
        // NO_CHARGE is a declared price, so this is confirmed $0 — not unpriced.
        assertThat(event.getPricingState()).isEqualTo(PricingState.NO_CHARGE);
    }

    /**
     * The wedge. A snapshot written by a NEWER schema version — what a reverted canary rollout leaves
     * behind — is one {@link ConfigSnapshot#fromJson} refuses by design. Because accounting shares the
     * reaper's transaction, an unguarded parse there rolls back the TIMED_OUT write this test asserts,
     * and the row returns to RUNNING to fail identically on every subsequent sweep — burning a
     * {@code (workspace, purpose)} concurrency slot forever.
     *
     * <p>Only a real transaction manager can show this: under the unit test's mocked
     * {@code TransactionTemplate} the rollback does not happen and the job looks reaped either way.
     */
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
        // Honest verdict: the spend is real but cannot be priced, so the month reads unverifiable
        // rather than cheap.
        assertThat(event.getPricingState()).isEqualTo(PricingState.UNPRICED);
        assertThat(event.getCostUsd()).isNull();
        // The model name lived in the snapshot we could not read — absent, never guessed.
        assertThat(event.getModel()).isNull();
        // The observed tokens are still the only surviving record of the work the provider did.
        assertThat(event.getInputTokens()).isEqualTo(900L);

        // And the slot is genuinely released: a second sweep finds nothing left to reap, which is the
        // difference between "recovered" and "wedged in a 2-minute retry loop forever".
        sweeper.reapStaleRunningJobs();
        assertThat(usageEventRepository.findAll()).hasSize(1);
        assertThat(jobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(AgentJobStatus.TIMED_OUT);
    }

    // ── helpers ──

    private LlmUsageEvent onlyUsageEvent() {
        List<LlmUsageEvent> events = usageEventRepository.findAll();
        assertThat(events).as("the reap must append exactly one ledger event").hasSize(1);
        return events.getFirst();
    }

    /** Token counts the LLM proxy accumulated onto the row before the worker died. */
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

    private tools.jackson.databind.JsonNode readableSnapshot() {
        return snapshot().toJson(objectMapper);
    }

    /** {@code schemaVersion} one past this server's — the reverted-canary case. */
    private tools.jackson.databind.JsonNode snapshotFromTheFuture() {
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
            false,
            FundingSource.INSTANCE,
            instanceModel.getConnection().getId(),
            instanceModel.getId(),
            workspace.getId(),
            600,
            false,
            null
        ).withPriceSnapshot(
            new LlmPriceSnapshot(FundingSource.INSTANCE, PricingState.NO_CHARGE, null, null, null, null, null, null)
        );
    }

    private UUID staleRunningJob(tools.jackson.databind.JsonNode configSnapshot, AgentJob usage) {
        AgentJob job = new AgentJob();
        job.setWorkspace(workspace);
        job.setPurpose(AgentPurpose.PRACTICE_DETECTION);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setStatus(AgentJobStatus.RUNNING);
        job.setConfigSnapshot(configSnapshot);
        job.setWorkerId("dead-replica");
        job.setStartedAt(Instant.now().minus(Duration.ofMinutes(20)));
        // Non-null executionStartedAt is what makes this attempt billable: it got past preparation and
        // actually ran, so its spend has to be accounted for.
        job.setExecutionStartedAt(Instant.now().minus(Duration.ofMinutes(19)));
        job.setLlmTotalCalls(usage.getLlmTotalCalls());
        job.setLlmTotalInputTokens(usage.getLlmTotalInputTokens());
        job.setLlmTotalOutputTokens(usage.getLlmTotalOutputTokens());
        job.setLlmTotalReasoningTokens(usage.getLlmTotalReasoningTokens());
        job.setLlmCacheReadTokens(usage.getLlmCacheReadTokens());
        job.setLlmCacheWriteTokens(usage.getLlmCacheWriteTokens());
        return jobRepository.saveAndFlush(job).getId();
    }
}
