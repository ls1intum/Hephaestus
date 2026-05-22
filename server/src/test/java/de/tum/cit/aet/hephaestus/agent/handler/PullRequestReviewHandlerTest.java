package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.WorkspaceContextBuilder;
import de.tum.cit.aet.hephaestus.agent.context.providers.GitDiffOperations;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionDeliveryService.DeliveryResult;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmission;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmissionRequest;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.task.TaskEnvelopeWriter;
import de.tum.cit.aet.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.cit.aet.hephaestus.gitprovider.common.events.RepositoryRef;
import de.tum.cit.aet.hephaestus.gitprovider.git.GitRepositoryManager;
import de.tum.cit.aet.hephaestus.gitprovider.issue.Issue;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.Verdict;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@DisplayName("PullRequestReviewHandler")
class PullRequestReviewHandlerTest extends BaseUnitTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Mock
    private GitRepositoryManager gitRepositoryManager;

    @Mock
    private PracticeRepository practiceRepository;

    @Mock
    private WorkspaceContextBuilder workspaceContextBuilder;

    @Mock
    private GitDiffOperations gitDiffOperations;

    @Mock
    private PracticeDetectionDeliveryService deliveryService;

    @Mock
    private FeedbackDeliveryService feedbackService;

    private static final Long WORKSPACE_ID = 99L;

    private PracticeDetectionResultParser resultParser;
    private TaskEnvelopeWriter taskEnvelopeWriter;
    private PullRequestReviewHandler handler;

    @BeforeEach
    void setUp() {
        resultParser = new PracticeDetectionResultParser(objectMapper);
        taskEnvelopeWriter = new TaskEnvelopeWriter(objectMapper);
        handler = new PullRequestReviewHandler(
            objectMapper,
            gitRepositoryManager,
            practiceRepository,
            workspaceContextBuilder,
            taskEnvelopeWriter,
            gitDiffOperations,
            resultParser,
            deliveryService,
            feedbackService
        );
    }

    private PullRequestReviewSubmissionRequest sampleRequest() {
        var pullRequestData = new EventPayload.PullRequestData(
            456L,
            42,
            "Fix authentication bug",
            "This PR fixes the login issue",
            Issue.State.OPEN,
            false,
            false,
            10,
            5,
            3,
            "https://github.com/owner/repo/pull/42",
            new RepositoryRef(123L, "owner/repo", "main"),
            789L,
            Instant.now(),
            Instant.now(),
            null,
            null,
            null
        );
        return new PullRequestReviewSubmissionRequest(pullRequestData, "feature/auth-fix", "abc123def456", "main");
    }

    private ObjectNode sampleJobMetadata() {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("repository_id", 123L);
        metadata.put("repository_full_name", "owner/repo");
        metadata.put("pull_request_id", 456L);
        metadata.put("pr_number", 42);
        metadata.put("pr_url", "https://github.com/owner/repo/pull/42");
        metadata.put("commit_sha", "abc123def456");
        metadata.put("source_branch", "feature/auth-fix");
        metadata.put("target_branch", "main");
        return metadata;
    }

    private AgentJob jobWithMetadata(ObjectNode metadata) {
        var job = new AgentJob();
        job.setId(UUID.randomUUID());
        job.setMetadata(metadata);
        Workspace workspace = new Workspace();
        workspace.setId(WORKSPACE_ID);
        job.setWorkspace(workspace);
        return job;
    }

    private Practice createPractice(String slug, String name, String criteria) {
        Practice p = new Practice();
        p.setId((long) slug.hashCode());
        p.setSlug(slug);
        p.setName(name);
        p.setCriteria(criteria);
        p.setActive(true);
        return p;
    }

    private List<Practice> samplePractices() {
        return List.of(
            createPractice("pr-description-quality", "PR Description Quality", "criteria"),
            createPractice("error-handling", "Error Handling", "fallback criteria")
        );
    }

    /** Stub the workspace context build + practice catalog to return minimal valid data. */
    private void stubDefaults() {
        lenient()
            .when(workspaceContextBuilder.build(any(ContextRequest.PracticeReviewRequest.class)))
            .thenReturn(
                new LinkedHashMap<>(Map.of("context/target/metadata.json", "{}".getBytes(StandardCharsets.UTF_8)))
            );
        lenient().when(practiceRepository.findByWorkspaceIdAndActiveTrue(WORKSPACE_ID)).thenReturn(samplePractices());
    }

    @Nested
    @DisplayName("jobType")
    class JobType {

        @Test
        @DisplayName("returns PULL_REQUEST_REVIEW")
        void returnsPullRequestReview() {
            assertThat(handler.jobType()).isEqualTo(AgentJobType.PULL_REQUEST_REVIEW);
        }
    }

    @Nested
    @DisplayName("createSubmission")
    class CreateSubmission {

        @Test
        @DisplayName("extracts metadata + idempotency key")
        void extractsMetadata() {
            JobSubmission submission = handler.createSubmission(sampleRequest());
            JsonNode metadata = submission.metadata();

            assertThat(metadata.get("repository_id").asLong()).isEqualTo(123L);
            assertThat(metadata.get("repository_full_name").asText()).isEqualTo("owner/repo");
            assertThat(metadata.get("pr_number").asInt()).isEqualTo(42);
            assertThat(metadata.get("commit_sha").asText()).isEqualTo("abc123def456");
            assertThat(submission.idempotencyKey()).isEqualTo("pr_review:owner/repo:42:abc123def456");
        }

        @Test
        @DisplayName("rejects wrong request type")
        void rejectsWrongRequestType() {
            JobSubmissionRequest wrongType = new JobSubmissionRequest() {};
            assertThatThrownBy(() -> handler.createSubmission(wrongType))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected PullRequestReviewSubmissionRequest");
        }
    }

    @Nested
    @DisplayName("prepareInputFiles")
    class PrepareInputFiles {

        @Test
        @DisplayName("delegates to WorkspaceContextBuilder with a PracticeReviewRequest")
        void delegatesToWorkspaceContextBuilder() {
            stubDefaults();
            AgentJob job = jobWithMetadata(sampleJobMetadata());

            handler.prepareInputFiles(job);

            ArgumentCaptor<ContextRequest> captor = ArgumentCaptor.forClass(ContextRequest.class);
            verify(workspaceContextBuilder).build(captor.capture());
            assertThat(captor.getValue()).isInstanceOf(ContextRequest.PracticeReviewRequest.class);
            assertThat(((ContextRequest.PracticeReviewRequest) captor.getValue()).job()).isSameAs(job);
        }

        @Test
        @DisplayName("merges context/target/* files from the builder into the result map")
        void mergesProviderFiles() {
            byte[] metadataBytes = "{\"pr_number\":42}".getBytes(StandardCharsets.UTF_8);
            when(workspaceContextBuilder.build(any(ContextRequest.PracticeReviewRequest.class))).thenReturn(
                new LinkedHashMap<>(Map.of("context/target/metadata.json", metadataBytes))
            );
            when(practiceRepository.findByWorkspaceIdAndActiveTrue(WORKSPACE_ID)).thenReturn(samplePractices());

            Map<String, byte[]> files = handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata()));

            assertThat(files.get("context/target/metadata.json")).isEqualTo(metadataBytes);
        }

        @Test
        @DisplayName("writes a valid task.json envelope at the workspace root")
        void writesTaskJsonEnvelope() throws Exception {
            stubDefaults();
            Map<String, byte[]> files = handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata()));

            assertThat(files).containsKey("task.json");
            JsonNode envelope = objectMapper.readTree(files.get("task.json"));
            assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);
            assertThat(envelope.get("workspaceId").asLong()).isEqualTo(WORKSPACE_ID);
            JsonNode task = envelope.get("task");
            assertThat(task.get("kind").asText()).isEqualTo("practice_review");
            assertThat(task.get("pullRequestNumber").asInt()).isEqualTo(42);
            assertThat(task.get("repositoryFullName").asText()).isEqualTo("owner/repo");
            assertThat(task.get("prompt").asText()).contains("Review merge request #42");
        }

        @Test
        @DisplayName("injects the practice catalog (.practices/index.json + .practices/all-criteria.md)")
        void injectsPracticeCatalog() {
            stubDefaults();
            Map<String, byte[]> files = handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata()));

            assertThat(files).containsKey(".practices/index.json");
            assertThat(files).containsKey(".practices/all-criteria.md");
            assertThat(files).containsKey(".practices/pr-description-quality.md");
            assertThat(files).containsKey(".practices/error-handling.md");
            assertThat(files).containsKey(".analysis/practices/.gitkeep");
        }

        @Test
        @DisplayName("does NOT write a legacy .prompt file (replaced by task.json)")
        void doesNotWriteLegacyPromptFile() {
            stubDefaults();
            Map<String, byte[]> files = handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata()));
            assertThat(files).doesNotContainKey(".prompt");
        }

        @Test
        @DisplayName("rejects practices whose slug fails the workspace-ABI pattern (defense-in-depth)")
        void rejectsMalformedSlug() {
            when(workspaceContextBuilder.build(any())).thenReturn(new LinkedHashMap<>());
            when(practiceRepository.findByWorkspaceIdAndActiveTrue(WORKSPACE_ID)).thenReturn(
                List.of(createPractice("../etc/passwd", "bad", "c"))
            );

            assertThatThrownBy(() -> handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata())))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("Practice slug fails ABI pattern");
        }

        @Test
        @DisplayName("throws JobPreparationException when no active practices for the workspace")
        void throwsWhenNoActivePractices() {
            when(workspaceContextBuilder.build(any())).thenReturn(new LinkedHashMap<>());
            when(practiceRepository.findByWorkspaceIdAndActiveTrue(WORKSPACE_ID)).thenReturn(List.of());

            assertThatThrownBy(() -> handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata())))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("No active practices");
        }

        @Test
        @DisplayName("throws JobPreparationException when metadata is missing")
        void throwsWhenMetadataMissing() {
            var job = new AgentJob();
            job.setMetadata(null);
            assertThatThrownBy(() -> handler.prepareInputFiles(job))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("no metadata");
        }

        @Test
        @DisplayName("preserves provider-file order (LinkedHashMap)")
        void preservesProviderOrder() {
            var providerFiles = new LinkedHashMap<String, byte[]>();
            providerFiles.put("context/target/metadata.json", "{}".getBytes(StandardCharsets.UTF_8));
            providerFiles.put("context/target/diff.patch", "diff".getBytes(StandardCharsets.UTF_8));
            providerFiles.put("context/target/comments.json", "[]".getBytes(StandardCharsets.UTF_8));
            when(workspaceContextBuilder.build(any())).thenReturn(providerFiles);
            when(practiceRepository.findByWorkspaceIdAndActiveTrue(WORKSPACE_ID)).thenReturn(samplePractices());

            Map<String, byte[]> files = handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata()));

            // First three entries must be the provider files in their original order
            var keys = files.keySet().iterator();
            assertThat(keys.next()).isEqualTo("context/target/metadata.json");
            assertThat(keys.next()).isEqualTo("context/target/diff.patch");
            assertThat(keys.next()).isEqualTo("context/target/comments.json");
        }
    }

    @Nested
    @DisplayName("filterByDiffScope")
    class FilterByDiffScope {

        @Test
        @DisplayName("keeps finding whose evidence path is in diff")
        void keepsFindingInDiff() {
            var finding = finding("fatal-error-crash", Verdict.NEGATIVE, "Sources/View.swift");
            var filtered = PullRequestReviewHandler.filterByDiffScope(List.of(finding), Set.of("Sources/View.swift"));
            assertThat(filtered).containsExactly(finding);
        }

        @Test
        @DisplayName("keeps finding backed by internal context/target/metadata.json (whitelisted)")
        void keepsFindingBackedByMetadata() {
            var finding = finding("mr-description-quality", Verdict.NEGATIVE, "context/target/metadata.json");
            var filtered = PullRequestReviewHandler.filterByDiffScope(List.of(finding), Set.of("Sources/View.swift"));
            assertThat(filtered).containsExactly(finding);
        }

        @Test
        @DisplayName("filters finding backed only by non-whitelisted internal context")
        void filtersFindingBackedByNonWhitelistedInternal() {
            var finding = finding("review-noise", Verdict.NEGATIVE, "context/target/comments.json");
            var filtered = PullRequestReviewHandler.filterByDiffScope(List.of(finding), Set.of("Sources/View.swift"));
            assertThat(filtered).isEmpty();
        }

        @Test
        @DisplayName("filters finding pointing only outside the diff")
        void filtersFindingOutsideDiff() {
            var finding = finding("view-logic-separation", Verdict.NEGATIVE, "Sources/Other.swift");
            var filtered = PullRequestReviewHandler.filterByDiffScope(List.of(finding), Set.of("Sources/View.swift"));
            assertThat(filtered).isEmpty();
        }

        private PracticeDetectionResultParser.ValidatedFinding finding(String slug, Verdict verdict, String path) {
            return new PracticeDetectionResultParser.ValidatedFinding(
                slug,
                "title",
                verdict,
                Severity.MINOR,
                0.8f,
                objectMapper
                    .createObjectNode()
                    .set(
                        "locations",
                        objectMapper.createArrayNode().add(objectMapper.createObjectNode().put("path", path))
                    ),
                null,
                null,
                List.of()
            );
        }
    }

    @Nested
    @DisplayName("parseDiffNameOnlyPaths")
    class ParseDiffNameOnlyPaths {

        @Test
        @DisplayName("extracts simple file paths")
        void simplePaths() {
            String output = "src/Main.swift\nViews/ContentView.swift\nREADME.md\n";
            assertThat(PullRequestReviewHandler.parseDiffNameOnlyPaths(output)).containsExactlyInAnyOrder(
                "src/Main.swift",
                "Views/ContentView.swift",
                "README.md"
            );
        }

        @Test
        @DisplayName("returns empty for blank input")
        void blankInput() {
            assertThat(PullRequestReviewHandler.parseDiffNameOnlyPaths("")).isEmpty();
            assertThat(PullRequestReviewHandler.parseDiffNameOnlyPaths("  \n  ")).isEmpty();
        }
    }

    @Nested
    @DisplayName("deliver")
    class Deliver {

        private AgentJob jobWithOutput(String rawOutputJson) {
            var job = new AgentJob();
            job.setId(UUID.randomUUID());
            ObjectNode output = objectMapper.createObjectNode();
            output.put("rawOutput", rawOutputJson);
            job.setOutput(output);
            return job;
        }

        @Test
        @DisplayName("delegates to delivery service with parsed findings")
        @SuppressWarnings("unchecked")
        void delegatesToDeliveryService() {
            String rawOutput = """
                {
                  "findings": [{
                    "practiceSlug": "pr-description-quality",
                    "title": "Good PR description",
                    "verdict": "POSITIVE",
                    "severity": "INFO",
                    "confidence": 0.95
                  }]
                }
                """;
            AgentJob job = jobWithOutput(rawOutput);
            when(deliveryService.deliver(eq(job), any())).thenReturn(new DeliveryResult(1, 0, 0, false));

            handler.deliver(job);

            verify(deliveryService).deliver(eq(job), any());
            verify(feedbackService).deliverFeedback(eq(job), any());
        }

        @Test
        @DisplayName("throws when output has no valid findings")
        void throwsWhenNoValidFindings() {
            AgentJob job = jobWithOutput("{\"findings\":[]}");
            assertThatThrownBy(() -> handler.deliver(job))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("No valid findings");
        }
    }
}
