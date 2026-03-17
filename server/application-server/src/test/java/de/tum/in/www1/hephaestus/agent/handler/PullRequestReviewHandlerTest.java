package de.tum.in.www1.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.agent.AgentJobType;
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
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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

    private PullRequestReviewHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PullRequestReviewHandler(
            objectMapper,
            gitRepositoryManager,
            pullRequestRepository,
            reviewCommentRepository
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
        return job;
    }

    /** Stub all git/DB calls to return minimal valid data. */
    private void stubDefaults() {
        when(gitRepositoryManager.readFilesAtCommit(eq(123L), eq("abc123def456"), anyLong())).thenReturn(Map.of());
        when(gitRepositoryManager.generateUnifiedDiff(123L, "main", "feature/auth-fix")).thenReturn("diff content");
        when(pullRequestRepository.findByIdWithRepository(456L)).thenReturn(Optional.empty());
        when(reviewCommentRepository.findByPullRequestIdWithAuthorOrderByCreatedAt(456L)).thenReturn(List.of());
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
        @DisplayName("should include repo files under repo/ prefix")
        void shouldIncludeRepoFiles() {
            Map<String, byte[]> repoFiles = Map.of(
                "src/Main.java",
                "public class Main {}".getBytes(),
                "README.md",
                "# My Project".getBytes()
            );
            when(gitRepositoryManager.readFilesAtCommit(eq(123L), eq("abc123def456"), anyLong())).thenReturn(repoFiles);
            when(gitRepositoryManager.generateUnifiedDiff(123L, "main", "feature/auth-fix")).thenReturn("diff content");
            when(pullRequestRepository.findByIdWithRepository(456L)).thenReturn(Optional.empty());
            when(reviewCommentRepository.findByPullRequestIdWithAuthorOrderByCreatedAt(456L)).thenReturn(List.of());

            Map<String, byte[]> files = handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata()));

            assertThat(files).containsKey("repo/src/Main.java");
            assertThat(files).containsKey("repo/README.md");
            assertThat(new String(files.get("repo/src/Main.java"))).isEqualTo("public class Main {}");
        }

        @Test
        @DisplayName("should include diff patch")
        void shouldIncludeDiffPatch() {
            String diffContent = "--- a/file.java\n+++ b/file.java\n@@ -1,3 +1,4 @@\n+new line\n";
            when(gitRepositoryManager.readFilesAtCommit(eq(123L), anyString(), anyLong())).thenReturn(Map.of());
            when(gitRepositoryManager.generateUnifiedDiff(123L, "main", "feature/auth-fix")).thenReturn(diffContent);
            when(pullRequestRepository.findByIdWithRepository(456L)).thenReturn(Optional.empty());
            when(reviewCommentRepository.findByPullRequestIdWithAuthorOrderByCreatedAt(456L)).thenReturn(List.of());

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

            when(gitRepositoryManager.readFilesAtCommit(eq(123L), anyString(), anyLong())).thenReturn(Map.of());
            when(gitRepositoryManager.generateUnifiedDiff(123L, "main", "feature/auth-fix")).thenReturn("diff");
            when(pullRequestRepository.findByIdWithRepository(456L)).thenReturn(Optional.of(pullRequest));
            when(reviewCommentRepository.findByPullRequestIdWithAuthorOrderByCreatedAt(456L)).thenReturn(List.of());

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
            when(gitRepositoryManager.readFilesAtCommit(eq(123L), anyString(), anyLong())).thenReturn(Map.of());
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
            when(gitRepositoryManager.readFilesAtCommit(eq(123L), anyString(), anyLong())).thenReturn(Map.of());
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
            when(gitRepositoryManager.readFilesAtCommit(eq(123L), anyString(), anyLong())).thenReturn(Map.of());
            when(gitRepositoryManager.generateUnifiedDiff(123L, "main", "feature/auth-fix")).thenReturn(largeDiff);
            when(pullRequestRepository.findByIdWithRepository(456L)).thenReturn(Optional.empty());
            when(reviewCommentRepository.findByPullRequestIdWithAuthorOrderByCreatedAt(456L)).thenReturn(List.of());

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
            when(gitRepositoryManager.readFilesAtCommit(eq(123L), anyString(), anyLong())).thenReturn(Map.of());
            when(gitRepositoryManager.generateUnifiedDiff(123L, "main", "feature/auth-fix")).thenReturn(largeDiff);
            when(pullRequestRepository.findByIdWithRepository(456L)).thenReturn(Optional.empty());
            when(reviewCommentRepository.findByPullRequestIdWithAuthorOrderByCreatedAt(456L)).thenReturn(List.of());

            Map<String, byte[]> files = handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata()));

            // The result must be valid UTF-8 (no orphaned continuation bytes)
            String diff = new String(files.get(".context/diff.patch"), StandardCharsets.UTF_8);
            assertThat(diff).doesNotContain("\uFFFD"); // replacement character = broken UTF-8
            assertThat(diff).contains("[... diff truncated at 2 MB");
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

            when(gitRepositoryManager.readFilesAtCommit(eq(123L), anyString(), anyLong())).thenReturn(Map.of());
            when(gitRepositoryManager.generateUnifiedDiff(123L, "main", "feature/auth-fix")).thenReturn("diff");
            when(pullRequestRepository.findByIdWithRepository(456L)).thenReturn(Optional.empty());
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
        @DisplayName("should include created_at in review comments")
        void shouldIncludeCreatedAtInComments() throws Exception {
            PullRequestReviewComment comment = new PullRequestReviewComment();
            comment.setPath("src/Main.java");
            comment.setLine(10);
            comment.setBody("Fix this");
            comment.setCreatedAt(Instant.parse("2025-06-01T12:00:00Z"));
            User author = new User();
            author.setLogin("reviewer");
            comment.setAuthor(author);

            when(gitRepositoryManager.readFilesAtCommit(eq(123L), anyString(), anyLong())).thenReturn(Map.of());
            when(gitRepositoryManager.generateUnifiedDiff(123L, "main", "feature/auth-fix")).thenReturn("diff");
            when(pullRequestRepository.findByIdWithRepository(456L)).thenReturn(Optional.empty());
            when(reviewCommentRepository.findByPullRequestIdWithAuthorOrderByCreatedAt(456L)).thenReturn(
                List.of(comment)
            );

            Map<String, byte[]> files = handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata()));

            JsonNode comments = objectMapper.readTree(files.get(".context/comments.json"));
            assertThat(comments.get(0).get("created_at").asText()).isEqualTo("2025-06-01T12:00:00Z");
            assertThat(comments.get(0).get("author").asText()).isEqualTo("reviewer");
        }

        @Test
        @DisplayName("should handle comments with null createdAt")
        void shouldHandleCommentsWithNullCreatedAt() throws Exception {
            PullRequestReviewComment comment = new PullRequestReviewComment();
            comment.setPath("src/Main.java");
            comment.setLine(5);
            comment.setBody("Old comment");
            comment.setCreatedAt(null);
            comment.setAuthor(null);

            when(gitRepositoryManager.readFilesAtCommit(eq(123L), anyString(), anyLong())).thenReturn(Map.of());
            when(gitRepositoryManager.generateUnifiedDiff(123L, "main", "feature/auth-fix")).thenReturn("diff");
            when(pullRequestRepository.findByIdWithRepository(456L)).thenReturn(Optional.empty());
            when(reviewCommentRepository.findByPullRequestIdWithAuthorOrderByCreatedAt(456L)).thenReturn(
                List.of(comment)
            );

            Map<String, byte[]> files = handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata()));

            JsonNode comments = objectMapper.readTree(files.get(".context/comments.json"));
            assertThat(comments).hasSize(1);
            assertThat(comments.get(0).has("created_at")).isFalse();
            assertThat(comments.get(0).has("author")).isFalse();
        }
    }

    @Nested
    @DisplayName("buildPrompt")
    class BuildPrompt {

        @Test
        @DisplayName("should reference workspace paths and pull request details")
        void shouldReferenceWorkspacePaths() {
            String prompt = handler.buildPrompt(jobWithMetadata(sampleJobMetadata()));

            assertThat(prompt).contains("/workspace/repo/");
            assertThat(prompt).contains("/workspace/.context/diff.patch");
            assertThat(prompt).contains("/workspace/.context/metadata.json");
            assertThat(prompt).contains("/workspace/.output/result.json");
            assertThat(prompt).contains("#42");
            assertThat(prompt).contains("owner/repo");
        }

        @Test
        @DisplayName("should not be blank")
        void shouldNotBeBlank() {
            String prompt = handler.buildPrompt(jobWithMetadata(sampleJobMetadata()));

            assertThat(prompt).isNotBlank();
        }

        @Test
        @DisplayName("should handle percent characters in metadata without format string errors")
        void shouldHandlePercentInMetadata() {
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
    }

    @Nested
    @DisplayName("deliver")
    class Deliver {

        @Test
        @DisplayName("should be a no-op by default")
        void shouldBeNoOp() {
            // deliver() should not throw — it's a no-op
            handler.deliver(new AgentJob());
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
    }
}
