package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmConnection;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmConnectionRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelPrice;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelPriceRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.ModelVisibility;
import de.tum.cit.aet.hephaestus.agent.catalog.PricingMode;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfig;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfigRepository;
import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.agent.usage.PricingState;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.TestEntities;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end proof of multi-replica orphan recovery against REAL Postgres (#1138, rewritten for the
 * #1368 NATS→Postgres cutover) — the path mocks can't cover: a dead worker's RUNNING job is detected,
 * CAS-requeued back to QUEUED, and becomes claimable by a live poller.
 *
 * <p>The queue IS the {@code agent_job} table now, so there is no separate transport to verify a
 * republish onto — recovery is proven entirely through row state: {@link AgentJobZombieSweeper}
 * requeues the orphan (RUNNING → QUEUED, ownership cleared, {@code retry_count} bumped), and
 * {@link AgentJobExecutor#processJob} (same package, called directly for a deterministic assertion
 * instead of racing the background poll loop) proves the requeued row is actually claimable again.
 *
 * <p>{@code hephaestus.agent.poll-interval} is set to an hour so the executor's own background poll
 * thread stays quiescent for the duration of the test — every claim in this test is driven explicitly.
 * The Docker host points at a socket that can never exist so the worker-role sandbox beans wire
 * (lazy clients, no I/O at construction) without needing a real Docker daemon; any resulting async
 * sandbox failure lands after this test's synchronous claim assertions, and is caught internally
 * (RUNNING → FAILED), never propagating.
 */
@DisplayName("Orphan recovery over PostgreSQL Integration")
class AgentOrphanRecoveryIntegrationTest extends BaseIntegrationTest {

    @DynamicPropertySource
    static void agentProperties(DynamicPropertyRegistry registry) {
        registry.add("hephaestus.agent.enabled", () -> "true");
        registry.add("hephaestus.agent.poll-interval", () -> "1h");
        registry.add("hephaestus.agent.max-retries", () -> "5");
        // application-test.yml turns the worker role off by default; AgentJobExecutor needs it on.
        registry.add("hephaestus.runtime.worker.enabled", () -> "true");
        registry.add("hephaestus.sandbox.docker-host", () ->
            "unix:///nonexistent/hephaestus-test-orphan-recovery.sock"
        );
        registry.add("hephaestus.agent.image.pull-policy", () -> "NEVER");
    }

    @Autowired
    private AgentJobZombieSweeper sweeper;

    @Autowired
    private AgentJobExecutor executor;

    @Autowired
    private AgentJobRepository jobRepository;

    @Autowired
    private WorkerRegistryRepository workerRegistryRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private AgentConfigRepository agentConfigRepository;

    @Autowired
    private LlmConnectionRepository connectionRepository;

    @Autowired
    private LlmModelRepository modelRepository;

    @Autowired
    private LlmModelPriceRepository priceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Workspace workspace;
    private AgentConfig agentConfig;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        workspace = workspaceRepository.save(TestEntities.activeWorkspace("orphan-recovery-ws"));

        LlmConnection connection = new LlmConnection();
        connection.setSlug("orphan-recovery");
        connection.setDisplayName("Orphan recovery provider");
        connection.setBaseUrl("https://api.openai.com/v1");
        connection.setApiProtocol("openai-completions");
        connection.setEnabled(true);
        connection = connectionRepository.save(connection);

        LlmModel model = new LlmModel();
        model.setConnection(connection);
        model.setSlug("orphan-recovery-model");
        model.setDisplayName("Orphan recovery model");
        model.setUpstreamModelId("test-model");
        model.setVisibility(ModelVisibility.PUBLIC);
        model.setEnabled(true);
        model = modelRepository.save(model);

        LlmModelPrice price = new LlmModelPrice();
        price.setModel(model);
        price.setPricingMode(PricingMode.NO_CHARGE);
        price.setNote("Integration-test model has no per-token charge");
        price.setEffectiveFrom(Instant.now().minusSeconds(60));
        priceRepository.save(price);

        agentConfig = new AgentConfig();
        agentConfig.setWorkspace(workspace);
        agentConfig.setName("orphan-recovery-config");
        agentConfig.setEnabled(true);
        agentConfig.setInstanceModel(model);
        agentConfig = agentConfigRepository.save(agentConfig);
    }

    @Test
    @DisplayName("a dead worker's RUNNING job is requeued (retry_count++) and becomes claimable again")
    void orphanRecoveryRequeuesAndBecomesClaimable() {
        UUID jobId = runningJobOwnedBy("dead-replica", Instant.now().minus(Duration.ofMinutes(5)), 0);
        registerStaleWorker("dead-replica", Instant.now().minus(Duration.ofMinutes(5)));

        sweeper.recoverOrphanedJobs();

        // DB: the job is back on the queue, ownership cleared, retry bumped.
        AgentJob requeued = jobRepository.findById(jobId).orElseThrow();
        assertThat(requeued.getStatus()).isEqualTo(AgentJobStatus.QUEUED);
        assertThat(requeued.getWorkerId()).isNull();
        assertThat(requeued.getRetryCount()).isEqualTo(1);

        // #1368 hardening: the requeue backs the job off (available_at = now + AgentJobBackoff), so it
        // is deliberately NOT yet a poll candidate — see jobWithFutureAvailableAtIsNotClaimed below for
        // that assertion. #1368 fix wave (finding #3): processJob's own SKIP LOCKED claim NOW also gates
        // on available_at <= now (closing the stale-poll-result race — see
        // AgentJobRepository#findByIdQueuedForUpdateSkipLocked's javadoc), so a direct claim attempt
        // made WHILE still backed off correctly does NOT succeed. Simulate the backoff having elapsed
        // (a later poll, after available_at has passed) by fast-forwarding it directly.
        fastForwardAvailableAt(jobId);

        boolean claimed = executor.processJob(jobId);
        assertThat(claimed).isTrue();

        AgentJob reclaimed = jobRepository.findById(jobId).orElseThrow();
        assertThat(reclaimed.getStatus()).isEqualTo(AgentJobStatus.RUNNING);
        assertThat(reclaimed.getRetryCount()).isEqualTo(1); // claim itself doesn't touch retry_count
    }

    @Test
    @DisplayName(
        "#1368 hardening: orphan requeue rotates the job token — the old token no longer authenticates, the new one does"
    )
    void orphanRequeueRotatesTheJobToken() {
        UUID jobId = runningJobOwnedBy("dead-replica", Instant.now().minus(Duration.ofMinutes(5)), 0);
        registerStaleWorker("dead-replica", Instant.now().minus(Duration.ofMinutes(5)));
        AgentJob before = jobRepository.findById(jobId).orElseThrow();
        String oldTokenHash = before.getJobTokenHash();

        sweeper.recoverOrphanedJobs();

        AgentJob requeued = jobRepository.findById(jobId).orElseThrow();
        String newTokenHash = requeued.getJobTokenHash();
        assertThat(newTokenHash).isNotEqualTo(oldTokenHash);

        // The old token is dead: this mirrors JobTokenAuthenticationFilter#resolveJobRouting's lookup
        // (hash + status=RUNNING) — the requeue moved status to QUEUED too, so BOTH conditions now fail
        // for the old token even before considering the hash change.
        assertThat(jobRepository.findByJobTokenHashAndStatus(oldTokenHash, AgentJobStatus.RUNNING)).isEmpty();

        // The new token authenticates once the job is claimed (RUNNING) again. #1368 fix wave (finding
        // #3): the claim now also gates on available_at, so fast-forward past the requeue's backoff
        // first — see orphanRecoveryRequeuesAndBecomesClaimable's comment for the full reasoning.
        fastForwardAvailableAt(jobId);

        boolean claimed = executor.processJob(jobId);
        assertThat(claimed).isTrue();
        assertThat(jobRepository.findByJobTokenHashAndStatus(newTokenHash, AgentJobStatus.RUNNING)).isPresent();
        assertThat(jobRepository.findByJobTokenHashAndStatus(oldTokenHash, AgentJobStatus.RUNNING)).isEmpty();
    }

    @Test
    @DisplayName("#1368 fix wave, finding #3: a direct claim attempt made WHILE still backed off does not succeed")
    void claimAttemptWhileStillBackedOffDoesNotSucceed() {
        UUID jobId = runningJobOwnedBy("dead-replica-5", Instant.now().minus(Duration.ofMinutes(5)), 0);
        registerStaleWorker("dead-replica-5", Instant.now().minus(Duration.ofMinutes(5)));

        sweeper.recoverOrphanedJobs();

        AgentJob requeued = jobRepository.findById(jobId).orElseThrow();
        assertThat(requeued.getStatus()).isEqualTo(AgentJobStatus.QUEUED);
        assertThat(requeued.getAvailableAt())
            .as("the backoff-computed available_at is still in the future")
            .isAfter(Instant.now());

        // Deliberately NOT fast-forwarded: a claim attempt against a job whose backoff has not yet
        // elapsed must be refused, closing the stale-poll-result race (a concurrent backoff-requeue
        // between a candidate poll and this claim must not be bypassable).
        boolean claimed = executor.processJob(jobId);

        assertThat(claimed).isFalse();
        AgentJob stillQueued = jobRepository.findById(jobId).orElseThrow();
        assertThat(stillQueued.getStatus()).isEqualTo(AgentJobStatus.QUEUED);
    }

    @Test
    @DisplayName("#1368 hardening: a job whose available_at is in the future is not offered as a poll candidate")
    void jobWithFutureAvailableAtIsNotClaimed() {
        UUID jobId = runningJobOwnedBy("dead-replica-4", Instant.now().minus(Duration.ofMinutes(5)), 0);
        registerStaleWorker("dead-replica-4", Instant.now().minus(Duration.ofMinutes(5)));

        sweeper.recoverOrphanedJobs();

        // The backoff-computed available_at from AgentJobBackoff.compute(1) is >= 15s in the future
        // (base 1^4 + 15 = 16s, jittered ±10%) — comfortably beyond "now" for this assertion.
        AgentJob requeued = jobRepository.findById(jobId).orElseThrow();
        assertThat(requeued.getStatus()).isEqualTo(AgentJobStatus.QUEUED);
        assertThat(requeued.getAvailableAt()).isAfter(Instant.now());

        assertThat(jobRepository.findQueuedIdsOldestFirst(10))
            .as("a not-yet-eligible QUEUED job must not be offered as a poll candidate")
            .doesNotContain(jobId);
    }

    @Test
    @DisplayName("an orphan already at the retry cap is failed, not requeued again")
    void orphanPastRetryCapIsFailedNotRequeued() {
        UUID jobId = runningJobOwnedBy("dead-replica-2", Instant.now().minus(Duration.ofMinutes(5)), 5);
        registerStaleWorker("dead-replica-2", Instant.now().minus(Duration.ofMinutes(5)));

        sweeper.recoverOrphanedJobs();

        AgentJob failed = jobRepository.findById(jobId).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(AgentJobStatus.FAILED);
        assertThat(failed.getErrorMessage()).contains("retry limit reached");
        // Never went back through QUEUED — no poll candidate left behind.
        assertThat(jobRepository.findQueuedIdsOldestFirst(10)).doesNotContain(jobId);
    }

    /**
     * #1368 fix wave: {@code requeueOrphan} used to CAS on {@code status='RUNNING'} alone. A belated
     * requeue attempt — e.g. a slow/duplicate sweeper pass working from a stale {@link OrphanedJobRef}
     * snapshot — could match a row that a LIVE sibling has since legitimately re-claimed: status is
     * RUNNING again, just under a different {@code worker_id}. Without the {@code worker_id} fence,
     * that belated write would silently steal the sibling's in-progress job back to QUEUED. This
     * proves the fence holds at the repository level directly (no need to orchestrate an actual race).
     */
    @Test
    @DisplayName(
        "requeueOrphan is fenced on worker_id — a stale caller cannot steal a job a live sibling has re-claimed"
    )
    void requeueOrphanDoesNotStealAJobReclaimedBySomeoneElse() {
        UUID jobId = runningJobOwnedBy("live-sibling", Instant.now(), 0);

        // A stale sweeper pass believes the job is still owned by a worker that has since died AND been
        // superseded — "dead-replica" is not the row's actual current owner. @Modifying queries need an
        // active transaction (the sweeper normally provides one via TransactionTemplate); wrap here too.
        String candidateNewToken = AgentJob.generateJobToken();
        int updated = transactionTemplate.execute(s ->
            jobRepository.requeueOrphan(
                jobId,
                "dead-replica",
                5,
                Instant.now(),
                candidateNewToken,
                AgentJob.computeTokenHash(candidateNewToken)
            )
        );

        assertThat(updated)
            .as("the CAS must not match — the row's worker_id does not match the stale caller's")
            .isZero();
        AgentJob untouched = jobRepository.findById(jobId).orElseThrow();
        assertThat(untouched.getStatus()).isEqualTo(AgentJobStatus.RUNNING);
        assertThat(untouched.getWorkerId()).isEqualTo("live-sibling");
        assertThat(untouched.getRetryCount()).isZero();
    }

    @Test
    @DisplayName("requeueOrphan enforces the retry cap in SQL even if a caller forgets to check it first")
    void requeueOrphanRefusesPastTheRetryCapEvenUnchecked() {
        UUID jobId = runningJobOwnedBy("dead-replica-3", Instant.now(), 5);

        String candidateNewToken = AgentJob.generateJobToken();
        int updated = transactionTemplate.execute(s ->
            jobRepository.requeueOrphan(
                jobId,
                "dead-replica-3",
                5,
                Instant.now(),
                candidateNewToken,
                AgentJob.computeTokenHash(candidateNewToken)
            )
        );

        assertThat(updated).isZero();
        AgentJob unchanged = jobRepository.findById(jobId).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(AgentJobStatus.RUNNING);
        assertThat(unchanged.getRetryCount()).isEqualTo(5);
    }

    // ── helpers ──

    private UUID runningJobOwnedBy(String workerId, Instant startedAt, int retryCount) {
        AgentJob job = new AgentJob();
        job.setWorkspace(workspace);
        job.setConfig(agentConfig);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setStatus(AgentJobStatus.RUNNING);
        job.setConfigSnapshot(
            new ConfigSnapshot(
                ConfigSnapshot.SCHEMA_VERSION,
                agentConfig.getId(),
                "orphan-recovery-config",
                "openai-completions",
                "https://api.openai.com/v1",
                "test-model",
                null,
                null,
                null,
                false,
                FundingSource.INSTANCE,
                agentConfig.getInstanceModel().getConnection().getId(),
                agentConfig.getInstanceModel().getId(),
                workspace.getId(),
                600,
                false
            )
                .withPriceSnapshot(
                    new LlmPriceSnapshot(
                        FundingSource.INSTANCE,
                        PricingState.NO_CHARGE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                    )
                )
                .toJson(objectMapper)
        );
        job.setWorkerId(workerId);
        job.setStartedAt(startedAt);
        job.setRetryCount(retryCount);
        return jobRepository.saveAndFlush(job).getId();
    }

    private void registerStaleWorker(String workerId, Instant lastHeartbeat) {
        WorkerRegistry w = new WorkerRegistry();
        w.setWorkerId(workerId);
        w.setLastHeartbeat(lastHeartbeat);
        w.setRegisteredAt(lastHeartbeat);
        workerRegistryRepository.saveAndFlush(w);
    }

    /**
     * Simulates the requeue's backoff having elapsed (#1368 fix wave, finding #3): moves
     * {@code available_at} into the past directly, standing in for "time passed and a later poll
     * iteration is now attempting the claim" without actually sleeping out the backoff window in the
     * test.
     */
    private void fastForwardAvailableAt(UUID jobId) {
        transactionTemplate.executeWithoutResult(status -> {
            AgentJob job = jobRepository.findById(jobId).orElseThrow();
            job.setAvailableAt(Instant.now().minus(Duration.ofSeconds(1)));
            jobRepository.saveAndFlush(job);
        });
    }
}
