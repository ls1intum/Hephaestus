package de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackAnchor;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GitLabDiffPositionsTest extends BaseUnitTest {
    static final GitLabDiffPositions.Version VERSION = new GitLabDiffPositions.Version(
            1,
            "a".repeat(40),
            "b".repeat(40),
            "c".repeat(40),
            List.of(new GitLabDiffPositions.Diff(
                    "old.java", "new.java", "@@ -10,3 +20,3 @@\n context\n-old\n+new\n last\n", false, false)));

    @Test
    void shouldReadProviderDiffsWithExtraFieldsAndOmittedOptionalFlags() {
        var diff = new JsonMapper()
                .readValue(
                        "{\"old_path\":\"old.java\",\"new_path\":\"new.java\",\"diff\":\"@@ -1 +1 @@\\n-old\\n+new\\n\",\"renamed_file\":true}",
                        GitLabDiffPositions.Diff.class);
        assertThat(diff.tooLarge()).isNull();
        assertThat(diff.collapsed()).isNull();
        assertThat(GitLabDiffPositions.build(
                        new GitLabDiffPositions.Version(
                                1, VERSION.base(), VERSION.start(), VERSION.head(), List.of(diff)),
                        new FeedbackAnchor("new.java", null, 1, CitationSide.NEW)))
                .containsEntry("newLine", 1);
    }

    @Test
    void shouldPositionAddedAndRemovedLinesWithBothRenamePaths() {
        assertThat(GitLabDiffPositions.build(VERSION, new FeedbackAnchor("new.java", null, 21, CitationSide.NEW)))
                .containsEntry("newLine", 21)
                .doesNotContainKey("oldLine")
                .containsEntry("paths", Map.of("oldPath", "old.java", "newPath", "new.java"))
                .containsEntry("baseSha", VERSION.base())
                .containsEntry("startSha", VERSION.start())
                .containsEntry("headSha", VERSION.head());
        assertThat(GitLabDiffPositions.build(VERSION, new FeedbackAnchor("old.java", null, 11, CitationSide.OLD)))
                .containsEntry("oldLine", 11)
                .doesNotContainKey("newLine");
    }

    @Test
    void shouldUseBothCoordinatesForContextFromEitherSide() {
        for (var side : CitationSide.values()) {
            assertThat(GitLabDiffPositions.build(
                            VERSION,
                            new FeedbackAnchor(
                                    side == CitationSide.NEW ? "new.java" : "old.java",
                                    null,
                                    side == CitationSide.NEW ? 22 : 12,
                                    side)))
                    .containsEntry("oldLine", 12)
                    .containsEntry("newLine", 22);
        }
    }

    @Test
    void shouldRefusePathsOnTheWrongSideAndLinesOutsideTheDiff() {
        assertThat(GitLabDiffPositions.build(VERSION, new FeedbackAnchor("old.java", null, 21, CitationSide.NEW)))
                .isNull();
        assertThat(GitLabDiffPositions.build(VERSION, new FeedbackAnchor("new.java", null, 99, CitationSide.NEW)))
                .isNull();
    }
}
