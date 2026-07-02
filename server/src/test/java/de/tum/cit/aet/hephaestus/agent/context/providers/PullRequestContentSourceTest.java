package de.tum.cit.aet.hephaestus.agent.context.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import de.tum.cit.aet.hephaestus.practices.observation.DeveloperHistoryProvider;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class PullRequestContentSourceTest extends BaseUnitTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private GitRepositoryManager gitRepositoryManager;

    @Mock
    private PullRequestRepository pullRequestRepository;

    @Mock
    private PullRequestReviewCommentRepository reviewCommentRepository;

    @Mock
    private DeveloperHistoryProvider developerHistoryProvider;

    @Mock
    private GitDiffOperations gitDiffOperations;

    @Mock
    private de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService connectionService;

    private static final Long WORKSPACE_ID = 99L;

    private PullRequestContentSource provider;

    @BeforeEach
    void setUp() {
        provider = new PullRequestContentSource(
            objectMapper,
            gitRepositoryManager,
            pullRequestRepository,
            reviewCommentRepository,
            developerHistoryProvider,
            gitDiffOperations,
            connectionService,
            List.of()
        );
    }

    private ObjectNode sampleMetadata() {
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

    private AgentJob jobWith(ObjectNode metadata) {
        var job = new AgentJob();
        job.setMetadata(metadata);
        Workspace workspace = new Workspace();
        workspace.setId(WORKSPACE_ID);
        job.setWorkspace(workspace);
        return job;
    }

    private ContextRequest.PracticeReviewRequest request(ObjectNode metadata) {
        return new ContextRequest.PracticeReviewRequest(jobWith(metadata));
    }

    private void stubGit() {
        lenient().when(gitRepositoryManager.isEnabled()).thenReturn(true);
        lenient().when(gitRepositoryManager.isRepositoryCloned(123L)).thenReturn(true);
        lenient()
            .when(gitRepositoryManager.getRepositoryPath(123L))
            .thenReturn(Path.of("/tmp/hephaestus-git-repos/123"));
    }

    @Nested
    class Supports {

        @Test
        void supportsPracticeReview() {
            assertThat(provider.supports(request(sampleMetadata()))).isTrue();
        }
    }

    @Nested
    class MetadataAndComments {

        @Test
        void writesMetadataJson() throws Exception {
            stubGit();
            when(pullRequestRepository.findByIdWithAllForGate(456L)).thenReturn(Optional.empty());
            when(reviewCommentRepository.findByPullRequestIdWithAuthorOrderByCreatedAt(456L)).thenReturn(List.of());

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            assertThat(files).containsKey("inputs/context/metadata.json");
            JsonNode metadataJson = objectMapper.readTree(files.get("inputs/context/metadata.json"));
            assertThat(metadataJson.get("pr_number").asInt()).isEqualTo(42);
            assertThat(metadataJson.get("repository_full_name").asString()).isEqualTo("owner/repo");
            assertThat(metadataJson.get("enriched").asBoolean()).isFalse();
        }

        @Test
        void enrichesFromDb() throws Exception {
            PullRequest pr = new PullRequest();
            pr.setTitle("Fix authentication bug");
            pr.setBody("This PR fixes the login issue");
            pr.setState(Issue.State.OPEN);
            pr.setAdditions(10);
            User author = new User();
            author.setLogin("testuser");
            pr.setAuthor(author);

            stubGit();
            when(pullRequestRepository.findByIdWithAllForGate(456L)).thenReturn(Optional.of(pr));
            when(reviewCommentRepository.findByPullRequestIdWithAuthorOrderByCreatedAt(456L)).thenReturn(List.of());

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            JsonNode metadataJson = objectMapper.readTree(files.get("inputs/context/metadata.json"));
            assertThat(metadataJson.get("enriched").asBoolean()).isTrue();
            assertThat(metadataJson.get("title").asString()).isEqualTo("Fix authentication bug");
            assertThat(metadataJson.get("author").asString()).isEqualTo("testuser");
            assertThat(metadataJson.get("additions").asInt()).isEqualTo(10);
        }

        @Test
        void writesCommentsJson() throws Exception {
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

            stubGit();
            when(pullRequestRepository.findByIdWithAllForGate(456L)).thenReturn(Optional.empty());
            when(reviewCommentRepository.findByPullRequestIdWithAuthorOrderByCreatedAt(456L)).thenReturn(
                List.of(full, minimal)
            );

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            JsonNode comments = objectMapper.readTree(files.get("inputs/context/comments.json"));
            assertThat(comments).hasSize(2);
            assertThat(comments.get(0).get("created_at").asString()).isEqualTo("2025-06-01T12:00:00Z");
            assertThat(comments.get(0).get("author").asString()).isEqualTo("reviewer");
            assertThat(comments.get(1).has("author")).isFalse();
        }

        @Test
        void truncatesComments() throws Exception {
            var comments = new java.util.ArrayList<PullRequestReviewComment>();
            for (int i = 0; i < PullRequestContentSource.MAX_COMMENTS + 100; i++) {
                PullRequestReviewComment c = new PullRequestReviewComment();
                c.setPath("file.java");
                c.setLine(i);
                c.setBody("Comment " + i);
                comments.add(c);
            }

            stubGit();
            when(pullRequestRepository.findByIdWithAllForGate(456L)).thenReturn(Optional.empty());
            when(reviewCommentRepository.findByPullRequestIdWithAuthorOrderByCreatedAt(456L)).thenReturn(comments);

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            JsonNode commentsJson = objectMapper.readTree(files.get("inputs/context/comments.json"));
            assertThat(commentsJson).hasSize(PullRequestContentSource.MAX_COMMENTS);
            assertThat(commentsJson.get(0).get("body").asString()).isEqualTo("Comment 100");
        }
    }

    @Nested
    class DeveloperHistory {

        @Test
        void includesDeveloperHistory() {
            PullRequest pr = new PullRequest();
            pr.setTitle("Fix bug");
            User author = new User();
            author.setId(42L);
            author.setLogin("alice");
            pr.setAuthor(author);

            stubGit();
            when(pullRequestRepository.findByIdWithAllForGate(456L)).thenReturn(Optional.of(pr));
            when(reviewCommentRepository.findByPullRequestIdWithAuthorOrderByCreatedAt(456L)).thenReturn(List.of());

            byte[] historyJson =
                "[{\"practice\":\"error-handling\",\"good\":0,\"bad\":3,\"lastSeen\":\"2025-06-01T12:00:00Z\"}]".getBytes(
                    StandardCharsets.UTF_8
                );
            when(developerHistoryProvider.buildHistoryJson(42L, WORKSPACE_ID)).thenReturn(Optional.of(historyJson));

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            assertThat(files.get("inputs/context/contributor_history.json")).isEqualTo(historyJson);
        }

        @Test
        void omitsWhenEmpty() {
            PullRequest pr = new PullRequest();
            pr.setTitle("New PR");
            User author = new User();
            author.setId(42L);
            author.setLogin("alice");
            pr.setAuthor(author);

            stubGit();
            when(pullRequestRepository.findByIdWithAllForGate(456L)).thenReturn(Optional.of(pr));
            when(reviewCommentRepository.findByPullRequestIdWithAuthorOrderByCreatedAt(456L)).thenReturn(List.of());
            when(developerHistoryProvider.buildHistoryJson(42L, WORKSPACE_ID)).thenReturn(Optional.empty());

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            assertThat(files).doesNotContainKey("inputs/context/contributor_history.json");
        }

        @Test
        void continuesWhenHistoryThrows() {
            PullRequest pr = new PullRequest();
            pr.setTitle("Broken history");
            User author = new User();
            author.setId(42L);
            author.setLogin("alice");
            pr.setAuthor(author);

            stubGit();
            when(pullRequestRepository.findByIdWithAllForGate(456L)).thenReturn(Optional.of(pr));
            when(reviewCommentRepository.findByPullRequestIdWithAuthorOrderByCreatedAt(456L)).thenReturn(List.of());
            when(developerHistoryProvider.buildHistoryJson(42L, WORKSPACE_ID)).thenThrow(
                new RuntimeException("DB connection timeout")
            );

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            assertThat(files).doesNotContainKey("inputs/context/contributor_history.json");
            assertThat(files).containsKey("inputs/context/metadata.json");
        }

        @Test
        void skipsWhenNoAuthor() {
            PullRequest pr = new PullRequest();
            pr.setTitle("Orphan PR");

            stubGit();
            when(pullRequestRepository.findByIdWithAllForGate(456L)).thenReturn(Optional.of(pr));
            when(reviewCommentRepository.findByPullRequestIdWithAuthorOrderByCreatedAt(456L)).thenReturn(List.of());

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            verifyNoInteractions(developerHistoryProvider);
            assertThat(files).doesNotContainKey("inputs/context/contributor_history.json");
        }
    }

    @Nested
    class DiffPrecompute {

        private final String repoPath = "/tmp/hephaestus-git-repos/123";

        @Test
        void computeAndStoreDiffSummary_parsesPerFileChunks() throws Exception {
            // The diff_summary.md parser is driven directly from an annotated diff.patch (no git needed).
            String annotated =
                "[L1] diff --git a/src/A.java b/src/A.java\n" +
                "[L1] +line a1\n" +
                "[L2] +line a2\n" +
                "[L1] diff --git a/src/B.java b/src/B.java\n" +
                "[L1] +line b1\n";
            Map<String, byte[]> files = new LinkedHashMap<>();
            files.put("inputs/context/diff.patch", annotated.getBytes(StandardCharsets.UTF_8));

            provider.computeAndStoreDiffSummary(files);

            assertThat(files).containsKey("inputs/context/diff_summary.md");
            String summary = new String(files.get("inputs/context/diff_summary.md"), StandardCharsets.UTF_8);
            assertThat(summary).contains("**2 files changed**");
            assertThat(summary).contains("`src/A.java`");
            assertThat(summary).contains("`src/B.java`");
        }

        @Test
        void computeAndStoreDiffSummary_emptyDiffPatch_writesNothing() {
            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.computeAndStoreDiffSummary(files); // no diff.patch present at all
            assertThat(files).doesNotContainKey("inputs/context/diff_summary.md");

            files.put("inputs/context/diff.patch", new byte[0]); // present but empty
            provider.computeAndStoreDiffSummary(files);
            assertThat(files).doesNotContainKey("inputs/context/diff_summary.md");
        }

        @Test
        void emptyDiff_abortsWithJobPreparationException() {
            stubGit();
            when(pullRequestRepository.findByIdWithAllForGate(456L)).thenReturn(Optional.empty());
            lenient()
                .when(reviewCommentRepository.findByPullRequestIdWithAuthorOrderByCreatedAt(456L))
                .thenReturn(List.of());
            // A resolvable range but a blank diff → "Empty diff: no changed files…".
            when(
                gitDiffOperations.resolveDiffRange(Path.of(repoPath), "main", "feature/auth-fix", "abc123def456")
            ).thenReturn(new String[] { "main", "abc123def456" });
            when(gitDiffOperations.diffStat(Path.of(repoPath), "main", "abc123def456")).thenReturn("");
            when(gitDiffOperations.diff(Path.of(repoPath), "main", "abc123def456")).thenReturn("   ");

            assertThatThrownBy(() -> provider.contribute(request(sampleMetadata()), new LinkedHashMap<>()))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("Empty diff");
        }

        @Test
        void headVerifiedButRangeUnresolvable_abortsWithJobPreparationException() {
            stubGit();
            when(pullRequestRepository.findByIdWithAllForGate(456L)).thenReturn(Optional.empty());
            lenient()
                .when(reviewCommentRepository.findByPullRequestIdWithAuthorOrderByCreatedAt(456L))
                .thenReturn(List.of());
            // headVerified = true (commit present) but every range-resolution strategy fails → hard abort.
            when(gitRepositoryManager.commitExists(123L, "abc123def456")).thenReturn(true);
            when(
                gitDiffOperations.resolveDiffRange(Path.of(repoPath), "main", "feature/auth-fix", "abc123def456")
            ).thenReturn(null);

            assertThatThrownBy(() -> provider.contribute(request(sampleMetadata()), new LinkedHashMap<>()))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("all resolution strategies failed");
        }

        @Test
        void realDiff_writesAnnotatedPatchAndSummary() throws Exception {
            stubGit();
            when(pullRequestRepository.findByIdWithAllForGate(456L)).thenReturn(Optional.empty());
            when(reviewCommentRepository.findByPullRequestIdWithAuthorOrderByCreatedAt(456L)).thenReturn(List.of());
            when(
                gitDiffOperations.resolveDiffRange(Path.of(repoPath), "main", "feature/auth-fix", "abc123def456")
            ).thenReturn(new String[] { "main", "abc123def456" });
            when(gitDiffOperations.diffStat(Path.of(repoPath), "main", "abc123def456")).thenReturn("1 file changed");
            when(gitDiffOperations.diff(Path.of(repoPath), "main", "abc123def456")).thenReturn(
                "diff --git a/src/A.java b/src/A.java\n@@ -1,1 +1,2 @@\n context\n+added\n"
            );

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            assertThat(files).containsKey("inputs/context/diff.patch");
            assertThat(files).containsKey("inputs/context/diff_stat.txt");
            assertThat(files).containsKey("inputs/context/diff_summary.md");
            String patch = new String(files.get("inputs/context/diff.patch"), StandardCharsets.UTF_8);
            assertThat(patch).contains("[L2] +added");
        }
    }

    @Nested
    class RepositoryAvailability {

        @Test
        void throwsWhenCheckoutMissing() {
            lenient().when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(gitRepositoryManager.isRepositoryCloned(123L)).thenReturn(false);

            assertThatThrownBy(() -> provider.contribute(request(sampleMetadata()), new LinkedHashMap<>()))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("Repository checkout is not available locally for bind-mount");
        }

        @Test
        void throwsWhenMetadataMissing() {
            var job = new AgentJob();
            assertThatThrownBy(() ->
                provider.contribute(new ContextRequest.PracticeReviewRequest(job), new LinkedHashMap<>())
            )
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("no metadata");
        }
    }
}
