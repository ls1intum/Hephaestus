package de.tum.cit.aet.hephaestus.agent.practice.live;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeScriptInputs;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.practices.DefinitionTestFixtures;
import de.tum.cit.aet.hephaestus.practices.spi.PinnedPractice;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentId;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewMethod;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

// Live suites do not run in CI; validate their staged records against the runner schema here.
@Tag("unit")
class LiveWorkRecordsTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @TempDir
    Path workspace;

    @Test
    void shouldStageThePullRequestInputsTheRunnerParsesWhenALiveTestStagesAReview() throws IOException {
        ObjectNode work = LiveWorkRecords.pullRequest("a".repeat(40), "Add login service", "", 10);

        JsonNode staged = staged("scm.pull_request", work);

        assertInputs(staged, "scm.pull_request", work);
    }

    @Test
    void shouldStageTheIssueInputsTheRunnerParsesWhenACriteriaCaseReviewsAnIssue() throws IOException {
        ObjectNode work = LiveWorkRecords.issue(PracticeCriteriaCaseFixtures.DEFAULT_TITLE, "");

        JsonNode staged = staged("scm.issue", work);

        assertInputs(staged, "scm.issue", work);
    }

    @Test
    void shouldIndexTheCapturedCheckoutWhenALiveTestStagesAChange() throws Exception {
        LiveRunnerWorkspace.stageCartChange(workspace);
        JsonNode manifest = LiveRunnerWorkspace.manifest(workspace, LiveRunnerWorkspace.capturedFiles());

        assertThat(manifest.path("contractVersion").asString()).isEqualTo(SandboxLayout.SOURCE_CONTRACT_VERSION);
        assertThat(manifest.path("files"))
                .extracting(file -> file.path("file").path("path").asString())
                .containsAll(LiveRunnerWorkspace.capturedFiles().keySet());
    }

    @Test
    void shouldCaptureTheCheckoutOfTheSharedChangeWhenALiveTestStagesIt() throws Exception {
        LiveRunnerWorkspace.stageCartChange(workspace);

        JsonNode change = MAPPER.readTree(
                workspace.resolve(SandboxLayout.CONTEXT_PREFIX + "change.json").toFile());
        Path repo = workspace.resolve(SandboxLayout.REPO_MOUNT_RELATIVE);
        assertThat(Files.readString(repo.resolve(".git/HEAD")).strip())
                .isEqualTo(change.path("head_sha").asString());
        for (String artifact : LiveRunnerWorkspace.capturedFiles().keySet()) {
            assertThat(workspace.resolve(artifact)).as(artifact).exists();
        }
        assertThat(Files.readAllLines(repo.resolve("src/cart.ts")))
                .as("the added comments every model slot of the fixture reads")
                .filteredOn(line -> line.strip().startsWith("//"))
                .hasSize(5);
        assertThat(repo.resolve("lib/diff-parser.ts"))
                .as("what the fixture's agent searches for")
                .exists();
    }

    private JsonNode staged(String kind, ObjectNode work) throws IOException {
        var item = new ItemRef(1, 1, new ItemKind(kind), 1L, null, null);
        var definition = DefinitionTestFixtures.definition(item.kind(), "Live practice", "Review the work.", null);
        UUID reviewId = UUID.fromString("10000000-0000-4000-8000-000000000001");
        var pinned =
                new PinnedPractice(1, 1, definition, "a".repeat(64), new ReviewMethod(1, "b".repeat(64), List.of()));
        var version = new ItemVersion("fixture:one", null);
        var assigned = new ReviewAssignmentSnapshot(
                new ReviewAssignmentId(reviewId, 1, 1), item, version, 1L, "live-practice", pinned);
        var review = new ReviewSnapshot(
                reviewId, 1, item, ReviewKind.ITEM, version, ReviewOrigin.REQUEST, 1, List.of(pinned));
        var staged = new PracticeScriptInputs(MAPPER).stage(review, assigned, work, LiveWorkRecords.AT, null);
        return MAPPER.readTree(staged.values().iterator().next());
    }

    private static void assertInputs(JsonNode staged, String kind, ObjectNode work) {
        assertThat(staged.path("contractVersion").asInt()).isEqualTo(SandboxLayout.PRACTICE_CONTRACT_VERSION);
        assertThat(staged.path("kind").asString()).isEqualTo(kind);
        assertThat(staged.path("item")).isEqualTo(MAPPER.readTree(MAPPER.writeValueAsBytes(work)));
        assertThat(staged.path("scope").path("kind").asString()).isEqualTo("ITEM");
        assertThat(staged.path("scope").path("item").path("id").asString()).isEqualTo("1");
        assertThat(staged.path("scope").path("version").asString()).isEqualTo("fixture:one");
        assertThat(staged.path("practice").path("versionId").asString()).isEqualTo("1");
        assertThat(staged.path("previous").isNull()).isTrue();
        assertThat(staged.has("cause")).isFalse();
    }
}
