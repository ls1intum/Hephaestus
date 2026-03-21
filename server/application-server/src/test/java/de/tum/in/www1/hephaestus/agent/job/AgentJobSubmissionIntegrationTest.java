package de.tum.in.www1.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.AgentJobType;
import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.agent.config.AgentConfig;
import de.tum.in.www1.hephaestus.agent.config.AgentConfigRepository;
import de.tum.in.www1.hephaestus.agent.handler.PullRequestReviewSubmissionRequest;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.events.RepositoryRef;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.testconfig.TestUserFactory;
import de.tum.in.www1.hephaestus.testconfig.WorkspaceTestFactory;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * Integration test for {@link AgentJobService#submit} exercising real PostgreSQL
 * idempotency (partial unique index), config snapshot capture, and event publication.
 */
@DisplayName("AgentJobService submission integration")
@RecordApplicationEvents
class AgentJobSubmissionIntegrationTest extends BaseIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private AgentJobService agentJobService;

    @Autowired
    private AgentJobRepository agentJobRepository;

    @Autowired
    private AgentConfigRepository agentConfigRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private GitProviderRepository gitProviderRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApplicationEvents applicationEvents;

    private Workspace workspace;
    private AgentConfig agentConfig;
    private Long prId;
    private Repository repo;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();

        workspace = workspaceRepository.save(WorkspaceTestFactory.activeWorkspace("submit-test"));

        agentConfig = new AgentConfig();
        agentConfig.setWorkspace(workspace);
        agentConfig.setName("test-config");
        agentConfig.setEnabled(true);
        agentConfig.setAgentType(AgentType.CLAUDE_CODE);
        agentConfig.setLlmProvider(LlmProvider.ANTHROPIC);
        agentConfig.setCredentialMode(CredentialMode.PROXY);
        agentConfig.setTimeoutSeconds(300);
        agentConfig = agentConfigRepository.save(agentConfig);

        // Seed git entities needed for the submission request
        GitProvider provider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITHUB, "https://github.com")
            .orElseGet(() -> gitProviderRepository.save(new GitProvider(GitProviderType.GITHUB, "https://github.com")));

        User author = TestUserFactory.createUser(300L, "pr-author", provider);
        author = userRepository.save(author);

        repo = new Repository();
        repo.setNativeId(2001L);
        repo.setProvider(provider);
        repo.setName("submit-repo");
        repo.setNameWithOwner("org/submit-repo");
        repo.setHtmlUrl("https://github.com/org/submit-repo");
        repo.setDefaultBranch("main");
        repo = repositoryRepository.save(repo);

        Instant now = Instant.now();
        pullRequestRepository.upsertCore(
            6001L,
            provider.getId(),
            10,
            "Submit Test PR",
            "Body",
            "OPEN",
            null,
            "https://github.com/org/submit-repo/pull/10",
            false,
            null,
            0,
            now,
            now,
            now,
            author.getId(),
            repo.getId(),
            null,
            null,
            false,
            false,
            1,
            5,
            3,
            2,
            null,
            null,
            null,
            "feature/submit",
            "main",
            "sha1abc",
            "sha1base",
            null
        );
        prId = pullRequestRepository.findByRepositoryIdAndNumber(repo.getId(), 10).orElseThrow().getId();
    }

    private PullRequestReviewSubmissionRequest createRequest(String commitSha) {
        RepositoryRef repoRef = new RepositoryRef(repo.getId(), repo.getNameWithOwner(), repo.getDefaultBranch());
        EventPayload.PullRequestData prData = new EventPayload.PullRequestData(
            prId,
            10,
            "Submit Test PR",
            "Body",
            PullRequest.State.OPEN,
            false,
            false,
            5,
            3,
            2,
            repo.getHtmlUrl() + "/pull/10",
            repoRef,
            null,
            null,
            null,
            null,
            null,
            null
        );
        return new PullRequestReviewSubmissionRequest(prData, "feature/submit", commitSha, repo.getDefaultBranch());
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("submits job with correct metadata, status, and config snapshot")
        void submitsJobWithCorrectState() {
            var request = createRequest("abc123");

            Optional<AgentJob> result = agentJobService.submit(
                workspace.getId(),
                AgentJobType.PULL_REQUEST_REVIEW,
                request
            );

            assertThat(result).isPresent();
            AgentJob job = result.get();
            assertThat(job.getStatus()).isEqualTo(AgentJobStatus.QUEUED);
            assertThat(job.getJobType()).isEqualTo(AgentJobType.PULL_REQUEST_REVIEW);
            assertThat(job.getIdempotencyKey()).isEqualTo("pr_review:org/submit-repo:10:abc123");
            assertThat(job.getConfigSnapshot()).isNotNull();
            assertThat(job.getMetadata().get("pull_request_id").asLong()).isEqualTo(prId);
            assertThat(job.getMetadata().get("pr_number").asInt()).isEqualTo(10);
            assertThat(job.getMetadata().get("commit_sha").asText()).isEqualTo("abc123");

            // Verify persisted in DB
            AgentJob fromDb = agentJobRepository.findById(job.getId()).orElseThrow();
            assertThat(fromDb.getStatus()).isEqualTo(AgentJobStatus.QUEUED);
        }

        @Test
        @DisplayName("publishes AgentJobCreatedEvent with correct fields")
        void publishesAgentJobCreatedEvent() {
            var request = createRequest("event123");

            Optional<AgentJob> result = agentJobService.submit(
                workspace.getId(),
                AgentJobType.PULL_REQUEST_REVIEW,
                request
            );

            assertThat(result).isPresent();
            List<AgentJobCreatedEvent> events = applicationEvents.stream(AgentJobCreatedEvent.class).toList();
            assertThat(events).hasSize(1);
            assertThat(events.get(0).jobId()).isEqualTo(result.get().getId());
            assertThat(events.get(0).workspaceId()).isEqualTo(workspace.getId());
        }
    }

    @Nested
    @DisplayName("Idempotency")
    class Idempotency {

        @Test
        @DisplayName("deduplicates submissions with same commit SHA")
        void deduplicatesSameCommitSha() {
            var request = createRequest("dedup123");

            Optional<AgentJob> first = agentJobService.submit(
                workspace.getId(),
                AgentJobType.PULL_REQUEST_REVIEW,
                request
            );
            Optional<AgentJob> second = agentJobService.submit(
                workspace.getId(),
                AgentJobType.PULL_REQUEST_REVIEW,
                request
            );

            assertThat(first).isPresent();
            assertThat(second).isPresent();
            assertThat(second.get().getId()).isEqualTo(first.get().getId());

            // Only 1 row in DB
            assertThat(agentJobRepository.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("allows different commit SHAs as separate jobs")
        void allowsDifferentCommitSha() {
            var request1 = createRequest("sha_v1");
            var request2 = createRequest("sha_v2");

            Optional<AgentJob> first = agentJobService.submit(
                workspace.getId(),
                AgentJobType.PULL_REQUEST_REVIEW,
                request1
            );
            Optional<AgentJob> second = agentJobService.submit(
                workspace.getId(),
                AgentJobType.PULL_REQUEST_REVIEW,
                request2
            );

            assertThat(first).isPresent();
            assertThat(second).isPresent();
            assertThat(second.get().getId()).isNotEqualTo(first.get().getId());

            assertThat(agentJobRepository.findAll()).hasSize(2);
        }

        @Test
        @DisplayName("deduplicates against pre-existing job with same idempotency key")
        void deduplicatesAgainstPreExistingJob() {
            // Pre-insert a QUEUED job with the same idempotency key via raw repository,
            // simulating a job created by a concurrent request before this one.
            AgentJob existing = new AgentJob();
            existing.setWorkspace(workspace);
            existing.setConfig(agentConfig);
            existing.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
            existing.setIdempotencyKey("pr_review:" + repo.getNameWithOwner() + ":10:race123");
            existing.setMetadata(OBJECT_MAPPER.createObjectNode());
            existing.setConfigSnapshot(OBJECT_MAPPER.createObjectNode());
            agentJobRepository.saveAndFlush(existing);

            // Submit through service — application-level check finds the existing job
            var request = createRequest("race123");
            Optional<AgentJob> result = agentJobService.submit(
                workspace.getId(),
                AgentJobType.PULL_REQUEST_REVIEW,
                request
            );

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(existing.getId());
        }
    }

    @Nested
    @DisplayName("No config")
    class NoConfig {

        @Test
        @DisplayName("returns empty when no agent config exists")
        void returnsEmptyWhenNoConfig() {
            agentConfigRepository.deleteAll();

            var request = createRequest("noconfig123");
            Optional<AgentJob> result = agentJobService.submit(
                workspace.getId(),
                AgentJobType.PULL_REQUEST_REVIEW,
                request
            );

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when config is disabled")
        void returnsEmptyWhenDisabled() {
            agentConfig.setEnabled(false);
            agentConfigRepository.save(agentConfig);

            var request = createRequest("disabled123");
            Optional<AgentJob> result = agentJobService.submit(
                workspace.getId(),
                AgentJobType.PULL_REQUEST_REVIEW,
                request
            );

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Config snapshot")
    class ConfigSnapshot {

        @Test
        @DisplayName("captures config snapshot at submit time, immune to later changes")
        void capturesSnapshotAtSubmitTime() {
            var request = createRequest("snapshot123");

            Optional<AgentJob> result = agentJobService.submit(
                workspace.getId(),
                AgentJobType.PULL_REQUEST_REVIEW,
                request
            );

            assertThat(result).isPresent();
            var snapshot = result.get().getConfigSnapshot();
            assertThat(snapshot.get("agentType").asText()).isEqualTo("CLAUDE_CODE");
            assertThat(snapshot.get("timeoutSeconds").asInt()).isEqualTo(300);

            // Change the config after submission
            agentConfig.setTimeoutSeconds(900);
            agentConfig.setAgentType(AgentType.OPENCODE);
            agentConfigRepository.save(agentConfig);

            // Re-load the job from DB — snapshot should still reflect the original
            AgentJob fromDb = agentJobRepository.findById(result.get().getId()).orElseThrow();
            assertThat(fromDb.getConfigSnapshot().get("agentType").asText()).isEqualTo("CLAUDE_CODE");
            assertThat(fromDb.getConfigSnapshot().get("timeoutSeconds").asInt()).isEqualTo(300);
        }
    }
}
