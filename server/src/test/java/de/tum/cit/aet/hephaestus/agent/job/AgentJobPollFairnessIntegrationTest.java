package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmConnection;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmConnectionRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBinding;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBindingRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.LlmCatalogTestFixtures;
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
 * Head-of-line starvation fix for the poll candidate query. A plain {@code WHERE status='QUEUED' ORDER
 * BY created_at LIMIT n} has no awareness of per-bucket concurrency caps: if the oldest {@code n}
 * QUEUED rows all belong to a {@code (workspace, purpose)} already saturated on RUNNING jobs, a
 * younger, immediately-runnable job in a different bucket never enters the candidate batch at all.
 */
@DisplayName("Poll candidate fairness")
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
    private WorkspaceAgentBindingRepository bindingRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private LlmConnectionRepository llmConnectionRepository;

    @Autowired
    private LlmModelRepository llmModelRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private LlmModel instanceModel;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();

        LlmConnection connection = llmConnectionRepository.save(LlmCatalogTestFixtures.connection("poll-fairness"));
        instanceModel = llmModelRepository.save(
            LlmCatalogTestFixtures.model(connection, "poll-fairness-model", "gpt-poll-fairness")
        );
    }

    @Test
    @DisplayName(
        "a saturated (workspace, purpose)'s older QUEUED backlog does not starve a younger, runnable job from another workspace"
    )
    void youngerRunnableJobFromAnotherWorkspaceIsNotStarved() {
        Workspace cappedWs = activeWorkspace("capped-ws");
        Workspace openWs = activeWorkspace("open-ws");
        binding(cappedWs, 1);
        binding(openWs, 3);

        runningJob(cappedWs);

        Instant base = Instant.now().minus(1, ChronoUnit.HOURS);
        for (int i = 0; i < 5; i++) {
            queuedJob(cappedWs, base.plusSeconds(i));
        }

        // A younger job on the OPEN workspace — created after all 5 capped candidates, so a naive
        // oldest-first LIMIT 5 query would never even fetch it.
        UUID youngerRunnableJobId = queuedJob(openWs, base.plusSeconds(100));

        List<UUID> candidates = jobRepository.findQueuedIdsOldestFirst(5);

        assertThat(candidates)
            .as(
                "the open workspace's runnable job must be a poll candidate even though 5 older, unclaimable " +
                    "jobs exist ahead of it"
            )
            .contains(youngerRunnableJobId);
    }

    @Test
    @DisplayName("a (workspace, purpose) at its concurrency cap contributes no candidates at all")
    void cappedWorkspaceContributesNoCandidates() {
        Workspace cappedWs = activeWorkspace("fully-capped-ws");
        binding(cappedWs, 1);
        runningJob(cappedWs);
        UUID queuedOnCapped = queuedJob(cappedWs, Instant.now());

        List<UUID> candidates = jobRepository.findQueuedIdsOldestFirst(10);

        assertThat(candidates).doesNotContain(queuedOnCapped);
    }

    @Test
    @DisplayName("a job whose (workspace, purpose) binding is gone is treated as uncapped and stays a candidate")
    void jobWithNoBindingIsAlwaysACandidate() {
        Workspace ws = activeWorkspace("no-binding-ws");
        // No binding row for this workspace/purpose — the correlated cap lookup returns NULL, which
        // COALESCEs to "uncapped", so the job is still fetched (the claim's admission re-check is the
        // authoritative gate that will reject an unbound job — it must not silently starve siblings here).
        UUID jobId = queuedJob(ws, Instant.now());

        assertThat(jobRepository.findQueuedIdsOldestFirst(10)).contains(jobId);
    }

    private Workspace activeWorkspace(String slug) {
        return workspaceRepository.save(TestEntities.activeWorkspace(slug));
    }

    private void binding(Workspace workspace, int maxConcurrentJobs) {
        WorkspaceAgentBinding binding = new WorkspaceAgentBinding();
        binding.setWorkspace(workspace);
        binding.setPurpose(AgentPurpose.PRACTICE_DETECTION);
        binding.setInstanceModel(instanceModel);
        binding.setEnabled(true);
        binding.setMaxConcurrentJobs(maxConcurrentJobs);
        bindingRepository.saveAndFlush(binding);
    }

    private UUID runningJob(Workspace workspace) {
        AgentJob job = new AgentJob();
        job.setWorkspace(workspace);
        job.setPurpose(AgentPurpose.PRACTICE_DETECTION);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setStatus(AgentJobStatus.RUNNING);
        job.setStartedAt(Instant.now());
        job.setWorkerId("some-worker");
        job.setConfigSnapshot(objectMapper.createObjectNode());
        return jobRepository.saveAndFlush(job).getId();
    }

    private UUID queuedJob(Workspace workspace, Instant createdAt) {
        AgentJob job = new AgentJob();
        job.setWorkspace(workspace);
        job.setPurpose(AgentPurpose.PRACTICE_DETECTION);
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
