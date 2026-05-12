package de.tum.in.www1.hephaestus.agent.context.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.agent.context.ContextRequest;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.in.www1.hephaestus.agent.job.AgentJob;
import de.tum.in.www1.hephaestus.gitprovider.git.GitRepositoryManager;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.practices.finding.ContributorHistoryProvider;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@DisplayName("PullRequestContentProvider")
class PullRequestContentProviderTest extends BaseUnitTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private GitRepositoryManager gitRepositoryManager;

    @Mock
    private PullRequestRepository pullRequestRepository;

    @Mock
    private PullRequestReviewCommentRepository reviewCommentRepository;

    @Mock
    private ContributorHistoryProvider contributorHistoryProvider;

    @Mock
    private GitDiffOperations gitDiffOperations;

    private static final Long WORKSPACE_ID = 99L;

    private PullRequestContentProvider provider;

    @BeforeEach
    void setUp() {
        provider = new PullRequestContentProvider(
            objectMapper,
            gitRepositoryManager,
            pullRequestRepository,
            reviewCommentRepository,
            contributorHistoryProvider,
            gitDiffOperations,
            null
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
    @DisplayName("supports")
    class Supports {

        @Test
        @DisplayName("supports PracticeReviewRequest")
        void supportsPracticeReview() {
            assertThat(provider.supports(request(sampleMetadata()))).isTrue();
        }
    }

    @Nested
    @DisplayName("metadata + comments")
    class MetadataAndComments {

        @Test
        @DisplayName("writes context/target/metadata.json with job-metadata fields")
        void writesMetadataJson() throws Exception {
            stubGit();
            when(pullRequestRepository.findByIdWithAllForGate(456L)).thenReturn(Optional.empty());
            when(reviewCommentRepository.findByPullRequestIdWithAuthorOrderByCreatedAt(456L)).thenReturn(List.of());

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            assertThat(files).containsKey("context/target/metadata.json");
            JsonNode metadataJson = objectMapper.readTree(files.get("context/target/metadata.json"));
            assertThat(metadataJson.get("pr_number").asInt()).isEqualTo(42);
            assertThat(metadataJson.get("repository_full_name").asText()).isEqualTo("owner/repo");
            assertThat(metadataJson.get("enriched").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("enriches metadata from DB when the pull request is present")
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

            JsonNode metadataJson = objectMapper.readTree(files.get("context/target/metadata.json"));
            assertThat(metadataJson.get("enriched").asBoolean()).isTrue();
            assertThat(metadataJson.get("title").asText()).isEqualTo("Fix authentication bug");
            assertThat(metadataJson.get("author").asText()).isEqualTo("testuser");
            assertThat(metadataJson.get("additions").asInt()).isEqualTo(10);
        }

        @Test
        @DisplayName("writes comments.json with createdAt and author when present")
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

            JsonNode comments = objectMapper.readTree(files.get("context/target/comments.json"));
            assertThat(comments).hasSize(2);
            assertThat(comments.get(0).get("created_at").asText()).isEqualTo("2025-06-01T12:00:00Z");
            assertThat(comments.get(0).get("author").asText()).isEqualTo("reviewer");
            assertThat(comments.get(1).has("author")).isFalse();
        }

        @Test
        @DisplayName("truncates comments to MAX_COMMENTS keeping the most recent")
        void truncatesComments() throws Exception {
            var comments = new java.util.ArrayList<PullRequestReviewComment>();
            for (int i = 0; i < PullRequestContentProvider.MAX_COMMENTS + 100; i++) {
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

            JsonNode commentsJson = objectMapper.readTree(files.get("context/target/comments.json"));
            assertThat(commentsJson).hasSize(PullRequestContentProvider.MAX_COMMENTS);
            assertThat(commentsJson.get(0).get("body").asText()).isEqualTo("Comment 100");
        }
    }

    @Nested
    @DisplayName("contributor history")
    class ContributorHistory {

        @Test
        @DisplayName("includes contributor_history.json when the provider returns data")
        void includesContributorHistory() {
            PullRequest pr = new PullRequest();
            pr.setTitle("Fix bug");
            User author = new User();
            author.setId(42L);
            author.setLogin("alice");
            pr.setAuthor(author);

            stubGit();
            when(pullRequestRepository.findByIdWithAllForGate(456L)).thenReturn(Optional.of(pr));
            when(reviewCommentRepository.findByPullRequestIdWithAuthorOrderByCreatedAt(456L)).thenReturn(List.of());

            byte[] historyJson = "[{\"practice\":\"error-handling\",\"negative\":3}]".getBytes(StandardCharsets.UTF_8);
            when(contributorHistoryProvider.buildHistoryJson(42L, WORKSPACE_ID)).thenReturn(Optional.of(historyJson));

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            assertThat(files.get("context/target/contributor_history.json")).isEqualTo(historyJson);
        }

        @Test
        @DisplayName("omits contributor_history.json when the provider returns empty")
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
            when(contributorHistoryProvider.buildHistoryJson(42L, WORKSPACE_ID)).thenReturn(Optional.empty());

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            assertThat(files).doesNotContainKey("context/target/contributor_history.json");
        }

        @Test
        @DisplayName("continues gracefully when contributor history provider throws")
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
            when(contributorHistoryProvider.buildHistoryJson(42L, WORKSPACE_ID)).thenThrow(
                new RuntimeException("DB connection timeout")
            );

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            assertThat(files).doesNotContainKey("context/target/contributor_history.json");
            assertThat(files).containsKey("context/target/metadata.json");
        }

        @Test
        @DisplayName("skips contributor history when PR has no author")
        void skipsWhenNoAuthor() {
            PullRequest pr = new PullRequest();
            pr.setTitle("Orphan PR");

            stubGit();
            when(pullRequestRepository.findByIdWithAllForGate(456L)).thenReturn(Optional.of(pr));
            when(reviewCommentRepository.findByPullRequestIdWithAuthorOrderByCreatedAt(456L)).thenReturn(List.of());

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            verifyNoInteractions(contributorHistoryProvider);
            assertThat(files).doesNotContainKey("context/target/contributor_history.json");
        }
    }

    @Nested
    @DisplayName("repository availability")
    class RepositoryAvailability {

        @Test
        @DisplayName("throws when the local checkout is missing")
        void throwsWhenCheckoutMissing() {
            lenient().when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(gitRepositoryManager.isRepositoryCloned(123L)).thenReturn(false);

            assertThatThrownBy(() -> provider.contribute(request(sampleMetadata()), new LinkedHashMap<>()))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("Repository checkout is not available locally for bind-mount");
        }

        @Test
        @DisplayName("throws when metadata is missing")
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
