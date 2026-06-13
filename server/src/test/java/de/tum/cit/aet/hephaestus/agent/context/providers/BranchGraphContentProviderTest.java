package de.tum.cit.aet.hephaestus.agent.context.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager.CommitInfo;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class BranchGraphContentProviderTest extends BaseUnitTest {

    private static final String FILE_KEY = "inputs/context/branch_graph.json";
    private static final Long REPO_ID = 123L;
    private static final Path REPO_PATH = Path.of("/tmp/hephaestus-git-repos/123");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private GitRepositoryManager gitRepositoryManager;

    @Mock
    private GitDiffOperations gitDiffOperations;

    private BranchGraphContentProvider provider;

    @BeforeEach
    void setUp() {
        provider = new BranchGraphContentProvider(objectMapper, gitRepositoryManager, gitDiffOperations);
    }

    private ObjectNode sampleMetadata() {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("repository_id", REPO_ID);
        metadata.put("pull_request_id", 456L);
        metadata.put("source_branch", "feature/auth-fix");
        metadata.put("target_branch", "main");
        metadata.put("commit_sha", "headsha000");
        return metadata;
    }

    private AgentJob jobWith(ObjectNode metadata) {
        var job = new AgentJob();
        job.setMetadata(metadata);
        Workspace workspace = new Workspace();
        workspace.setId(99L);
        job.setWorkspace(workspace);
        return job;
    }

    private ContextRequest.PracticeReviewRequest request(ObjectNode metadata) {
        return new ContextRequest.PracticeReviewRequest(jobWith(metadata));
    }

    private CommitInfo commit(String sha, String subject, String authorEmail) {
        return new CommitInfo(
            sha,
            subject,
            null,
            "Author Name",
            authorEmail,
            Instant.parse("2025-06-01T12:00:00Z"),
            "Committer Name",
            authorEmail,
            Instant.parse("2025-06-01T12:00:00Z"),
            1,
            0,
            1,
            List.of(),
            List.of()
        );
    }

    private void stubGitAvailable() {
        lenient().when(gitRepositoryManager.isEnabled()).thenReturn(true);
        lenient().when(gitRepositoryManager.isRepositoryCloned(REPO_ID)).thenReturn(true);
        lenient().when(gitRepositoryManager.getRepositoryPath(REPO_ID)).thenReturn(REPO_PATH);
    }

    @Nested
    class Contract {

        @Test
        void supportsPracticeReview() {
            assertThat(provider.supports(request(sampleMetadata()))).isTrue();
        }

        @Test
        void isBestEffort() {
            assertThat(provider.required()).isFalse();
        }
    }

    @Nested
    class HappyPath {

        @Test
        void writesBranchGraphWithMultiAuthorHint() throws Exception {
            stubGitAvailable();
            when(
                gitDiffOperations.resolveDiffRange(eq(REPO_PATH), eq("main"), eq("feature/auth-fix"), eq("headsha000"))
            ).thenReturn(new String[] { "mergebase111", "headsha000" });
            // A SUBSTANTIAL range (>= BRANCH_HINT_MIN_COMMITS) spanning two authors trips the hint.
            when(gitRepositoryManager.walkCommits(REPO_ID, "mergebase111", "headsha000")).thenReturn(
                List.of(
                    commit("c1", "Add login", "alice@example.com"),
                    commit("c2", "Fix token refresh", "bob@example.com"),
                    commit("c3", "Tidy imports", "alice@example.com"),
                    commit("c4", "Wire keychain", "bob@example.com"),
                    commit("c5", "Add account view", "alice@example.com"),
                    commit("c6", "Handle 401", "bob@example.com")
                )
            );

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            assertThat(files).containsKey(FILE_KEY);
            JsonNode json = objectMapper.readTree(files.get(FILE_KEY));
            assertThat(json.get("sourceBranch").asString()).isEqualTo("feature/auth-fix");
            assertThat(json.get("targetBranch").asString()).isEqualTo("main");
            assertThat(json.get("mergeBaseSha").asString()).isEqualTo("mergebase111");
            assertThat(json.get("commitsAhead").asInt()).isEqualTo(6);
            assertThat(json.get("distinctAuthorsInRange").asInt()).isEqualTo(2);
            assertThat(json.get("sampleSubjects").get(0).asString()).isEqualTo("Add login");
            assertThat(json.get("looksBranchedOffFeatureBranch").asBoolean()).isTrue();
        }

        @Test
        void releaseBranchSourceIsNeverFlagged() throws Exception {
            // A release/integration MR (develop -> main) legitimately carries a large multi-author range;
            // it must NOT trip the "branched off a feature branch" hint (MR578 false-positive).
            ObjectNode md = sampleMetadata();
            md.put("source_branch", "develop");
            md.put("target_branch", "main");
            stubGitAvailable();
            when(
                gitDiffOperations.resolveDiffRange(eq(REPO_PATH), eq("main"), eq("develop"), eq("headsha000"))
            ).thenReturn(new String[] { "mergebase111", "headsha000" });
            var commits = new java.util.ArrayList<CommitInfo>();
            for (int i = 0; i < 12; i++) {
                commits.add(commit("c" + i, "Release commit " + i, (i % 3 == 0 ? "alice" : "bob") + "@example.com"));
            }
            when(gitRepositoryManager.walkCommits(REPO_ID, "mergebase111", "headsha000")).thenReturn(commits);

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(md), files);

            JsonNode json = objectMapper.readTree(files.get(FILE_KEY));
            assertThat(json.get("commitsAhead").asInt()).isEqualTo(12);
            assertThat(json.get("distinctAuthorsInRange").asInt()).isGreaterThan(1);
            assertThat(json.get("looksBranchedOffFeatureBranch").asBoolean()).isFalse();
        }

        @Test
        void singleAuthorRangeIsNotFlagged() throws Exception {
            stubGitAvailable();
            when(gitDiffOperations.resolveDiffRange(any(), any(), any(), any())).thenReturn(
                new String[] { "mergebase111", "headsha000" }
            );
            when(gitRepositoryManager.walkCommits(REPO_ID, "mergebase111", "headsha000")).thenReturn(
                List.of(commit("c1", "Add login", "alice@example.com"), commit("c2", "Fix typo", "alice@example.com"))
            );

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            JsonNode json = objectMapper.readTree(files.get(FILE_KEY));
            assertThat(json.get("distinctAuthorsInRange").asInt()).isEqualTo(1);
            assertThat(json.get("looksBranchedOffFeatureBranch").asBoolean()).isFalse();
        }

        @Test
        void capsSampleSubjectsAtTwelve() throws Exception {
            stubGitAvailable();
            when(gitDiffOperations.resolveDiffRange(any(), any(), any(), any())).thenReturn(
                new String[] { "mergebase111", "headsha000" }
            );
            var commits = new java.util.ArrayList<CommitInfo>();
            for (int i = 0; i < 30; i++) {
                commits.add(commit("c" + i, "Subject " + i, "alice@example.com"));
            }
            when(gitRepositoryManager.walkCommits(REPO_ID, "mergebase111", "headsha000")).thenReturn(commits);

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            JsonNode json = objectMapper.readTree(files.get(FILE_KEY));
            assertThat(json.get("commitsAhead").asInt()).isEqualTo(30);
            assertThat(json.get("sampleSubjects")).hasSize(BranchGraphContentProvider.MAX_SAMPLE_SUBJECTS);
        }
    }

    @Nested
    class BestEffortAbstention {

        @Test
        void skipsWhenGitDisabled() {
            when(gitRepositoryManager.isEnabled()).thenReturn(false);

            Map<String, byte[]> files = new LinkedHashMap<>();
            assertThatCode(() -> provider.contribute(request(sampleMetadata()), files)).doesNotThrowAnyException();
            assertThat(files).doesNotContainKey(FILE_KEY);
        }

        @Test
        void skipsWhenRepoNotCloned() {
            lenient().when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(gitRepositoryManager.isRepositoryCloned(REPO_ID)).thenReturn(false);

            Map<String, byte[]> files = new LinkedHashMap<>();
            assertThatCode(() -> provider.contribute(request(sampleMetadata()), files)).doesNotThrowAnyException();
            assertThat(files).doesNotContainKey(FILE_KEY);
        }

        @Test
        void skipsWhenRangeUnresolved() {
            stubGitAvailable();
            when(gitDiffOperations.resolveDiffRange(any(), any(), any(), any())).thenReturn(null);

            Map<String, byte[]> files = new LinkedHashMap<>();
            assertThatCode(() -> provider.contribute(request(sampleMetadata()), files)).doesNotThrowAnyException();
            assertThat(files).doesNotContainKey(FILE_KEY);
        }

        @Test
        void skipsWhenBranchesMissing() {
            ObjectNode metadata = objectMapper.createObjectNode();
            metadata.put("repository_id", REPO_ID);
            // no source_branch / target_branch

            Map<String, byte[]> files = new LinkedHashMap<>();
            assertThatCode(() -> provider.contribute(request(metadata), files)).doesNotThrowAnyException();
            assertThat(files).doesNotContainKey(FILE_KEY);
        }

        @Test
        void skipsWhenNoMetadata() {
            var job = new AgentJob();
            Map<String, byte[]> files = new LinkedHashMap<>();
            assertThatCode(() ->
                provider.contribute(new ContextRequest.PracticeReviewRequest(job), files)
            ).doesNotThrowAnyException();
            assertThat(files).doesNotContainKey(FILE_KEY);
        }

        @Test
        void swallowsGitExceptions() {
            stubGitAvailable();
            when(gitDiffOperations.resolveDiffRange(any(), any(), any(), any())).thenThrow(
                new RuntimeException("jgit boom")
            );

            Map<String, byte[]> files = new LinkedHashMap<>();
            assertThatCode(() -> provider.contribute(request(sampleMetadata()), files)).doesNotThrowAnyException();
            assertThat(files).doesNotContainKey(FILE_KEY);
        }

        @Test
        void doesNotSupportMentorChat() {
            ContextRequest mentor = new ContextRequest.MentorChatRequest(1L, 2L, java.util.UUID.randomUUID());
            assertThat(provider.supports(mentor)).isFalse();

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(mentor, files);
            assertThat(files).isEmpty();
        }
    }
}
