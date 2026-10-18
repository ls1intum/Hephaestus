package de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackAnchor;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback.GitLabDiffPositions.Point;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback.GitLabDiffPositions.Position;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback.GitLabDiffPositions.Range;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GitLabDiffPositionsTest extends BaseUnitTest {
    private static final String BASE = "a".repeat(40);
    private static final String START = "b".repeat(40);
    private static final String HEAD = "c".repeat(40);
    static final GitLabDiffPositions.Version VERSION = new GitLabDiffPositions.Version(
            1,
            BASE,
            START,
            HEAD,
            List.of(new GitLabDiffPositions.Diff(
                    "old.java", "new.java", "@@ -10,3 +20,3 @@\n context\n-old\n+new\n last\n", false, false)));

    // SHA-1 of "new.java": GitLab's line code names the file by its path after the change.
    static final String NEW_JAVA = "04c17068b0db8d1fec098d431ea10510ca732524";

    @Test
    void shouldReadProviderDiffsWithExtraFieldsAndOmittedOptionalFlags() {
        var diff = new JsonMapper()
                .readValue(
                        "{\"old_path\":\"old.java\",\"new_path\":\"new.java\",\"diff\":\"@@ -1 +1 @@\\n-old\\n+new\\n\",\"renamed_file\":true}",
                        GitLabDiffPositions.Diff.class);
        assertThat(diff.tooLarge()).isNull();
        assertThat(diff.collapsed()).isNull();
        assertThat(GitLabDiffPositions.build(
                        new GitLabDiffPositions.Version(1, BASE, START, HEAD, List.of(diff)),
                        new FeedbackAnchor("new.java", null, 1, CitationSide.NEW)))
                .extracting(Position::newLine)
                .isEqualTo(1);
    }

    // Added lines carry new_line only, removed lines old_line only, as GitLab's Discussions API documents.
    @Test
    void shouldPositionAddedAndRemovedLinesWithBothRenamePaths() {
        assertThat(GitLabDiffPositions.build(VERSION, new FeedbackAnchor("new.java", null, 21, CitationSide.NEW)))
                .isEqualTo(new Position(BASE, START, HEAD, "old.java", "new.java", null, 21, null));
        assertThat(GitLabDiffPositions.build(VERSION, new FeedbackAnchor("old.java", null, 11, CitationSide.OLD)))
                .isEqualTo(new Position(BASE, START, HEAD, "old.java", "new.java", 11, null, null));
    }

    @Test
    void shouldUseBothCoordinatesForContextFromEitherSide() {
        for (var side : CitationSide.values()) {
            var position = Objects.requireNonNull(GitLabDiffPositions.build(
                    VERSION,
                    new FeedbackAnchor(
                            side == CitationSide.NEW ? "new.java" : "old.java",
                            null,
                            side == CitationSide.NEW ? 22 : 12,
                            side)));
            assertThat(position.oldLine()).isEqualTo(12);
            assertThat(position.newLine()).isEqualTo(22);
        }
    }

    // A range names both ends; the position's own line is the end, so readback keeps reading the end line.
    @Test
    void shouldPlaceANewSideRangeFromAContextLineToAnAddedLine() {
        var position = Objects.requireNonNull(
                GitLabDiffPositions.build(VERSION, new FeedbackAnchor("new.java", 20, 21, CitationSide.NEW)));

        assertThat(position.oldLine()).isNull();
        assertThat(position.newLine()).isEqualTo(21);
        assertThat(position.range())
                .isEqualTo(new Range(
                        new Point(NEW_JAVA + "_10_20", "old", 10, 20),
                        new Point(NEW_JAVA + "_12_21", "new", null, 21)));
        assertThat(position.request())
                .isEqualTo(Map.of(
                        "position_type",
                        "text",
                        "base_sha",
                        BASE,
                        "start_sha",
                        START,
                        "head_sha",
                        HEAD,
                        "old_path",
                        "old.java",
                        "new_path",
                        "new.java",
                        "new_line",
                        21,
                        "line_range",
                        Map.of(
                                "start",
                                Map.of("line_code", NEW_JAVA + "_10_20", "type", "old", "old_line", 10, "new_line", 20),
                                "end",
                                Map.of("line_code", NEW_JAVA + "_12_21", "type", "new", "new_line", 21))));
    }

    @Test
    void shouldPlaceAnOldSideRangeEndingOnARemovedLine() {
        var position = Objects.requireNonNull(
                GitLabDiffPositions.build(VERSION, new FeedbackAnchor("old.java", 10, 11, CitationSide.OLD)));

        assertThat(position.oldLine()).isEqualTo(11);
        assertThat(position.newLine()).isNull();
        assertThat(position.range())
                .isEqualTo(new Range(
                        new Point(NEW_JAVA + "_10_20", "old", 10, 20),
                        new Point(NEW_JAVA + "_11_21", "old", 11, null)));
    }

    // The fixture of the live acceptance: a new file whose lines 26 and 27 to 30 are cited.
    @Test
    void shouldPlaceLinesOfANewFileWithTheZeroOldCounterOfItsHunk() {
        String path = "src/main/java/validation/PreviewFileHandler.java";
        String diff = "@@ -0,0 +1,30 @@\n"
                + IntStream.rangeClosed(1, 30).mapToObj(n -> "+line " + n).collect(Collectors.joining("\n")) + "\n";
        var version = new GitLabDiffPositions.Version(
                1, BASE, BASE, HEAD, List.of(new GitLabDiffPositions.Diff(path, path, diff, false, false)));

        assertThat(GitLabDiffPositions.build(version, new FeedbackAnchor(path, null, 26, CitationSide.NEW)))
                .isEqualTo(new Position(BASE, BASE, HEAD, path, path, null, 26, null));
        assertThat(Objects.requireNonNull(
                                GitLabDiffPositions.build(version, new FeedbackAnchor(path, 27, 30, CitationSide.NEW)))
                        .range())
                .isEqualTo(new Range(
                        new Point("8ee1d5d9596590fbc3c2d3b1c964d5cc4b3801b7_0_27", "new", null, 27),
                        new Point("8ee1d5d9596590fbc3c2d3b1c964d5cc4b3801b7_0_30", "new", null, 30)));
    }

    @Test
    void shouldNameARenamedFileByItsNewPathInTheLineCode() {
        var renamed = new GitLabDiffPositions.Version(
                1,
                BASE,
                START,
                HEAD,
                List.of(new GitLabDiffPositions.Diff(
                        "renamed/Old.java", "renamed/New.java", "@@ -1,2 +1,2 @@\n a\n-b\n+c\n", false, false)));

        assertThat(Objects.requireNonNull(GitLabDiffPositions.build(
                                renamed, new FeedbackAnchor("renamed/New.java", 1, 2, CitationSide.NEW)))
                        .range())
                .isEqualTo(new Range(
                        new Point("222d961b94a776849c01f3030cf26d1eda63321a_1_1", "old", 1, 1),
                        new Point("222d961b94a776849c01f3030cf26d1eda63321a_3_2", "new", null, 2)));
    }

    // A range this diff cannot hold in one hunk is not this diff's range, so no native range is claimed.
    @Test
    void shouldRefuseARangeAcrossHunksOrBeyondTheDiff() {
        var hunks = new GitLabDiffPositions.Version(
                1,
                BASE,
                START,
                HEAD,
                List.of(new GitLabDiffPositions.Diff(
                        "new.java", "new.java", "@@ -1,1 +1,1 @@\n a\n@@ -10,1 +10,1 @@\n b\n", false, false)));

        assertThat(GitLabDiffPositions.build(hunks, new FeedbackAnchor("new.java", 1, 10, CitationSide.NEW)))
                .isNull();
        assertThat(GitLabDiffPositions.build(hunks, new FeedbackAnchor("new.java", null, 10, CitationSide.NEW)))
                .isNotNull();
        assertThat(GitLabDiffPositions.build(VERSION, new FeedbackAnchor("new.java", 15, 21, CitationSide.NEW)))
                .isNull();
    }

    @Test
    void shouldRefusePathsOnTheWrongSideAndLinesOutsideTheDiff() {
        assertThat(GitLabDiffPositions.build(VERSION, new FeedbackAnchor("old.java", null, 21, CitationSide.NEW)))
                .isNull();
        assertThat(GitLabDiffPositions.build(VERSION, new FeedbackAnchor("new.java", null, 99, CitationSide.NEW)))
                .isNull();
    }
}
