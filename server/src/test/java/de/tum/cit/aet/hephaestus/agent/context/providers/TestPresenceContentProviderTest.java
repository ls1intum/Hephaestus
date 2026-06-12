package de.tum.cit.aet.hephaestus.agent.context.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class TestPresenceContentProviderTest extends BaseUnitTest {

    private static final String OUTPUT_KEY = "context/target/test_presence.json";
    private static final Long REPO_ID = 123L;
    private static final Long WORKSPACE_ID = 99L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private GitRepositoryManager gitRepositoryManager;

    @Mock
    private GitDiffOperations gitDiffOperations;

    @TempDir
    Path repoDir;

    private TestPresenceContentProvider provider;

    @BeforeEach
    void setUp() {
        provider = new TestPresenceContentProvider(objectMapper, gitRepositoryManager, gitDiffOperations);
    }

    private ObjectNode sampleMetadata() {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("repository_id", REPO_ID);
        metadata.put("pull_request_id", 456L);
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

    private void stubGitCloned() {
        lenient().when(gitRepositoryManager.isEnabled()).thenReturn(true);
        lenient().when(gitRepositoryManager.isRepositoryCloned(REPO_ID)).thenReturn(true);
        lenient().when(gitRepositoryManager.getRepositoryPath(REPO_ID)).thenReturn(repoDir);
    }

    private void writeFile(String relPath, String content) throws Exception {
        Path target = repoDir.resolve(relPath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }

    @Nested
    @Tag("unit")
    class Supports {

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
    @Tag("unit")
    class HappyPath {

        @Test
        void detectsTestTargetAndSamplePaths() throws Exception {
            writeFile("Sources/Auth.swift", "struct Auth {}");
            writeFile("Tests/AuthTests.swift", "import XCTest");
            writeFile("webapp/src/login.spec.ts", "describe('x', () => {})");
            writeFile("server/src/MainTest.java", "class MainTest {}");
            stubGitCloned();
            // No diff range resolvable in this fresh temp dir → changeTouchesTests stays null.
            lenient()
                .when(gitDiffOperations.resolveDiffRange(repoDir, "main", "feature/auth-fix", "abc123def456"))
                .thenReturn(null);

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            assertThat(files).containsKey(OUTPUT_KEY);
            JsonNode json = objectMapper.readTree(files.get(OUTPUT_KEY));
            assertThat(json.get("repoHasTestTarget").asBoolean()).isTrue();
            assertThat(json.get("testFileCount").asInt()).isEqualTo(3);
            assertThat(json.get("sampleTestPaths").isArray()).isTrue();
            assertThat(json.get("sampleTestPaths").size()).isEqualTo(3);
            assertThat(json.get("changeTouchesTests").isNull()).isTrue();
        }

        @Test
        void noTestTargetWhenRepoHasNoTests() throws Exception {
            writeFile("Sources/Auth.swift", "struct Auth {}");
            writeFile("README.md", "# project");
            stubGitCloned();

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            JsonNode json = objectMapper.readTree(files.get(OUTPUT_KEY));
            assertThat(json.get("repoHasTestTarget").asBoolean()).isFalse();
            assertThat(json.get("testFileCount").asInt()).isEqualTo(0);
            assertThat(json.get("sampleTestPaths").size()).isEqualTo(0);
            assertThat(json.get("note").asString()).contains("vacuous");
        }

        @Test
        void changeTouchesTestsTrueWhenDiffAddsTest() throws Exception {
            writeFile("Tests/AuthTests.swift", "import XCTest");
            stubGitCloned();
            when(gitDiffOperations.resolveDiffRange(repoDir, "main", "feature/auth-fix", "abc123def456")).thenReturn(
                new String[] { "baseSha", "headSha" }
            );
            when(gitDiffOperations.diffNameOnly(repoDir, "baseSha", "headSha")).thenReturn(
                "Sources/Auth.swift\nTests/AuthTests.swift\n"
            );

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            JsonNode json = objectMapper.readTree(files.get(OUTPUT_KEY));
            assertThat(json.get("changeTouchesTests").asBoolean()).isTrue();
        }

        @Test
        void changeTouchesTestsFalseWhenDiffHasNoTests() throws Exception {
            writeFile("Tests/AuthTests.swift", "import XCTest");
            stubGitCloned();
            when(gitDiffOperations.resolveDiffRange(repoDir, "main", "feature/auth-fix", "abc123def456")).thenReturn(
                new String[] { "baseSha", "headSha" }
            );
            when(gitDiffOperations.diffNameOnly(repoDir, "baseSha", "headSha")).thenReturn(
                "Sources/Auth.swift\nSources/Login.swift\n"
            );

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            JsonNode json = objectMapper.readTree(files.get(OUTPUT_KEY));
            assertThat(json.get("changeTouchesTests").asBoolean()).isFalse();
        }

        @Test
        void skipsGitDatabaseDirectory() throws Exception {
            // A path under .git that LOOKS like a test must not be counted.
            writeFile(".git/hooks/preTest.swift", "noop");
            writeFile("Tests/RealTests.swift", "import XCTest");
            stubGitCloned();

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            JsonNode json = objectMapper.readTree(files.get(OUTPUT_KEY));
            assertThat(json.get("testFileCount").asInt()).isEqualTo(1);
        }
    }

    @Nested
    @Tag("unit")
    class BestEffortAbstention {

        @Test
        void emitsNothingWhenGitDisabled() {
            when(gitRepositoryManager.isEnabled()).thenReturn(false);

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            assertThat(files).doesNotContainKey(OUTPUT_KEY);
        }

        @Test
        void emitsNothingWhenRepoNotCloned() {
            lenient().when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(gitRepositoryManager.isRepositoryCloned(REPO_ID)).thenReturn(false);

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            assertThat(files).doesNotContainKey(OUTPUT_KEY);
        }

        @Test
        void emitsNothingWhenNoMetadata() {
            var job = new AgentJob();

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(new ContextRequest.PracticeReviewRequest(job), files);

            assertThat(files).doesNotContainKey(OUTPUT_KEY);
        }

        @Test
        void emitsNothingWhenNoRepositoryId() {
            ObjectNode metadata = objectMapper.createObjectNode();
            metadata.put("pull_request_id", 456L);

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(metadata), files);

            assertThat(files).doesNotContainKey(OUTPUT_KEY);
        }

        @Test
        void ignoresUnsupportedRequestVariant() {
            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(new ContextRequest.MentorChatRequest(1L, 2L, java.util.UUID.randomUUID()), files);
            assertThat(files).isEmpty();
        }

        @Test
        void neverThrowsWhenGitManagerBlowsUp() {
            lenient().when(gitRepositoryManager.isEnabled()).thenReturn(true);
            when(gitRepositoryManager.isRepositoryCloned(REPO_ID)).thenThrow(new RuntimeException("disk gone"));

            Map<String, byte[]> files = new LinkedHashMap<>();
            // Must degrade silently — not propagate (builder would treat a raw RuntimeException as skip,
            // but we keep our own contract clean and swallow it here too).
            provider.contribute(request(sampleMetadata()), files);

            assertThat(files).doesNotContainKey(OUTPUT_KEY);
        }
    }

    @Nested
    @Tag("unit")
    class PathConvention {

        @Test
        void recognisesCommonTestConventions() {
            assertThat(TestPresenceContentProvider.isTestPath("Tests/LoginTests.swift")).isTrue();
            assertThat(TestPresenceContentProvider.isTestPath("src/FooTest.java")).isTrue();
            assertThat(TestPresenceContentProvider.isTestPath("pkg/foo_test.go")).isTrue();
            assertThat(TestPresenceContentProvider.isTestPath("web/login.test.ts")).isTrue();
            assertThat(TestPresenceContentProvider.isTestPath("web/login.spec.tsx")).isTrue();
            assertThat(TestPresenceContentProvider.isTestPath("module/__tests__/util.js")).isTrue();
            assertThat(TestPresenceContentProvider.isTestPath("App/test/Helper.kt")).isTrue();
        }

        @Test
        void rejectsNonTestPaths() {
            assertThat(TestPresenceContentProvider.isTestPath("Sources/Login.swift")).isFalse();
            assertThat(TestPresenceContentProvider.isTestPath("src/Main.java")).isFalse();
            assertThat(TestPresenceContentProvider.isTestPath("README.md")).isFalse();
            assertThat(TestPresenceContentProvider.isTestPath("docs/testing-guide.md")).isFalse();
        }
    }
}
