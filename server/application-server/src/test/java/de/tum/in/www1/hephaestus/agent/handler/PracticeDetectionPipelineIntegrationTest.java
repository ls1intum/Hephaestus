package de.tum.in.www1.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import de.tum.in.www1.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.in.www1.hephaestus.agent.job.AgentJob;
import de.tum.in.www1.hephaestus.agent.job.AgentJobRepository;
import de.tum.in.www1.hephaestus.agent.job.AgentJobStatus;
import de.tum.in.www1.hephaestus.agent.job.DeliveryStatus;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.practices.PracticeRepository;
import de.tum.in.www1.hephaestus.practices.finding.PracticeDetectionCompletedEvent;
import de.tum.in.www1.hephaestus.practices.finding.PracticeFindingRepository;
import de.tum.in.www1.hephaestus.practices.model.Practice;
import de.tum.in.www1.hephaestus.practices.model.PracticeFinding;
import de.tum.in.www1.hephaestus.practices.model.Verdict;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.testconfig.TestUserFactory;
import de.tum.in.www1.hephaestus.testconfig.WorkspaceTestFactory;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * Full pipeline integration test: handler.deliver() → result parser → finding persistence
 * → feedback delivery. Exercises real PostgreSQL for finding idempotency (ON CONFLICT DO NOTHING)
 * and event publication.
 *
 * <p><b>Mock boundary:</b> {@link PullRequestCommentPoster} and {@link DiffNotePoster} are mocked
 * because they make external API calls to GitHub/GitLab. All other components (result parser,
 * delivery service, feedback service, finding repository) are real beans against PostgreSQL.
 */
@DisplayName("Practice detection pipeline integration")
@RecordApplicationEvents
class PracticeDetectionPipelineIntegrationTest extends BaseIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private JobTypeHandlerRegistry handlerRegistry;

    @Autowired
    private PracticeFindingRepository practiceFindingRepository;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private AgentJobRepository agentJobRepository;

    @Autowired
    private AgentConfigRepository agentConfigRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private GitProviderRepository gitProviderRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private ApplicationEvents applicationEvents;

    @MockitoBean
    private PullRequestCommentPoster commentPoster;

    @MockitoBean
    private DiffNotePoster diffNotePoster;

    private JobTypeHandler handler;
    private Workspace workspace;
    private AgentJob agentJob;
    private Long prId;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();

        workspace = workspaceRepository.save(WorkspaceTestFactory.activeWorkspace("pipeline-test"));

        createPractice("pr-description-quality", "PR Description Quality");
        createPractice("error-handling", "Error Handling");

        AgentConfig config = new AgentConfig();
        config.setWorkspace(workspace);
        config.setName("pipeline-config");
        config.setEnabled(true);
        config.setAgentType(AgentType.CLAUDE_CODE);
        config.setLlmProvider(LlmProvider.ANTHROPIC);
        config.setCredentialMode(CredentialMode.PROXY);
        config.setTimeoutSeconds(300);
        config = agentConfigRepository.save(config);

        // Git entities
        GitProvider provider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITHUB, "https://github.com")
            .orElseGet(() -> gitProviderRepository.save(new GitProvider(GitProviderType.GITHUB, "https://github.com")));

        User contributor = TestUserFactory.createUser(500L, "pipeline-author", provider);
        contributor = userRepository.save(contributor);

        Repository repo = new Repository();
        repo.setNativeId(4001L);
        repo.setProvider(provider);
        repo.setName("pipeline-repo");
        repo.setNameWithOwner("org/pipeline-repo");
        repo.setHtmlUrl("https://github.com/org/pipeline-repo");
        repo.setDefaultBranch("main");
        repo = repositoryRepository.save(repo);

        Instant now = Instant.now();
        pullRequestRepository.upsertCore(
            8001L,
            provider.getId(),
            50,
            "Pipeline Test PR",
            "Test body",
            "OPEN",
            null,
            "https://github.com/org/pipeline-repo/pull/50",
            false,
            null,
            0,
            now,
            now,
            now,
            contributor.getId(),
            repo.getId(),
            null,
            null,
            false,
            false,
            1,
            10,
            5,
            3,
            null,
            null,
            null,
            "feature/pipeline",
            "main",
            "pipelinesha",
            "basesha",
            null
        );
        prId = pullRequestRepository.findByRepositoryIdAndNumber(repo.getId(), 50).orElseThrow().getId();

        // Create agent job with metadata
        agentJob = new AgentJob();
        agentJob.setWorkspace(workspace);
        agentJob.setConfig(config);
        agentJob.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        agentJob.setStatus(AgentJobStatus.COMPLETED);
        agentJob.setConfigSnapshot(
            OBJECT_MAPPER.valueToTree(Map.of("model", "claude-3.5", "agentType", "CLAUDE_CODE"))
        );

        ObjectNode metadata = OBJECT_MAPPER.createObjectNode();
        metadata.put("pull_request_id", prId);
        metadata.put("repository_id", repo.getId());
        metadata.put("repository_full_name", "org/pipeline-repo");
        metadata.put("pr_number", 50);
        metadata.put("pr_url", "https://github.com/org/pipeline-repo/pull/50");
        metadata.put("commit_sha", "pipelinesha");
        metadata.put("source_branch", "feature/pipeline");
        metadata.put("target_branch", "main");
        agentJob.setMetadata(metadata);
        agentJob = agentJobRepository.save(agentJob);

        handler = handlerRegistry.getHandler(AgentJobType.PULL_REQUEST_REVIEW);
    }

    private Practice createPractice(String slug, String name) {
        Practice p = new Practice();
        p.setWorkspace(workspace);
        p.setSlug(slug);
        p.setName(name);
        p.setCategory("test");
        p.setDescription("Test " + slug);
        p.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("PullRequestCreated")));
        return practiceRepository.save(p);
    }

    private void setJobOutput(String rawOutput) {
        ObjectNode output = OBJECT_MAPPER.createObjectNode();
        output.put("rawOutput", rawOutput);
        agentJob.setOutput(output);
        agentJob = agentJobRepository.save(agentJob);
    }

    private String validAgentOutput(boolean includeDelivery) {
        String findings = """
            {
              "findings": [
                {
                  "practiceSlug": "pr-description-quality",
                  "title": "Good PR description",
                  "verdict": "POSITIVE",
                  "severity": "INFO",
                  "confidence": 0.95
                },
                {
                  "practiceSlug": "error-handling",
                  "title": "Missing null check",
                  "verdict": "NEGATIVE",
                  "severity": "MAJOR",
                  "confidence": 0.85,
                  "reasoning": "The method does not check for null input.",
                  "guidance": "Add a null check at the top of the method."
                }
              ]""";

        if (includeDelivery) {
            return (
                findings +
                """
                  ,
                  "delivery": {
                    "mrNote": "## Practice Review\\nPlease add null checks for safety.",
                    "diffNotes": [{
                      "filePath": "src/Main.java",
                      "startLine": 10,
                      "endLine": 15,
                      "body": "Consider adding a null check here."
                    }]
                  }
                }"""
            );
        }
        return findings + "\n}";
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("full pipeline: parse → persist findings → publish event → post feedback")
        void fullPipelineFromParseToDelivery() {
            setJobOutput(validAgentOutput(true));
            when(commentPoster.postPracticeNote(any(), any(), isNull())).thenReturn("comment-123");
            when(diffNotePoster.postDiffNotes(any(), any())).thenReturn(new DiffNotePoster.DiffNoteResult(1, 0));

            handler.deliver(agentJob);

            // Verify findings persisted
            List<PracticeFinding> findings = practiceFindingRepository.findAll();
            assertThat(findings).hasSize(2);
            assertThat(findings)
                .extracting(PracticeFinding::getVerdict)
                .containsExactlyInAnyOrder(Verdict.POSITIVE, Verdict.NEGATIVE);

            // Verify completion event
            List<PracticeDetectionCompletedEvent> events = applicationEvents
                .stream(PracticeDetectionCompletedEvent.class)
                .toList();
            assertThat(events).hasSize(1);
            assertThat(events.get(0).findingsInserted()).isEqualTo(2);
            assertThat(events.get(0).hasNegative()).isTrue();

            // Verify comment poster called with mrNote
            verify(commentPoster).postPracticeNote(eq(agentJob), any(String.class), isNull());

            // Verify diff notes posted (has negative findings + first analysis)
            verify(diffNotePoster).postDiffNotes(eq(agentJob), any());

            // Verify delivery status set on in-memory object
            // (DB persistence happens in AgentJobExecutor, not in handler.deliver())
            assertThat(agentJob.getDeliveryCommentId()).isEqualTo("comment-123");
            assertThat(agentJob.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        }

        @Test
        @DisplayName("all-positive findings: no comment or diff notes posted")
        void allPositiveFindingsNoComment() {
            String output = """
                {
                  "findings": [
                    {
                      "practiceSlug": "pr-description-quality",
                      "title": "Good description",
                      "verdict": "POSITIVE",
                      "severity": "INFO",
                      "confidence": 0.9
                    },
                    {
                      "practiceSlug": "error-handling",
                      "title": "Proper error handling",
                      "verdict": "POSITIVE",
                      "severity": "INFO",
                      "confidence": 0.9
                    }
                  ]
                }""";
            setJobOutput(output);

            handler.deliver(agentJob);

            // Findings still persisted
            assertThat(practiceFindingRepository.findAll()).hasSize(2);

            // No comment or diff notes posted (no negatives, no delivery content)
            verify(commentPoster, never()).postPracticeNote(any(), any(), any());
            verify(diffNotePoster, never()).postDiffNotes(any(), any());
        }
    }

    @Nested
    @DisplayName("Error cases")
    class ErrorCases {

        @Test
        @DisplayName("invalid JSON output throws JobDeliveryException with no findings")
        void invalidJsonOutputFailsGracefully() {
            setJobOutput("this is not valid JSON at all");

            assertThatThrownBy(() -> handler.deliver(agentJob))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("No valid findings");

            assertThat(practiceFindingRepository.findAll()).isEmpty();
            verify(commentPoster, never()).postPracticeNote(any(), any(), any());
        }

        @Test
        @DisplayName("unknown practice slug discarded, valid findings persisted")
        void unknownSlugDiscardedOthersKept() {
            String output = """
                {
                  "findings": [
                    {
                      "practiceSlug": "pr-description-quality",
                      "title": "Good description",
                      "verdict": "POSITIVE",
                      "severity": "INFO",
                      "confidence": 0.9
                    },
                    {
                      "practiceSlug": "nonexistent-practice",
                      "title": "Unknown practice",
                      "verdict": "POSITIVE",
                      "severity": "INFO",
                      "confidence": 0.9
                    },
                    {
                      "practiceSlug": "error-handling",
                      "title": "Good handling",
                      "verdict": "NEGATIVE",
                      "severity": "MINOR",
                      "confidence": 0.8
                    }
                  ],
                  "delivery": { "mrNote": "Fix error handling." }
                }""";
            setJobOutput(output);
            when(commentPoster.postPracticeNote(any(), any(), isNull())).thenReturn("comment-456");

            handler.deliver(agentJob);

            // 2 persisted (known slugs), 1 discarded (unknown)
            List<PracticeFinding> findings = practiceFindingRepository.findAll();
            assertThat(findings).hasSize(2);

            List<PracticeDetectionCompletedEvent> events = applicationEvents
                .stream(PracticeDetectionCompletedEvent.class)
                .toList();
            assertThat(events).hasSize(1);
            assertThat(events.get(0).findingsInserted()).isEqualTo(2);
            assertThat(events.get(0).findingsDiscarded()).isEqualTo(1);
        }

        @Test
        @DisplayName("closed PR: findings persisted but no comment posted")
        void closedPrSkipsDelivery() {
            // Update PR state to CLOSED
            var pr = pullRequestRepository.findById(prId).orElseThrow();
            pullRequestRepository.upsertCore(
                8001L,
                pr.getProvider().getId(),
                50,
                "Pipeline Test PR",
                "Test body",
                "CLOSED",
                null,
                "https://github.com/org/pipeline-repo/pull/50",
                false,
                null,
                0,
                pr.getCreatedAt(),
                Instant.now(),
                pr.getCreatedAt(),
                pr.getAuthor().getId(),
                pr.getRepository().getId(),
                null,
                null,
                false,
                false,
                1,
                10,
                5,
                3,
                null,
                null,
                null,
                "feature/pipeline",
                "main",
                "pipelinesha",
                "basesha",
                null
            );

            setJobOutput(validAgentOutput(true));

            handler.deliver(agentJob);

            // Findings ARE persisted (deliver() persists first, then posts)
            assertThat(practiceFindingRepository.findAll()).hasSize(2);

            // Comment NOT posted (FeedbackDeliveryService skips closed PRs)
            verify(commentPoster, never()).postPracticeNote(any(), any(), any());
            verify(diffNotePoster, never()).postDiffNotes(any(), any());

            // Delivery status not set (feedback was skipped)
            assertThat(agentJob.getDeliveryCommentId()).isNull();
            assertThat(agentJob.getDeliveryStatus()).isNull();
        }
    }

    @Nested
    @DisplayName("Finding idempotency")
    class FindingIdempotency {

        @Test
        @DisplayName("re-delivering same job creates no duplicate findings")
        void redeliveryNoDuplicates() {
            setJobOutput(validAgentOutput(true));
            when(commentPoster.postPracticeNote(any(), any(), any())).thenReturn("comment-789");
            when(diffNotePoster.postDiffNotes(any(), any())).thenReturn(new DiffNotePoster.DiffNoteResult(1, 0));

            // First delivery
            handler.deliver(agentJob);
            assertThat(practiceFindingRepository.findAll()).hasSize(2);

            // Second delivery — same job, same output
            handler.deliver(agentJob);

            // Still only 2 findings (ON CONFLICT DO NOTHING)
            assertThat(practiceFindingRepository.findAll()).hasSize(2);

            // Event published twice (once per deliver() call)
            List<PracticeDetectionCompletedEvent> events = applicationEvents
                .stream(PracticeDetectionCompletedEvent.class)
                .toList();
            assertThat(events).hasSize(2);
            assertThat(events.get(0).findingsInserted()).isEqualTo(2);
            assertThat(events.get(1).findingsInserted()).isZero();
            assertThat(events.get(1).findingsDiscarded()).isEqualTo(2); // duplicates
        }
    }
}
