package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.CredentialMode;
import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfig;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfigRepository;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobStatus;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.finding.PracticeDetectionCompletedEvent;
import de.tum.cit.aet.hephaestus.practices.finding.PracticeFindingRepository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeFinding;
import de.tum.cit.aet.hephaestus.practices.model.Verdict;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.TestUserFactory;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
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
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Full pipeline integration test: handler.deliver() → result parser → finding persistence
 * → feedback delivery. Exercises real PostgreSQL for finding idempotency (ON CONFLICT DO NOTHING)
 * and event publication.
 *
 * <p><b>Mock boundary:</b> {@link PullRequestCommentPoster} and {@link DiffNotePoster} are mocked
 * because they make external API calls to GitHub/GitLab. All other components (result parser,
 * delivery service, feedback service, finding repository) are real beans against PostgreSQL.
 */
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

        workspace = workspaceRepository.save(WorkspaceTestFixtures.activeWorkspace("pipeline-test"));

        createPractice("pr-description-quality", "PR Description Quality");
        createPractice("error-handling", "Error Handling");

        AgentConfig config = new AgentConfig();
        config.setWorkspace(workspace);
        config.setName("pipeline-config");
        config.setEnabled(true);
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
            null,
            null // mergeCommitSha
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
        p.setCriteria("Test " + slug);
        p.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("PullRequestCreated")));
        return practiceRepository.save(p);
    }

    private void setJobOutput(String rawOutput) {
        ObjectNode output = OBJECT_MAPPER.createObjectNode();
        output.put("rawOutput", rawOutput);
        agentJob.setOutput(output);
        agentJob = agentJobRepository.save(agentJob);
    }

    private String validAgentOutput() {
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
                  "guidance": "Add a null check at the top of the method.",
                  "suggestedDiffNotes": [
                    { "filePath": "src/Main.java", "startLine": 10, "endLine": 15,
                      "body": "Consider adding a null check here." }
                  ]
                }
              ]""";
        return findings + "\n}";
    }

    @Nested
    class HappyPath {

        @Test
        void fullPipelineFromParseToDelivery() {
            setJobOutput(validAgentOutput());
            when(commentPoster.postFormattedBody(any(), any())).thenReturn("comment-123");
            when(diffNotePoster.reconcileInlineNotes(any(), any())).thenReturn(new DiffNotePoster.DiffNoteResult(1, 0));

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
            verify(commentPoster).postFormattedBody(eq(agentJob), any(String.class));

            // Verify diff notes posted (has negative findings + first analysis)
            verify(diffNotePoster).reconcileInlineNotes(eq(agentJob), any());

            // Verify delivery status set on in-memory object
            // (DB persistence happens in AgentJobExecutor, not in handler.deliver())
            assertThat(agentJob.getDeliveryCommentId()).isEqualTo("comment-123");
            // DeliveryStatus is set by AgentJobExecutor.persistDeliveryStatus(), not by handler.deliver()
            assertThat(agentJob.getDeliveryStatus()).isNull();
        }

        @Test
        void allPositiveFindingsPostsApproval() {
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
            when(commentPoster.postFormattedBody(any(), any())).thenReturn("comment-approval");

            handler.deliver(agentJob);

            // Findings still persisted
            assertThat(practiceFindingRepository.findAll()).hasSize(2);

            // Approval comment posted (no negatives → approval summary). Inline notes are reconciled
            // unconditionally on an OPEN PR (bb92f0010) with an EMPTY list — clearing any prior run's stale
            // line-numbered notes while posting no new ones.
            verify(commentPoster).postFormattedBody(any(), any());
            verify(diffNotePoster).reconcileInlineNotes(eq(agentJob), eq(java.util.List.of()));
        }
    }

    @Nested
    class ErrorCases {

        @Test
        void invalidJsonOutputFailsGracefully() {
            setJobOutput("this is not valid JSON at all");

            assertThatThrownBy(() -> handler.deliver(agentJob))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("No valid findings");

            assertThat(practiceFindingRepository.findAll()).isEmpty();
            verify(commentPoster, never()).postFormattedBody(any(), any());
        }

        @Test
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
                  ]
                }""";
            setJobOutput(output);
            when(commentPoster.postFormattedBody(any(), any())).thenReturn("comment-456");

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
                null,
                null // mergeCommitSha
            );

            setJobOutput(validAgentOutput());

            handler.deliver(agentJob);

            // Findings ARE persisted (deliver() persists first, then posts)
            assertThat(practiceFindingRepository.findAll()).hasSize(2);

            // Comment NOT posted (FeedbackDeliveryService skips closed PRs)
            verify(commentPoster, never()).postFormattedBody(any(), any());
            verify(diffNotePoster, never()).reconcileInlineNotes(any(), any());

            // Delivery status not set (feedback was skipped)
            assertThat(agentJob.getDeliveryCommentId()).isNull();
            assertThat(agentJob.getDeliveryStatus()).isNull();
        }
    }

    @Nested
    class FindingIdempotency {

        @Test
        @DisplayName("re-delivering same job creates no duplicate findings")
        void redeliveryNoDuplicates() {
            setJobOutput(validAgentOutput());
            when(commentPoster.postFormattedBody(any(), any())).thenReturn("comment-789");
            when(diffNotePoster.reconcileInlineNotes(any(), any())).thenReturn(new DiffNotePoster.DiffNoteResult(1, 0));

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
