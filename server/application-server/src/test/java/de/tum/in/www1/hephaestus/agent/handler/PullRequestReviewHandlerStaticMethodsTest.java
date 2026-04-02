package de.tum.in.www1.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.practices.model.Severity;
import de.tum.in.www1.hephaestus.practices.model.Verdict;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PullRequestReviewHandler static methods")
class PullRequestReviewHandlerStaticMethodsTest extends BaseUnitTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── parseDiffStatPaths ──────────────────────────────────────────────────

    @Nested
    @DisplayName("parseDiffStatPaths")
    class ParseDiffStatPaths {

        @Test
        @DisplayName("parses simple file paths from diff-stat output")
        void simpleFiles() {
            String diffStat = """
                 src/Main.swift        | 10 +++++++---
                 src/Helper.swift      |  3 +++
                 2 files changed, 10 insertions(+), 3 deletions(-)
                """;
            Set<String> paths = PullRequestReviewHandler.parseDiffStatPaths(diffStat);
            assertThat(paths).containsExactlyInAnyOrder("src/Main.swift", "src/Helper.swift");
        }

        @Test
        @DisplayName("handles full rename (old.txt => new.txt)")
        void fullRename() {
            String diffStat = """
                 old.swift => new.swift | 0
                """;
            Set<String> paths = PullRequestReviewHandler.parseDiffStatPaths(diffStat);
            assertThat(paths).containsExactly("new.swift");
        }

        @Test
        @DisplayName("handles partial rename with braces")
        void partialRename() {
            String diffStat = """
                 src/{OldDir => NewDir}/File.swift | 5 +++--
                """;
            Set<String> paths = PullRequestReviewHandler.parseDiffStatPaths(diffStat);
            assertThat(paths).containsExactly("src/NewDir/File.swift");
        }

        @Test
        @DisplayName("handles partial rename with empty target (directory removal)")
        void partialRenameEmptyTarget() {
            String diffStat = """
                 iHabit/{HabitLogic => }/HabitAddView.swift | 3 ++-
                """;
            Set<String> paths = PullRequestReviewHandler.parseDiffStatPaths(diffStat);
            assertThat(paths).containsExactly("iHabit/HabitAddView.swift");
        }

        @Test
        @DisplayName("skips summary line and empty lines")
        void skipsSummaryAndEmpty() {
            String diffStat = """
                 File.swift | 1 +

                 1 file changed, 1 insertion(+)
                """;
            Set<String> paths = PullRequestReviewHandler.parseDiffStatPaths(diffStat);
            assertThat(paths).containsExactly("File.swift");
        }

        @Test
        @DisplayName("skips --- separator lines")
        void skipsSeparatorLine() {
            String diffStat = """
                ---
                 File.swift | 1 +
                """;
            Set<String> paths = PullRequestReviewHandler.parseDiffStatPaths(diffStat);
            assertThat(paths).containsExactly("File.swift");
        }

        @Test
        @DisplayName("returns empty set for empty input")
        void emptyInput() {
            assertThat(PullRequestReviewHandler.parseDiffStatPaths("")).isEmpty();
        }
    }

    // ── filterByDiffScope ───────────────────────────────────────────────────

    @Nested
    @DisplayName("filterByDiffScope")
    class FilterByDiffScope {

        private PracticeDetectionResultParser.ValidatedFinding makeFinding(String slug, List<String> filePaths) {
            ObjectNode evidence = objectMapper.createObjectNode();
            ArrayNode locations = objectMapper.createArrayNode();
            for (String path : filePaths) {
                ObjectNode loc = objectMapper.createObjectNode();
                loc.put("path", path);
                loc.put("startLine", 1);
                locations.add(loc);
            }
            evidence.set("locations", locations);
            return new PracticeDetectionResultParser.ValidatedFinding(
                slug,
                "Test Title",
                Verdict.NEGATIVE,
                Severity.MINOR,
                0.9f,
                evidence,
                "reasoning",
                "guidance"
            );
        }

        private PracticeDetectionResultParser.ValidatedFinding makeFindingNoEvidence(String slug) {
            return new PracticeDetectionResultParser.ValidatedFinding(
                slug,
                "Test Title",
                Verdict.POSITIVE,
                Severity.INFO,
                0.9f,
                null,
                "reasoning",
                "guidance"
            );
        }

        @Test
        @DisplayName("returns all findings when diffFiles is empty (no filter)")
        void emptyDiffFilesReturnsAll() {
            var finding = makeFinding("test", List.of("some/file.swift"));
            var result = PullRequestReviewHandler.filterByDiffScope(List.of(finding), Set.of());
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("keeps findings with matching file paths")
        void keepsMatchingFindings() {
            var finding = makeFinding("test", List.of("src/Main.swift"));
            var result = PullRequestReviewHandler.filterByDiffScope(
                List.of(finding),
                Set.of("src/Main.swift", "src/Helper.swift")
            );
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("removes findings with no matching file paths")
        void removesNonMatchingFindings() {
            var finding = makeFinding("test", List.of("src/Other.swift"));
            var result = PullRequestReviewHandler.filterByDiffScope(List.of(finding), Set.of("src/Main.swift"));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("keeps findings with no evidence (cannot filter)")
        void keepsNoEvidenceFindings() {
            var finding = makeFindingNoEvidence("mr-description");
            var result = PullRequestReviewHandler.filterByDiffScope(List.of(finding), Set.of("src/Main.swift"));
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("keeps finding if ANY location is in scope")
        void keepsIfAnyLocationInScope() {
            var finding = makeFinding("test", List.of("out-of-scope.swift", "src/Main.swift"));
            var result = PullRequestReviewHandler.filterByDiffScope(List.of(finding), Set.of("src/Main.swift"));
            assertThat(result).hasSize(1);
        }
    }
}
