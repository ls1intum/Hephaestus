package de.tum.in.www1.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    private PullRequestCommentPoster commentPoster;

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
            commentPoster
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
        when(gitRepositoryManager.readFilesAtCommit(eq(123L), eq("abc123def456"), anyLong())).thenReturn(Map.of());
        when(gitRepositoryManager.generateUnifiedDiff(123L, "main", "feature/auth-fix")).thenReturn("diff content");
        when(pullRequestRepository.findByIdWithRepository(456L)).thenReturn(Optional.empty());
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
        @DisplayName("should include repo files under repo/ prefix and all context files")
        void shouldIncludeRepoFiles() {
            Map<String, byte[]> repoFiles = Map.of(
                "src/Main.java",
                "public class Main {}".getBytes(),
                "README.md",
                "# My Project".getBytes()
            );
            // Stub readFilesAtCommit directly with custom data (avoids double-stub from stubDefaults)
            when(gitRepositoryManager.readFilesAtCommit(eq(123L), eq("abc123def456"), anyLong())).thenReturn(repoFiles);
            when(gitRepositoryManager.generateUnifiedDiff(123L, "main", "feature/auth-fix")).thenReturn("diff content");
            when(pullRequestRepository.findByIdWithRepository(456L)).thenReturn(Optional.empty());
            when(reviewCommentRepository.findByPullRequestIdWithAuthorOrderByCreatedAt(456L)).thenReturn(List.of());
            when(practiceRepository.findByWorkspaceIdAndActiveTrue(WORKSPACE_ID)).thenReturn(samplePractices());

            Map<String, byte[]> files = handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata()));

            // Repo files prefixed with repo/
            assertThat(files).containsKey("repo/src/Main.java");
            assertThat(files).containsKey("repo/README.md");
            assertThat(new String(files.get("repo/src/Main.java"), StandardCharsets.UTF_8)).isEqualTo(
                "public class Main {}"
            );
            // All context files present
            assertThat(files).containsKeys(
                ".context/diff.patch",
                ".context/metadata.json",
                ".context/comments.json",
                ".context/practices.json"
            );
        }

        @Test
        @DisplayName("should include diff patch")
        void shouldIncludeDiffPatch() {
            String diffContent = "--- a/file.java\n+++ b/file.java\n@@ -1,3 +1,4 @@\n+new line\n";
            stubDefaults();
            when(gitRepositoryManager.generateUnifiedDiff(123L, "main", "feature/auth-fix")).thenReturn(diffContent);

            Map<String, byte[]> files = handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata()));

            assertThat(files).containsKey(".context/diff.patch");
            assertThat(new String(files.get(".context/diff.patch"), StandardCharsets.UTF_8)).isEqualTo(diffContent);
        }

        @Test
        @DisplayName("should include metadata.json and comments.json")
        void shouldIncludeMetadataAndComments() throws Exception {
            stubDefaults();

            Map<String, byte[]> files = handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata()));

            assertThat(files).containsKey(".context/metadata.json");
            assertThat(files).containsKey(".context/comments.json");

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
            when(pullRequestRepository.findByIdWithRepository(456L)).thenReturn(Optional.of(pullRequest));

            Map<String, byte[]> files = handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata()));

            JsonNode metadataJson = objectMapper.readTree(files.get(".context/metadata.json"));
            assertThat(metadataJson.get("enriched").asBoolean()).isTrue();
            assertThat(metadataJson.get("title").asText()).isEqualTo("Fix authentication bug");
            assertThat(metadataJson.get("author").asText()).isEqualTo("testuser");
            assertThat(metadataJson.get("additions").asInt()).isEqualTo(10);
        }

        @Test
        @DisplayName("should throw JobPreparationException when diff is empty")
        void shouldThrowWhenDiffIsEmpty() {
            when(gitRepositoryManager.readFilesAtCommit(eq(123L), eq("abc123def456"), anyLong())).thenReturn(Map.of());
            when(gitRepositoryManager.generateUnifiedDiff(123L, "main", "feature/auth-fix")).thenReturn("");

            assertThatThrownBy(() -> handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata())))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("Empty diff");
        }

        @Test
        @DisplayName("should throw JobPreparationException when readFilesAtCommit fails")
        void shouldThrowWhenReadFilesFails() {
            when(gitRepositoryManager.readFilesAtCommit(eq(123L), anyString(), anyLong())).thenThrow(
                new RuntimeException("Git error")
            );

            assertThatThrownBy(() -> handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata())))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("Failed to read repo files")
                .hasCauseInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("should throw JobPreparationException when generateUnifiedDiff fails")
        void shouldThrowWhenDiffGenerationFails() {
            when(gitRepositoryManager.readFilesAtCommit(eq(123L), eq("abc123def456"), anyLong())).thenReturn(Map.of());
            when(gitRepositoryManager.generateUnifiedDiff(123L, "main", "feature/auth-fix")).thenThrow(
                new RuntimeException("Diff error")
            );

            assertThatThrownBy(() -> handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata())))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("Failed to generate diff")
                .hasCauseInstanceOf(RuntimeException.class);
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
        @DisplayName("should truncate oversized diff")
        void shouldTruncateOversizedDiff() {
            // Create a diff just over the 2 MB limit
            String largeDiff = "x".repeat((int) PullRequestReviewHandler.MAX_DIFF_BYTES + 1000);
            stubDefaults();
            when(gitRepositoryManager.generateUnifiedDiff(123L, "main", "feature/auth-fix")).thenReturn(largeDiff);

            Map<String, byte[]> files = handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata()));

            String diff = new String(files.get(".context/diff.patch"), StandardCharsets.UTF_8);
            assertThat(diff).contains("[... diff truncated at 2 MB");
            // Should be truncated to approximately MAX_DIFF_BYTES + truncation note
            assertThat(diff.getBytes(StandardCharsets.UTF_8).length).isLessThan(
                (int) PullRequestReviewHandler.MAX_DIFF_BYTES + 200
            );
        }

        @Test
        @DisplayName("should truncate oversized diff without splitting multi-byte UTF-8 characters")
        void shouldTruncateWithoutSplittingUtf8() {
            // Build a diff that places a 3-byte UTF-8 character (€ = E2 82 AC) right at the boundary
            int padding = (int) PullRequestReviewHandler.MAX_DIFF_BYTES - 2;
            String largeDiff = "x".repeat(padding) + "€€€"; // € is 3 bytes, total > MAX_DIFF_BYTES
            stubDefaults();
            when(gitRepositoryManager.generateUnifiedDiff(123L, "main", "feature/auth-fix")).thenReturn(largeDiff);

            Map<String, byte[]> files = handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata()));

            // The result must be valid UTF-8 (no orphaned continuation bytes)
            String diff = new String(files.get(".context/diff.patch"), StandardCharsets.UTF_8);
            assertThat(diff).doesNotContain("\uFFFD"); // replacement character = broken UTF-8
            assertThat(diff).contains("[... diff truncated at 2 MB");
        }

        @Test
        @DisplayName("should not truncate diff exactly at limit")
        void shouldNotTruncateDiffExactlyAtLimit() {
            String exactDiff = "x".repeat((int) PullRequestReviewHandler.MAX_DIFF_BYTES);
            stubDefaults();
            when(gitRepositoryManager.generateUnifiedDiff(123L, "main", "feature/auth-fix")).thenReturn(exactDiff);

            Map<String, byte[]> files = handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata()));

            String diff = new String(files.get(".context/diff.patch"), StandardCharsets.UTF_8);
            assertThat(diff).doesNotContain("[... diff truncated");
            assertThat(diff).isEqualTo(exactDiff);
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
            // Workspace null-guard fires in step 5, AFTER git/DB calls in steps 1-4.
            // Stub those but NOT practiceRepository (which would be unused).
            when(gitRepositoryManager.readFilesAtCommit(eq(123L), eq("abc123def456"), anyLong())).thenReturn(Map.of());
            when(gitRepositoryManager.generateUnifiedDiff(123L, "main", "feature/auth-fix")).thenReturn("diff content");
            when(pullRequestRepository.findByIdWithRepository(456L)).thenReturn(Optional.empty());
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
        @DisplayName("should include practices.json with practice definitions")
        void shouldIncludePracticesJson() throws Exception {
            stubDefaults();

            Map<String, byte[]> files = handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata()));

            assertThat(files).containsKey(".context/practices.json");
            JsonNode practices = objectMapper.readTree(files.get(".context/practices.json"));
            assertThat(practices.isArray()).isTrue();
            assertThat(practices).hasSize(2);
            assertThat(practices.get(0).get("slug").asText()).isEqualTo("pr-description-quality");
            assertThat(practices.get(0).get("name").asText()).isEqualTo("PR Description Quality");
            assertThat(practices.get(0).get("description").asText()).isEqualTo(
                "PRs should have clear titles and descriptions explaining the change."
            );
            assertThat(practices.get(0).get("detection_prompt").asText()).isEqualTo(
                "Check if the PR has a meaningful title and description that explains the why."
            );
            // Category is null — key must be absent (not "category": null)
            assertThat(practices.get(0).has("category")).isFalse();
            // Second practice has no detection_prompt
            assertThat(practices.get(1).get("slug").asText()).isEqualTo("error-handling");
            assertThat(practices.get(1).has("detection_prompt")).isFalse();
            assertThat(practices.get(1).has("category")).isFalse();
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
        @DisplayName("should include practice category when present")
        void shouldIncludePracticeCategoryWhenPresent() throws Exception {
            Practice withCategory = createPractice("test-coverage", "Test Coverage", "Tests required.", null);
            withCategory.setCategory("testing");
            stubDefaults();
            when(practiceRepository.findByWorkspaceIdAndActiveTrue(WORKSPACE_ID)).thenReturn(List.of(withCategory));

            Map<String, byte[]> files = handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata()));

            JsonNode practices = objectMapper.readTree(files.get(".context/practices.json"));
            assertThat(practices.get(0).get("category").asText()).isEqualTo("testing");
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
            when(pullRequestRepository.findByIdWithRepository(456L)).thenReturn(Optional.of(pullRequest));

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
        @DisplayName("should reference workspace paths and pull request details")
        void shouldReferenceWorkspacePaths() {
            when(practiceRepository.findByWorkspaceIdAndActiveTrue(WORKSPACE_ID)).thenReturn(samplePractices());

            String prompt = handler.buildPrompt(jobWithMetadata(sampleJobMetadata()));

            assertThat(prompt).contains("/workspace/repo/");
            assertThat(prompt).contains("/workspace/.context/diff.patch");
            assertThat(prompt).contains("/workspace/.context/metadata.json");
            assertThat(prompt).contains("/workspace/.context/practices.json");
            assertThat(prompt).contains("/workspace/.output/result.json");
            assertThat(prompt).contains("#42");
            assertThat(prompt).contains("owner/repo");
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

            // Pure JSON instruction
            assertThat(prompt).contains("ONLY a JSON object");
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
            assertThat(prompt).contains("NOT_APPLICABLE`: the practice does not apply");
            assertThat(prompt).contains("NEEDS_REVIEW`: borderline case");
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
            when(deliveryService.deliver(eq(job), any())).thenReturn(new DeliveryResult(1, 0, 0, 0));

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
            when(deliveryService.deliver(eq(job), any())).thenReturn(new DeliveryResult(1, 0, 0, 0));

            handler.deliver(job);

            var captor = ArgumentCaptor.forClass(List.class);
            verify(deliveryService).deliver(eq(job), captor.capture());
            var findings = (List<PracticeDetectionResultParser.ValidatedFinding>) captor.getValue();
            // Only the valid finding should be passed through
            assertThat(findings).hasSize(1);
            assertThat(findings.get(0).practiceSlug()).isEqualTo("pr-description-quality");
        }

        @Test
        @DisplayName("should call commentPoster when review_comment is present in output")
        void shouldCallCommentPosterWhenReviewCommentPresent() {
            String rawOutput = """
                {
                  "review_comment": "Great code!",
                  "summary": "Looks good overall",
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
            when(deliveryService.deliver(eq(job), any())).thenReturn(new DeliveryResult(1, 0, 0, 0));
            when(commentPoster.postComment(eq(job), eq("Great code!"), eq("Looks good overall"))).thenReturn(
                "IC_abc123"
            );

            handler.deliver(job);

            verify(commentPoster).postComment(job, "Great code!", "Looks good overall");
            assertThat(job.getDeliveryCommentId()).isEqualTo("IC_abc123");
        }

        @Test
        @DisplayName("should skip comment posting when review_comment is absent")
        void shouldSkipCommentPostingWhenAbsent() {
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
            when(deliveryService.deliver(eq(job), any())).thenReturn(new DeliveryResult(1, 0, 0, 0));

            handler.deliver(job);

            verifyNoInteractions(commentPoster);
        }

        @Test
        @DisplayName("should not propagate comment posting failures (soft failure isolation)")
        void shouldNotPropagateCommentPostingFailures() {
            String rawOutput = """
                {
                  "review_comment": "Review text",
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
            when(commentPoster.postComment(eq(job), any(), any())).thenThrow(
                new JobDeliveryException("GitHub API timeout")
            );
            when(deliveryService.deliver(eq(job), any())).thenReturn(new DeliveryResult(1, 0, 0, 0));

            // Should NOT throw — comment failure is soft
            handler.deliver(job);

            // Practice detection should still proceed
            verify(deliveryService).deliver(eq(job), any());
        }

        @Test
        @DisplayName("should not propagate RuntimeException from comment posting (generic exception)")
        void shouldNotPropagateRuntimeExceptionFromCommentPosting() {
            String rawOutput = """
                {
                  "review_comment": "Review text",
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
            when(commentPoster.postComment(eq(job), any(), any())).thenThrow(
                new NullPointerException("Unexpected null from GraphQL response")
            );
            when(deliveryService.deliver(eq(job), any())).thenReturn(new DeliveryResult(1, 0, 0, 0));

            // Should NOT throw — comment failure is soft, even for unchecked exceptions
            handler.deliver(job);

            // Practice detection should still proceed
            verify(deliveryService).deliver(eq(job), any());
            // Comment ID should NOT be set when posting fails
            assertThat(job.getDeliveryCommentId()).isNull();
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
        }

        @Test
        @DisplayName("should skip comment posting when review_comment is numeric (non-textual)")
        void shouldSkipWhenReviewCommentIsNumeric() {
            String rawOutput = """
                {
                  "review_comment": 42,
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
            when(deliveryService.deliver(eq(job), any())).thenReturn(new DeliveryResult(1, 0, 0, 0));

            handler.deliver(job);

            verifyNoInteractions(commentPoster);
        }

        @Test
        @DisplayName("should skip comment posting when review_comment is JSON null")
        void shouldSkipWhenReviewCommentIsJsonNull() {
            String rawOutput = """
                {
                  "review_comment": null,
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
            when(deliveryService.deliver(eq(job), any())).thenReturn(new DeliveryResult(1, 0, 0, 0));

            handler.deliver(job);

            verifyNoInteractions(commentPoster);
        }
    }

    @Nested
    @DisplayName("findUtf8CharBoundary")
    class FindUtf8CharBoundary {

        @Test
        @DisplayName("should not split multi-byte characters")
        void shouldNotSplitMultiByteCharacters() {
            // "é" is 2 bytes in UTF-8: 0xC3 0xA9
            byte[] data = "aaé".getBytes(StandardCharsets.UTF_8);
            // data = [0x61, 0x61, 0xC3, 0xA9] — 4 bytes

            // Cutting at byte 3 would split the "é" — should back up to byte 2
            int boundary = PullRequestReviewHandler.findUtf8CharBoundary(data, 3);
            assertThat(boundary).isEqualTo(2);

            // Cutting at byte 4 is safe (after the full character)
            boundary = PullRequestReviewHandler.findUtf8CharBoundary(data, 4);
            assertThat(boundary).isEqualTo(4);
        }

        @Test
        @DisplayName("should handle limit beyond data length")
        void shouldHandleLimitBeyondData() {
            byte[] data = "abc".getBytes(StandardCharsets.UTF_8);
            assertThat(PullRequestReviewHandler.findUtf8CharBoundary(data, 100)).isEqualTo(3);
        }

        @Test
        @DisplayName("should not split 4-byte characters (emoji)")
        void shouldNotSplitFourByteCharacters() {
            // "😀" is 4 bytes in UTF-8: F0 9F 98 80
            byte[] data = "aa\uD83D\uDE00".getBytes(StandardCharsets.UTF_8);
            // data = [0x61, 0x61, 0xF0, 0x9F, 0x98, 0x80] — 6 bytes

            // Cutting at byte 3, 4, or 5 would split the emoji — should back up to byte 2
            assertThat(PullRequestReviewHandler.findUtf8CharBoundary(data, 3)).isEqualTo(2);
            assertThat(PullRequestReviewHandler.findUtf8CharBoundary(data, 4)).isEqualTo(2);
            assertThat(PullRequestReviewHandler.findUtf8CharBoundary(data, 5)).isEqualTo(2);

            // Cutting at byte 6 is safe (after the full character)
            assertThat(PullRequestReviewHandler.findUtf8CharBoundary(data, 6)).isEqualTo(6);
        }
    }
}
