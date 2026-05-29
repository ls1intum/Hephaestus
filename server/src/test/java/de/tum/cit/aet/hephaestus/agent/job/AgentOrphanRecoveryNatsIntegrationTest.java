package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.NatsTestContainer;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFactory;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import io.nats.client.Connection;
import io.nats.client.ConsumerContext;
import io.nats.client.FetchConsumer;
import io.nats.client.JetStreamManagement;
import io.nats.client.Message;
import io.nats.client.StreamContext;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end runtime proof of multi-replica orphan recovery against REAL NATS JetStream + REAL
 * Postgres (#1138) — the path mocks can't cover: a dead worker's RUNNING job is detected, CAS-requeued,
 * and re-published onto the actual work queue where a live replica would claim it.
 *
 * <p>Runs with {@code agent.nats.enabled=true} but {@code runtime.worker.enabled=false}, so the
 * connection + submitter + sweeper wire against the container while the executor's pull loop does NOT —
 * keeping the republished message on the stream for deterministic verification (no sandbox needed,
 * no consume race).
 */
@DisplayName("Orphan recovery over real NATS (JetStream) Integration")
class AgentOrphanRecoveryNatsIntegrationTest extends BaseIntegrationTest {

    private static final String STREAM = "AGENT";
    private static final String SUBJECT_WILDCARD = "agent.jobs.>";

    @DynamicPropertySource
    static void natsProperties(DynamicPropertyRegistry registry) {
        registry.add("hephaestus.agent.nats.enabled", () -> "true");
        registry.add("hephaestus.agent.nats.server", NatsTestContainer::getServerUrl);
        // Executor + liveness reporter stay off so the pull loop doesn't consume what we publish.
        registry.add("hephaestus.runtime.worker.enabled", () -> "false");
        // Webhook role off: WebhookConfiguration is @ConditionalOnBean(Connection) and would otherwise
        // wake up (now that agentNatsConnection exists) and demand the unrelated sync "natsConnection".
        registry.add("hephaestus.runtime.webhook.enabled", () -> "false");
    }

    @Autowired
    @Qualifier("agentNatsConnection")
    private Connection nats;

    @Autowired
    private AgentJobSubmitter submitter;

    @Autowired
    private AgentJobZombieSweeper sweeper;

    @Autowired
    private AgentJobRepository jobRepository;

    @Autowired
    private WorkerRegistryRepository workerRegistryRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    private Workspace workspace;

    @BeforeEach
    void setUp() throws Exception {
        databaseTestUtils.cleanDatabase();
        ensureCleanStream();
        workspace = workspaceRepository.save(WorkspaceTestFactory.activeWorkspace("nats-orphan-ws"));
    }

    @Test
    @DisplayName("AgentJobSubmitter publishes a claimable message onto the real work queue")
    void submitterPublishesToRealJetStream() throws Exception {
        UUID jobId = UUID.randomUUID();

        submitter.publish(jobId, workspace.getId());

        assertThat(drainStream()).containsExactly(jobId.toString());
    }

    @Test
    @DisplayName("a dead worker's RUNNING job is requeued AND re-published to NATS for a sibling to claim")
    void orphanRecoveryRequeuesAndRepublishes() throws Exception {
        UUID jobId = runningJobOwnedBy("dead-replica", Instant.now().minus(Duration.ofMinutes(5)));
        registerStaleWorker("dead-replica", Instant.now().minus(Duration.ofMinutes(5)));

        sweeper.recoverOrphanedJobs();

        // DB: the job is back on the queue, ownership cleared, retry bumped.
        AgentJob requeued = jobRepository.findById(jobId).orElseThrow();
        assertThat(requeued.getStatus()).isEqualTo(AgentJobStatus.QUEUED);
        assertThat(requeued.getWorkerId()).isNull();
        assertThat(requeued.getRetryCount()).isEqualTo(1);

        // NATS: the job is actually on the work queue, ready for a live replica to claim.
        assertThat(drainStream()).contains(jobId.toString());
    }

    // ── helpers ──

    private void ensureCleanStream() throws Exception {
        JetStreamManagement jsm = nats.jetStreamManagement();
        StreamConfiguration cfg = StreamConfiguration.builder()
            .name(STREAM)
            .subjects(SUBJECT_WILDCARD)
            .retentionPolicy(RetentionPolicy.WorkQueue)
            .storageType(StorageType.File)
            .build();
        try {
            jsm.addStream(cfg);
        } catch (Exception alreadyExists) {
            jsm.purgeStream(STREAM); // drain leftovers from a prior test/run (reused container)
        }
    }

    /** Drain every message currently on the work queue, returning their payloads (job ids). */
    private List<String> drainStream() throws Exception {
        JetStreamManagement jsm = nats.jetStreamManagement();
        jsm.addOrUpdateConsumer(
            STREAM,
            ConsumerConfiguration.builder().durable("test-verifier").filterSubject(SUBJECT_WILDCARD).build()
        );
        StreamContext sc = nats.getStreamContext(STREAM);
        ConsumerContext cc = sc.getConsumerContext("test-verifier");
        List<String> payloads = new ArrayList<>();
        try (
            FetchConsumer fetch = cc.fetch(
                io.nats.client.FetchConsumeOptions.builder()
                    .maxMessages(20)
                    .expiresIn(Duration.ofSeconds(3).toMillis())
                    .build()
            )
        ) {
            Message msg;
            while ((msg = fetch.nextMessage()) != null) {
                payloads.add(new String(msg.getData(), StandardCharsets.UTF_8));
                msg.ack();
            }
        }
        return payloads;
    }

    private UUID runningJobOwnedBy(String workerId, Instant startedAt) {
        AgentJob job = new AgentJob();
        job.setWorkspace(workspace);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setStatus(AgentJobStatus.RUNNING);
        job.setConfigSnapshot(objectMapper.createObjectNode());
        job.setWorkerId(workerId);
        job.setStartedAt(startedAt);
        return jobRepository.saveAndFlush(job).getId();
    }

    private void registerStaleWorker(String workerId, Instant lastHeartbeat) {
        WorkerRegistry w = new WorkerRegistry();
        w.setWorkerId(workerId);
        w.setLastHeartbeat(lastHeartbeat);
        w.setRegisteredAt(lastHeartbeat);
        workerRegistryRepository.saveAndFlush(w);
    }
}
