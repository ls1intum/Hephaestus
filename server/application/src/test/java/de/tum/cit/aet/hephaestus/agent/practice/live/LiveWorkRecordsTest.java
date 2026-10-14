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

/**
 * The inputs the live harnesses stage for a practice program, pinned to {@code practice-results/work-records/}, which
 * {@code lib/contract.test.ts} parses with the runner's own input schema. The live tests do not run in CI, so a staged record that the runner refuses fails here instead.
 */
@Tag("unit")
class LiveWorkRecordsTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Path RECORDS = Path.of("src/test/resources/practice-results/work-records");

    @TempDir
    Path workspace;

    @Test
    void shouldStageThePullRequestInputsTheRunnerParsesWhenALiveTestStagesAReview() throws IOException {
        ObjectNode work = LiveWorkRecords.pullRequest("a".repeat(40), "Add login service", "", 10);

        JsonNode staged = staged("scm.pull_request", work);

        assertThat(staged).isEqualTo(pinned("scm.pull_request.json", staged));
    }

    @Test
    void shouldStageTheIssueInputsTheRunnerParsesWhenACriteriaCaseReviewsAnIssue() throws IOException {
        ObjectNode work = LiveWorkRecords.issue(PracticeCriteriaCaseFixtures.DEFAULT_TITLE, "");

        JsonNode staged = staged("scm.issue", work);

        assertThat(staged).isEqualTo(pinned("scm.issue.json", staged));
    }

    /** {@code pi-result-normalize.spec.ts} checks a quote of the change against this index as the session does. */
    @Test
    void shouldIndexTheCapturedCheckoutWhenALiveTestStagesAChange() throws Exception {
        LiveRunnerWorkspace.stageCartChange(workspace);
        JsonNode manifest = LiveRunnerWorkspace.manifest(workspace, LiveRunnerWorkspace.capturedFiles());

        assertThat(manifest)
                .isEqualTo(pinnedFile(Path.of("src/test/resources/practice-results/live-manifest.json"), manifest));
    }

    /** The staged checkout is the one the index names: its head detached at the pinned change, its witnesses there. */
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

    /** The single canonical v3 inputs file staged by the runtime serializer. */
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

    private static JsonNode pinned(String name, JsonNode staged) throws IOException {
        return pinnedFile(RECORDS.resolve(name), staged);
    }

    /** The pinned file; {@code UPDATE_WORK_RECORDS=1} writes it from what the harness stages now. */
    private static JsonNode pinnedFile(Path file, JsonNode staged) throws IOException {
        if ("1".equals(System.getenv("UPDATE_WORK_RECORDS"))) {
            Files.createDirectories(file.getParent());
            Files.write(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(staged));
        }
        return MAPPER.readTree(file.toFile());
    }
}
