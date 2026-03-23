package de.tum.in.www1.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.agent.AgentJobType;
import de.tum.in.www1.hephaestus.agent.handler.PracticeDetectionDeliveryService.DeliveryResult;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobSubmission;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobSubmissionRequest;
import de.tum.in.www1.hephaestus.agent.job.AgentJob;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.events.RepositoryRef;
import de.tum.in.www1.hephaestus.gitprovider.git.GitRepositoryManager;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.practices.PracticeRepository;
import de.tum.in.www1.hephaestus.practices.model.CaMethod;
import de.tum.in.www1.hephaestus.practices.model.Practice;
import de.tum.in.www1.hephaestus.practices.model.Severity;
import de.tum.in.www1.hephaestus.practices.model.Verdict;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

@DisplayName("PullRequestReviewHandler")
class PullRequestReviewHandlerTest extends BaseUnitTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private GitRepositoryManager gitRepositoryManager;

    @Mock
    private PullRequestRepository pullRequestRepository;

    @Mock
    private PullRequestReviewCommentRepository reviewCommentRepository;

    @Mock
    private PracticeRepository practiceRepository;

    @Mock
    private PracticeDetectionDeliveryService deliveryService;

    private static final Long WORKSPACE_ID = 99L;

    @Mock
    private FeedbackDeliveryService feedbackService;

    private PracticeDetectionResultParser resultParser;
    private PullRequestReviewHandler handler;

    @BeforeEach
    void setUp() {
        resultParser = new PracticeDetectionResultParser(objectMapper, 100);
        handler = new PullRequestReviewHandler(
            objectMapper,
            gitRepositoryManager,
            pullRequestRepository,
            reviewCommentRepository,
            practiceRepository,
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
        job.setMetadata(metadata);
        Workspace workspace = new Workspace();
        workspace.setId(WORKSPACE_ID);
        job.setWorkspace(workspace);
        return job;
    }

    private Practice createPractice(String slug, String name, String description, String detectionPrompt) {
        Practice p = new Practice();
        p.setId((long) slug.hashCode());
        p.setSlug(slug);
        p.setName(name);
        p.setDescription(description);
        p.setDetectionPrompt(detectionPrompt);
        p.setActive(true);
        return p;
    }

    private List<Practice> samplePractices() {
        return List.of(
            createPractice(
                "pr-description-quality",
                "PR Description Quality",
                "PRs should have clear titles and descriptions explaining the change.",
                "Check if the PR has a meaningful title and description that explains the why."
            ),
            createPractice(
                "error-handling",
                "Error Handling",
                "Code should handle errors explicitly rather than silently swallowing exceptions.",
                null // No detection prompt — falls back to description
            )
        );
    }

    /** Stub all git/DB calls to return minimal valid data. */
    private void stubDefaults() {
        lenient().when(gitRepositoryManager.isEnabled()).thenReturn(true);
        lenient().when(gitRepositoryManager.isRepositoryCloned(123L)).thenReturn(true);
        lenient()
            .when(gitRepositoryManager.getRepositoryPath(123L))
            .thenReturn(java.nio.file.Path.of("/tmp/hephaestus-git-repos/123"));
        when(pullRequestRepository.findByIdWithAllForGate(456L)).thenReturn(Optional.empty());
        when(reviewCommentRepository.findByPullRequestIdWithAuthorOrderByCreatedAt(456L)).thenReturn(List.of());
        when(practiceRepository.findByWorkspaceIdAndActiveTrue(WORKSPACE_ID)).thenReturn(samplePractices());
    }

    @Nested
    @DisplayName("jobType")
    class JobType {

        @Test
        @DisplayName("should return PULL_REQUEST_REVIEW")
        void shouldReturnPullRequestReview() {
            assertThat(handler.jobType()).isEqualTo(AgentJobType.PULL_REQUEST_REVIEW);
        }
    }

    @Nested
    @DisplayName("createSubmission")
    class CreateSubmission {

        @Test
        @DisplayName("should extract correct metadata from request")
        void shouldExtractCorrectMetadata() {
            JobSubmission submission = handler.createSubmission(sampleRequest());
            JsonNode metadata = submission.metadata();

            assertThat(metadata.get("repository_id").asLong()).isEqualTo(123L);
            assertThat(metadata.get("repository_full_name").asText()).isEqualTo("owner/repo");
            assertThat(metadata.get("pull_request_id").asLong()).isEqualTo(456L);
            assertThat(metadata.get("pr_number").asInt()).isEqualTo(42);
            assertThat(metadata.get("pr_url").asText()).isEqualTo("https://github.com/owner/repo/pull/42");
            assertThat(metadata.get("commit_sha").asText()).isEqualTo("abc123def456");
            assertThat(metadata.get("source_branch").asText()).isEqualTo("feature/auth-fix");
            assertThat(metadata.get("target_branch").asText()).isEqualTo("main");
        }

        @Test
        @DisplayName("should build correct idempotency key")
        void shouldBuildCorrectIdempotencyKey() {
            JobSubmission submission = handler.createSubmission(sampleRequest());

            assertThat(submission.idempotencyKey()).isEqualTo("pr_review:owner/repo:42:abc123def456");
        }

        @Test
        @DisplayName("should reject wrong request type")
        void shouldRejectWrongRequestType() {
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
        @DisplayName("should include only metadata.json and comments.json (repo is bind-mounted)")
        void shouldIncludeOnlyDbSourcedContextFiles() throws Exception {
            stubDefaults();

            Map<String, byte[]> files = handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata()));

            // Only DB-sourced context files — no repo files, no diff.patch
            assertThat(files).containsKey(".context/metadata.json");
            assertThat(files).containsKey(".context/comments.json");
            assertThat(files).doesNotContainKey(".context/diff.patch");
            assertThat(
                files
                    .keySet()
                    .stream()
                    .noneMatch(k -> k.startsWith("repo/"))
            )
                .as("repo files should not be injected (repo is bind-mounted)")
                .isTrue();

            // Verify metadata.json is valid JSON
            JsonNode metadataJson = objectMapper.readTree(files.get(".context/metadata.json"));
            assertThat(metadataJson.get("pr_number").asInt()).isEqualTo(42);
            assertThat(metadataJson.get("repository_full_name").asText()).isEqualTo("owner/repo");
            assertThat(metadataJson.get("enriched").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("should enrich metadata from database when pull request exists")
        void shouldEnrichMetadataFromDatabase() throws Exception {
            PullRequest pullRequest = new PullRequest();
            pullRequest.setTitle("Fix authentication bug");
            pullRequest.setBody("This PR fixes the login issue");
            pullRequest.setState(Issue.State.OPEN);
            pullRequest.setAdditions(10);
            pullRequest.setDeletions(5);
            pullRequest.setChangedFiles(3);
            User author = new User();
            author.setLogin("testuser");
            pullRequest.setAuthor(author);

            stubDefaults();
            when(pullRequestRepository.findByIdWithAllForGate(456L)).thenReturn(Optional.of(pullRequest));

            Map<String, byte[]> files = handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata()));

            JsonNode metadataJson = objectMapper.readTree(files.get(".context/metadata.json"));
            assertThat(metadataJson.get("enriched").asBoolean()).isTrue();
            assertThat(metadataJson.get("title").asText()).isEqualTo("Fix authentication bug");
            assertThat(metadataJson.get("author").asText()).isEqualTo("testuser");
            assertThat(metadataJson.get("additions").asInt()).isEqualTo(10);
        }

        @Test
        @DisplayName("should throw JobPreparationException on missing metadata field")
        void shouldThrowOnMissingMetadataField() {
            ObjectNode incomplete = objectMapper.createObjectNode();
            incomplete.put("repository_id", 123L);
            // Missing all other fields

            assertThatThrownBy(() -> handler.prepareInputFiles(jobWithMetadata(incomplete)))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("Missing required metadata field");
        }

        @Test
        @DisplayName("should throw JobPreparationException on non-numeric metadata field")
        void shouldThrowOnNonNumericMetadataField() {
            ObjectNode badMetadata = sampleJobMetadata();
            badMetadata.put("repository_id", "not-a-number");

            assertThatThrownBy(() -> handler.prepareInputFiles(jobWithMetadata(badMetadata)))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("Expected numeric metadata field");
        }

        @Test
        @DisplayName("should throw JobPreparationException when metadata is null")
        void shouldThrowWhenMetadataIsNull() {
            var job = new AgentJob();
            job.setMetadata(null);

            assertThatThrownBy(() -> handler.prepareInputFiles(job))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("no metadata");
        }

        @Test
        @DisplayName("should throw JobPreparationException when workspace is null")
        void shouldThrowWhenWorkspaceIsNull() {
            // Workspace null-guard fires after DB-sourced context preparation
            lenient().when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(pullRequestRepository.findByIdWithAllForGate(456L)).thenReturn(Optional.empty());
            when(reviewCommentRepository.findByPullRequestIdWithAuthorOrderByCreatedAt(456L)).thenReturn(List.of());

            var job = new AgentJob();
            job.setMetadata(sampleJobMetadata());
            // workspace deliberately not set

            assertThatThrownBy(() -> handler.prepareInputFiles(job))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("no workspace");
        }

        @Test
        @DisplayName("should include comments with null author")
        void shouldHandleCommentsWithNullAuthor() throws Exception {
            PullRequestReviewComment comment = new PullRequestReviewComment();
            comment.setPath("src/Main.java");
            comment.setLine(10);
            comment.setBody("Looks good");
            comment.setAuthor(null);
            comment.setCreatedAt(Instant.parse("2025-01-15T10:30:00Z"));

            stubDefaults();
            when(reviewCommentRepository.findByPullRequestIdWithAuthorOrderByCreatedAt(456L)).thenReturn(
                List.of(comment)
            );

            Map<String, byte[]> files = handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata()));

            JsonNode comments = objectMapper.readTree(files.get(".context/comments.json"));
            assertThat(comments).hasSize(1);
            assertThat(comments.get(0).get("path").asText()).isEqualTo("src/Main.java");
            assertThat(comments.get(0).get("body").asText()).isEqualTo("Looks good");
            assertThat(comments.get(0).get("created_at").asText()).isEqualTo("2025-01-15T10:30:00Z");
            assertThat(comments.get(0).has("author")).isFalse();
        }

        @Test
        @DisplayName("should serialize comment fields conditionally based on null values")
        void shouldSerializeCommentFieldsConditionally() throws Exception {
            PullRequestReviewComment full = new PullRequestReviewComment();
            full.setPath("src/Main.java");
            full.setLine(10);
            full.setBody("Fix this");
            full.setCreatedAt(Instant.parse("2025-06-01T12:00:00Z"));
            User reviewer = new User();
            reviewer.setLogin("reviewer");
            full.setAuthor(reviewer);

            PullRequestReviewComment minimal = new PullRequestReviewComment();
            minimal.setPath("src/Other.java");
            minimal.setLine(5);
            minimal.setBody("Old comment");
            minimal.setCreatedAt(null);
            minimal.setAuthor(null);

            stubDefaults();
            when(reviewCommentRepository.findByPullRequestIdWithAuthorOrderByCreatedAt(456L)).thenReturn(
                List.of(full, minimal)
            );

            Map<String, byte[]> files = handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata()));

            JsonNode comments = objectMapper.readTree(files.get(".context/comments.json"));
            assertThat(comments).hasSize(2);

            // Full comment — all fields present
            assertThat(comments.get(0).get("created_at").asText()).isEqualTo("2025-06-01T12:00:00Z");
            assertThat(comments.get(0).get("author").asText()).isEqualTo("reviewer");
            assertThat(comments.get(0).get("path").asText()).isEqualTo("src/Main.java");

            // Minimal comment — optional fields absent
            assertThat(comments.get(1).has("created_at")).isFalse();
            assertThat(comments.get(1).has("author")).isFalse();
            assertThat(comments.get(1).get("body").asText()).isEqualTo("Old comment");
        }

        @Test
        @DisplayName("should truncate comments to MAX_COMMENTS keeping most recent")
        void shouldTruncateCommentsToMaxKeepingMostRecent() throws Exception {
            var comments = new java.util.ArrayList<PullRequestReviewComment>();
            for (int i = 0; i < PullRequestReviewHandler.MAX_COMMENTS + 100; i++) {
                PullRequestReviewComment c = new PullRequestReviewComment();
                c.setPath("file.java");
                c.setLine(i);
                c.setBody("Comment " + i);
                comments.add(c);
            }

            stubDefaults();
            when(reviewCommentRepository.findByPullRequestIdWithAuthorOrderByCreatedAt(456L)).thenReturn(comments);

            Map<String, byte[]> files = handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata()));

            JsonNode commentsJson = objectMapper.readTree(files.get(".context/comments.json"));
            assertThat(commentsJson).hasSize(PullRequestReviewHandler.MAX_COMMENTS);
            // Should keep the most recent (last) comments
            assertThat(commentsJson.get(0).get("body").asText()).isEqualTo("Comment 100");
            assertThat(commentsJson.get(PullRequestReviewHandler.MAX_COMMENTS - 1).get("body").asText()).isEqualTo(
                "Comment " + (PullRequestReviewHandler.MAX_COMMENTS + 99)
            );
        }

        @Test
        @DisplayName("should NOT include practices.json (practices are inline in prompt)")
        void shouldNotIncludePracticesJson() throws Exception {
            stubDefaults();

            Map<String, byte[]> files = handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata()));

            // Practices are no longer written to workspace files — they are inline in buildPrompt()
            assertThat(files).doesNotContainKey(".context/practices.json");
        }

        @Test
        @DisplayName("should throw JobPreparationException when no active practices")
        void shouldThrowWhenNoPractices() {
            stubDefaults();
            when(practiceRepository.findByWorkspaceIdAndActiveTrue(WORKSPACE_ID)).thenReturn(List.of());

            assertThatThrownBy(() -> handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata())))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("No active practices");
        }

        @Test
        @DisplayName("should include practice category in prompt when present")
        void shouldIncludePracticeCategoryInPrompt() {
            Practice withCategory = createPractice("test-coverage", "Test Coverage", "Tests required.", null);
            withCategory.setCategory("testing");
            when(practiceRepository.findByWorkspaceIdAndActiveTrue(WORKSPACE_ID)).thenReturn(List.of(withCategory));

            String prompt = handler.buildPrompt(jobWithMetadata(sampleJobMetadata()));

            assertThat(prompt).contains("### test-coverage: Test Coverage");
            assertThat(prompt).contains("Category: testing");
        }

        @Test
        @DisplayName("should handle enriched metadata with null author")
        void shouldHandleEnrichedMetadataWithNullAuthor() throws Exception {
            PullRequest pullRequest = new PullRequest();
            pullRequest.setTitle("Some PR");
            pullRequest.setBody("Body");
            pullRequest.setState(Issue.State.OPEN);
            pullRequest.setAuthor(null);

            stubDefaults();
            when(pullRequestRepository.findByIdWithAllForGate(456L)).thenReturn(Optional.of(pullRequest));

            Map<String, byte[]> files = handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata()));

            JsonNode metadataJson = objectMapper.readTree(files.get(".context/metadata.json"));
            assertThat(metadataJson.get("enriched").asBoolean()).isTrue();
            assertThat(metadataJson.get("title").asText()).isEqualTo("Some PR");
            assertThat(metadataJson.get("state").asText()).isEqualTo("OPEN");
            assertThat(metadataJson.has("author")).isFalse();
        }
    }

    @Nested
    @DisplayName("buildPrompt")
    class BuildPrompt {

        @Test
        @DisplayName("should contain section headers, output path, git instructions, and pull request details")
        void shouldReferenceWorkspacePaths() {
            when(practiceRepository.findByWorkspaceIdAndActiveTrue(WORKSPACE_ID)).thenReturn(samplePractices());

            String prompt = handler.buildPrompt(jobWithMetadata(sampleJobMetadata()));

            // Prompt structure: section headers for inline context and instructions
            assertThat(prompt).contains("## Context");
            assertThat(prompt).contains("## Repository");
            assertThat(prompt).contains("## Practices to Evaluate");
            assertThat(prompt).contains("## Instructions");
            assertThat(prompt).contains("## Output");
            // Output path is still referenced (agent writes results there)
            assertThat(prompt).contains("/workspace/.output/result.json");
            // Pull request details are present
            assertThat(prompt).contains("#42");
            assertThat(prompt).contains("owner/repo");
            // Git instructions reference real branch names
            assertThat(prompt).contains("origin/feature/auth-fix");
            assertThat(prompt).contains("origin/main");
            assertThat(prompt).contains("git diff origin/main..origin/feature/auth-fix");
        }

        @Test
        @DisplayName("should handle percent characters in metadata without format string errors")
        void shouldHandlePercentInMetadata() {
            when(practiceRepository.findByWorkspaceIdAndActiveTrue(WORKSPACE_ID)).thenReturn(samplePractices());
            ObjectNode metadata = sampleJobMetadata();
            metadata.put("repository_full_name", "owner/repo%with%percent");
            metadata.put("pr_url", "https://github.com/owner/repo%20special/pull/42");

            String prompt = handler.buildPrompt(jobWithMetadata(metadata));

            assertThat(prompt).contains("owner/repo%with%percent");
            assertThat(prompt).contains("repo%20special");
        }

        @Test
        @DisplayName("should throw on missing metadata fields")
        void shouldThrowOnMissingMetadataFields() {
            ObjectNode incomplete = objectMapper.createObjectNode();

            assertThatThrownBy(() -> handler.buildPrompt(jobWithMetadata(incomplete)))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("Missing required metadata field");
        }

        @Test
        @DisplayName("should throw JobPreparationException when metadata is null")
        void shouldThrowWhenMetadataIsNull() {
            var job = new AgentJob();
            job.setMetadata(null);
            Workspace ws = new Workspace();
            ws.setId(WORKSPACE_ID);
            job.setWorkspace(ws);

            assertThatThrownBy(() -> handler.buildPrompt(job))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("no metadata");
        }

        @Test
        @DisplayName("should throw JobPreparationException when workspace is null")
        void shouldThrowWhenWorkspaceIsNull() {
            var job = new AgentJob();
            job.setMetadata(sampleJobMetadata());
            // workspace deliberately not set

            assertThatThrownBy(() -> handler.buildPrompt(job))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("no workspace");
        }

        @Test
        @DisplayName("should include all practice definitions as markdown headings")
        void shouldIncludeAllPracticeDefinitions() {
            when(practiceRepository.findByWorkspaceIdAndActiveTrue(WORKSPACE_ID)).thenReturn(samplePractices());

            String prompt = handler.buildPrompt(jobWithMetadata(sampleJobMetadata()));

            // Both practices appear as structured markdown headings
            assertThat(prompt).contains("### pr-description-quality: PR Description Quality");
            assertThat(prompt).contains("### error-handling: Error Handling");
            // Category is null for sample practices — must NOT appear
            assertThat(prompt).doesNotContain("Category:");
        }

        @Test
        @DisplayName("should use detectionPrompt when available, description as fallback")
        void shouldPreferDetectionPromptOverDescription() {
            when(practiceRepository.findByWorkspaceIdAndActiveTrue(WORKSPACE_ID)).thenReturn(samplePractices());

            String prompt = handler.buildPrompt(jobWithMetadata(sampleJobMetadata()));

            // Practice with detectionPrompt — uses it, NOT the description
            assertThat(prompt).contains(
                "Check if the PR has a meaningful title and description that explains the why."
            );
            assertThat(prompt).doesNotContain("PRs should have clear titles and descriptions explaining the change.");
            // Practice without detectionPrompt — falls back to description
            assertThat(prompt).contains(
                "Code should handle errors explicitly rather than silently swallowing exceptions."
            );
        }

        @Test
        @DisplayName("should specify output contract matching PracticeDetectionResultParser")
        void shouldSpecifyOutputContractMatchingParser() {
            when(practiceRepository.findByWorkspaceIdAndActiveTrue(WORKSPACE_ID)).thenReturn(samplePractices());

            String prompt = handler.buildPrompt(jobWithMetadata(sampleJobMetadata()));

            // Output section exists and specifies the result file
            assertThat(prompt).contains("## Output");
            assertThat(prompt).contains("/workspace/.output/result.json");
            assertThat(prompt).contains("\"findings\"");

            // Required fields appear in the JSON schema section
            String outputSection = prompt.substring(prompt.indexOf("## Output"));
            assertThat(outputSection).contains("\"practiceSlug\"");
            assertThat(outputSection).contains("\"title\"");
            assertThat(outputSection).contains("\"verdict\"");
            assertThat(outputSection).contains("\"severity\"");
            assertThat(outputSection).contains("\"confidence\"");

            // Optional fields marked as such
            assertThat(outputSection).contains("\"reasoning\"");
            assertThat(outputSection).contains("\"guidance\"");
            assertThat(outputSection).contains("\"guidanceMethod\"");
            assertThat(outputSection).contains("(optional");

            // All Verdict enum values from Verdict.java
            for (Verdict v : Verdict.values()) {
                assertThat(prompt).contains(v.name());
            }
            // All Severity enum values from Severity.java
            for (Severity s : Severity.values()) {
                assertThat(prompt).contains(s.name());
            }
            // All CaMethod enum values from CaMethod.java
            for (CaMethod m : CaMethod.values()) {
                assertThat(prompt).contains(m.name());
            }

            // JSON output instruction
            assertThat(prompt).contains("write a JSON object to `/workspace/.output/result.json`");
        }

        @Test
        @DisplayName("should throw when no active practices exist")
        void shouldThrowWhenNoPractices() {
            when(practiceRepository.findByWorkspaceIdAndActiveTrue(WORKSPACE_ID)).thenReturn(List.of());

            assertThatThrownBy(() -> handler.buildPrompt(jobWithMetadata(sampleJobMetadata())))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("No active practices");
        }

        @Test
        @DisplayName("should handle practice with null description and null detectionPrompt")
        void shouldHandleNullInstructions() {
            Practice noInstructions = createPractice("code-style", "Code Style", null, null);
            when(practiceRepository.findByWorkspaceIdAndActiveTrue(WORKSPACE_ID)).thenReturn(List.of(noInstructions));

            String prompt = handler.buildPrompt(jobWithMetadata(sampleJobMetadata()));

            // Practice heading is present even without instructions
            assertThat(prompt).contains("### code-style: Code Style");
            // Should not contain "null" as literal text
            assertThat(prompt).doesNotContain("null\n");
        }

        @Test
        @DisplayName("should include category in prompt when practice has one")
        void shouldIncludeCategoryInPrompt() {
            Practice withCategory = createPractice("test-coverage", "Test Coverage", "Tests required.", null);
            withCategory.setCategory("testing");
            when(practiceRepository.findByWorkspaceIdAndActiveTrue(WORKSPACE_ID)).thenReturn(List.of(withCategory));

            String prompt = handler.buildPrompt(jobWithMetadata(sampleJobMetadata()));

            assertThat(prompt).contains("### test-coverage: Test Coverage");
            assertThat(prompt).contains("Category: testing");
        }

        @Test
        @DisplayName("should work correctly with a single practice")
        void shouldWorkWithSinglePractice() {
            Practice single = createPractice(
                "naming-conventions",
                "Naming Conventions",
                "Variables and methods should follow language conventions.",
                null
            );
            when(practiceRepository.findByWorkspaceIdAndActiveTrue(WORKSPACE_ID)).thenReturn(List.of(single));

            String prompt = handler.buildPrompt(jobWithMetadata(sampleJobMetadata()));

            assertThat(prompt).contains("### naming-conventions: Naming Conventions");
            assertThat(prompt).contains("Variables and methods should follow language conventions.");
            // Only one practice heading
            assertThat(prompt.split("### ").length - 1).isEqualTo(1);
        }

        @Test
        @DisplayName("should include verdict meanings for agent guidance")
        void shouldIncludeVerdictMeanings() {
            when(practiceRepository.findByWorkspaceIdAndActiveTrue(WORKSPACE_ID)).thenReturn(samplePractices());

            String prompt = handler.buildPrompt(jobWithMetadata(sampleJobMetadata()));

            assertThat(prompt).contains("POSITIVE`: the contributor followed");
            assertThat(prompt).contains("NEGATIVE`: the contributor violated");
            assertThat(prompt).contains("NOT_APPLICABLE`: practice does not apply");
            assertThat(prompt).contains("NEEDS_REVIEW`: borderline");
        }

        @Test
        @DisplayName("should include practice count in output contract")
        void shouldIncludePracticeCountInOutputContract() {
            when(practiceRepository.findByWorkspaceIdAndActiveTrue(WORKSPACE_ID)).thenReturn(samplePractices());

            String prompt = handler.buildPrompt(jobWithMetadata(sampleJobMetadata()));

            // samplePractices() returns 2 practices — count must appear in field rules
            assertThat(prompt).contains("2 total");
        }

        @Test
        @DisplayName("should include severity+verdict example for disambiguation")
        void shouldIncludeSeverityVerdictExample() {
            when(practiceRepository.findByWorkspaceIdAndActiveTrue(WORKSPACE_ID)).thenReturn(samplePractices());

            String prompt = handler.buildPrompt(jobWithMetadata(sampleJobMetadata()));

            // Severity semantics are disambiguated with a concrete example
            assertThat(prompt).contains("verdict=POSITIVE severity=MAJOR");
            assertThat(prompt).contains("verdict=NEGATIVE severity=MINOR");
        }
    }

    @Nested
    @DisplayName("deliver")
    class Deliver {

        private AgentJob jobWithOutput(String rawOutputJson) {
            var job = new AgentJob();
            ObjectNode output = objectMapper.createObjectNode();
            output.put("rawOutput", rawOutputJson);
            job.setOutput(output);
            return job;
        }

        @Test
        @DisplayName("should delegate to parser and delivery service with correct findings")
        @SuppressWarnings("unchecked")
        void shouldDelegateToDeliveryService() {
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
            when(deliveryService.deliver(eq(job), any())).thenReturn(new DeliveryResult(1, 0, 0, 0, false));

            handler.deliver(job);

            var captor = ArgumentCaptor.forClass(List.class);
            verify(deliveryService).deliver(eq(job), captor.capture());
            var findings = (List<PracticeDetectionResultParser.ValidatedFinding>) captor.getValue();
            assertThat(findings).hasSize(1);
            assertThat(findings.get(0).practiceSlug()).isEqualTo("pr-description-quality");
            assertThat(findings.get(0).verdict()).isEqualTo(Verdict.POSITIVE);
            assertThat(findings.get(0).severity()).isEqualTo(Severity.INFO);
            assertThat(findings.get(0).confidence()).isEqualTo(0.95f);
        }

        @Test
        @DisplayName("should re-throw JobDeliveryException from delivery service as-is")
        void shouldRethrowJobDeliveryException() {
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
            JobDeliveryException original = new JobDeliveryException("Workspace not found");
            when(deliveryService.deliver(eq(job), any())).thenThrow(original);

            assertThatThrownBy(() -> handler.deliver(job)).isSameAs(original);
        }

        @Test
        @DisplayName("should wrap unexpected exceptions from delivery service")
        void shouldWrapUnexpectedExceptions() {
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
            when(deliveryService.deliver(eq(job), any())).thenThrow(new RuntimeException("DB connection lost"));

            assertThatThrownBy(() -> handler.deliver(job))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("Delivery failed unexpectedly")
                .hasCauseInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("should throw JobDeliveryException when no valid findings")
        void shouldThrowWhenNoValidFindings() {
            String rawOutput = """
                {
                  "findings": [{
                    "practiceSlug": "",
                    "title": "",
                    "verdict": "POSITIVE",
                    "severity": "INFO",
                    "confidence": 0.95
                  }]
                }
                """;
            AgentJob job = jobWithOutput(rawOutput);

            assertThatThrownBy(() -> handler.deliver(job))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("No valid findings");

            verifyNoInteractions(deliveryService);
        }

        @Test
        @DisplayName("should throw JobDeliveryException when output is null")
        void shouldThrowWhenOutputIsNull() {
            var job = new AgentJob();
            job.setOutput(null);

            assertThatThrownBy(() -> handler.deliver(job))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("No valid findings");
        }

        @Test
        @DisplayName("should throw JobDeliveryException when rawOutput is invalid JSON")
        void shouldThrowWhenInvalidJson() {
            AgentJob job = jobWithOutput("not valid json {{{");

            assertThatThrownBy(() -> handler.deliver(job))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("No valid findings");
        }

        @Test
        @DisplayName("should deliver valid findings and discard invalid ones")
        @SuppressWarnings("unchecked")
        void shouldDeliverValidAndDiscardInvalid() {
            String rawOutput = """
                {
                  "findings": [
                    {
                      "practiceSlug": "pr-description-quality",
                      "title": "Good PR description",
                      "verdict": "POSITIVE",
                      "severity": "INFO",
                      "confidence": 0.90
                    },
                    {
                      "practiceSlug": "",
                      "title": "",
                      "verdict": "POSITIVE",
                      "severity": "INFO",
                      "confidence": 0.5
                    }
                  ]
                }
                """;
            AgentJob job = jobWithOutput(rawOutput);
            when(deliveryService.deliver(eq(job), any())).thenReturn(new DeliveryResult(1, 0, 0, 0, false));

            handler.deliver(job);

            var captor = ArgumentCaptor.forClass(List.class);
            verify(deliveryService).deliver(eq(job), captor.capture());
            var findings = (List<PracticeDetectionResultParser.ValidatedFinding>) captor.getValue();
            // Only the valid finding should be passed through
            assertThat(findings).hasSize(1);
            assertThat(findings.get(0).practiceSlug()).isEqualTo("pr-description-quality");
        }

        @Test
        @DisplayName("should call feedbackService after findings delivery")
        void shouldCallFeedbackServiceAfterFindings() {
            String rawOutput = """
                {
                  "findings": [{
                    "practiceSlug": "pr-description-quality",
                    "title": "Good PR description",
                    "verdict": "NEGATIVE",
                    "severity": "MAJOR",
                    "confidence": 0.85
                  }],
                  "delivery": {
                    "mrNote": "Please improve PR description."
                  }
                }
                """;
            AgentJob job = jobWithOutput(rawOutput);
            when(deliveryService.deliver(eq(job), any())).thenReturn(new DeliveryResult(1, 0, 0, 0, true));

            handler.deliver(job);

            var deliveryCaptor = ArgumentCaptor.forClass(PracticeDetectionResultParser.DeliveryContent.class);
            verify(feedbackService).deliverFeedback(eq(job), deliveryCaptor.capture(), eq(true));
            var delivery = deliveryCaptor.getValue();
            assertThat(delivery).isNotNull();
            assertThat(delivery.mrNote()).isEqualTo("Please improve PR description.");
            assertThat(delivery.diffNotes()).isEmpty();
        }

        @Test
        @DisplayName("should call feedbackService with null delivery when no delivery content")
        void shouldCallFeedbackServiceWithNullDelivery() {
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
            when(deliveryService.deliver(eq(job), any())).thenReturn(new DeliveryResult(1, 0, 0, 0, false));

            handler.deliver(job);

            verify(feedbackService).deliverFeedback(eq(job), eq(null), eq(false));
        }

        @Test
        @DisplayName("should rethrow JobDeliveryException from practice detection unwrapped")
        void shouldRethrowJobDeliveryExceptionUnwrapped() {
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
            JobDeliveryException original = new JobDeliveryException("Practice DB constraint violation");
            when(deliveryService.deliver(eq(job), any())).thenThrow(original);

            assertThatThrownBy(() -> handler.deliver(job)).isSameAs(original); // Not wrapped — same instance

            verifyNoInteractions(feedbackService);
        }
    }

    @Nested
    @DisplayName("volumeMounts")
    class VolumeMounts {

        @Test
        @DisplayName("should mount real repo path read-only at /workspace/repo")
        void shouldMountRealRepoPath() {
            when(gitRepositoryManager.isRepositoryCloned(123L)).thenReturn(true);
            when(gitRepositoryManager.getRepositoryPath(123L)).thenReturn(
                java.nio.file.Path.of("/tmp/hephaestus-git-repos/123")
            );

            Map<String, String> mounts = handler.volumeMounts(jobWithMetadata(sampleJobMetadata()));

            assertThat(mounts).containsEntry("/tmp/hephaestus-git-repos/123", "/workspace/repo");
            assertThat(mounts).hasSize(1);
        }

        @Test
        @DisplayName("should throw when repository is not cloned")
        void shouldThrowWhenRepoNotCloned() {
            when(gitRepositoryManager.isRepositoryCloned(123L)).thenReturn(false);
            when(gitRepositoryManager.getRepositoryPath(123L)).thenReturn(
                java.nio.file.Path.of("/tmp/hephaestus-git-repos/123")
            );

            assertThatThrownBy(() -> handler.volumeMounts(jobWithMetadata(sampleJobMetadata())))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("Repository not cloned");
        }
    }
}
