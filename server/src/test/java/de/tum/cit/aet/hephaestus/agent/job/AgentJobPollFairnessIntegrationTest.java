package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfig;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfigRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.TestEntities;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;

/**
 * Head-of-line starvation fix for the poll candidate query (#1368 fix wave, adversarial review of the
 * NATS→Postgres cutover, finding 2): {@code findQueuedIdsOldestFirst} used to be a plain {@code WHERE
 * status='QUEUED' ORDER BY created_at LIMIT n} with no awareness of per-config concurrency caps. If the
 * oldest {@code n} QUEUED rows all belonged to a config already saturated on RUNNING jobs, every claim
 * attempt in that batch correctly skipped them (the concurrency-full outcome), but a younger,
 * immediately-runnable job belonging to a DIFFERENT config never even entered the candidate batch —
 * it would starve behind an unclaimable backlog for as many poll cycles as the saturated config stays
 * saturated. The query now excludes candidates whose config is already at its RUNNING cap.
 */
@DisplayName("Poll candidate fairness (#1368 fix wave)")
class AgentJobPollFairnessIntegrationTest extends BaseIntegrationTest {

    @DynamicPropertySource
    static void agentProperties(DynamicPropertyRegistry registry) {
        registry.add("hephaestus.agent.enabled", () -> "true");
        // Quiescent: this test drives claims explicitly rather than racing the background poll loop.
        registry.add("hephaestus.agent.poll-interval", () -> "1h");
        registry.add("hephaestus.runtime.worker.enabled", () -> "true");
        registry.add("hephaestus.sandbox.docker-host", () -> "unix:///nonexistent/hephaestus-test-fairness.sock");
        registry.add("hephaestus.agent.image.pull-policy", () -> "NEVER");
    }

    @Autowired
    private AgentJobRepository jobRepository;

    @Autowired
    private AgentConfigRepository configRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Workspace workspace;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        workspace = workspaceRepository.save(TestEntities.activeWorkspace("poll-fairness-ws"));
    }

    @Test
    @DisplayName(
        "a saturated config's older QUEUED backlog does not starve a younger, runnable job from a different config"
    )
    void youngerRunnableJobFromAnotherConfigIsNotStarved() {
        AgentConfig cappedConfig = agentConfig("capped-config", 1);
        AgentConfig openConfig = agentConfig("open-config", 3);

        // The capped config already has one RUNNING job at its max-concurrent-jobs=1 cap.
        runningJob(cappedConfig);

        // Older QUEUED backlog, all on the capped (unclaimable) config.
        Instant base = Instant.now().minus(1, ChronoUnit.HOURS);
        for (int i = 0; i < 5; i++) {
            queuedJob(cappedConfig, base.plusSeconds(i));
        }

        // A younger job on the OPEN config — created after all 5 capped-config candidates, so a naive
        // oldest-first LIMIT 5 query would never even fetch it.
        UUID youngerRunnableJobId = queuedJob(openConfig, base.plusSeconds(100));

        List<UUID> candidates = jobRepository.findQueuedIdsOldestFirst(5);

        assertThat(candidates)
            .as(
                "the open config's runnable job must be a poll candidate even though 5 older, unclaimable " +
                    "jobs exist ahead of it"
            )
            .contains(youngerRunnableJobId);
    }

    @Test
    @DisplayName("a config at its concurrency cap contributes no candidates at all")
    void cappedConfigContributesNoCandidates() {
        AgentConfig cappedConfig = agentConfig("fully-capped", 1);
        runningJob(cappedConfig);
        UUID queuedOnCappedConfig = queuedJob(cappedConfig, Instant.now());

        List<UUID> candidates = jobRepository.findQueuedIdsOldestFirst(10);

        assertThat(candidates).doesNotContain(queuedOnCappedConfig);
    }

    @Test
    @DisplayName("a job with no config (config_id NULL) is always a candidate")
    void configLessJobIsAlwaysACandidate() {
        AgentJob job = new AgentJob();
        job.setWorkspace(workspace);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setStatus(AgentJobStatus.QUEUED);
        job.setConfigSnapshot(objectMapper.createObjectNode());
        UUID jobId = jobRepository.saveAndFlush(job).getId();

        assertThat(jobRepository.findQueuedIdsOldestFirst(10)).contains(jobId);
    }

    // ── helpers ──

    private AgentConfig agentConfig(String name, int maxConcurrentJobs) {
        AgentConfig config = new AgentConfig();
        config.setWorkspace(workspace);
        config.setName(name);
        config.setEnabled(true);
        config.setLlmProvider(LlmProvider.ANTHROPIC);
        config.setTimeoutSeconds(300);
        config.setMaxConcurrentJobs(maxConcurrentJobs);
        return configRepository.saveAndFlush(config);
    }

    private UUID runningJob(AgentConfig config) {
        AgentJob job = new AgentJob();
        job.setWorkspace(workspace);
        job.setConfig(config);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setStatus(AgentJobStatus.RUNNING);
        job.setStartedAt(Instant.now());
        job.setWorkerId("some-worker");
        job.setConfigSnapshot(objectMapper.createObjectNode());
        return jobRepository.saveAndFlush(job).getId();
    }

    private UUID queuedJob(AgentConfig config, Instant createdAt) {
        AgentJob job = new AgentJob();
        job.setWorkspace(workspace);
        job.setConfig(config);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setStatus(AgentJobStatus.QUEUED);
        job.setConfigSnapshot(objectMapper.createObjectNode());
        // @PrePersist only stamps createdAt when it is still null, so setting it up front here (before
        // the first save/flush, i.e. before @PrePersist fires) lets this fixture control ordering exactly
        // — created_at is `updatable=false`, so a second UPDATE after the initial INSERT would silently
        // no-op instead of changing it.
        job.setCreatedAt(createdAt);
        return jobRepository.saveAndFlush(job).getId();
    }
}
