package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.Verdict;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class PullRequestReviewHandlerStaticMethodsTest extends BaseUnitTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // parseDiffStatPaths

    @Nested
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
        void fullRename() {
            String diffStat = """
                 old.swift => new.swift | 0
                """;
            Set<String> paths = PullRequestReviewHandler.parseDiffStatPaths(diffStat);
            assertThat(paths).containsExactly("new.swift");
        }

        @Test
        void partialRename() {
            String diffStat = """
                 src/{OldDir => NewDir}/File.swift | 5 +++--
                """;
            Set<String> paths = PullRequestReviewHandler.parseDiffStatPaths(diffStat);
            assertThat(paths).containsExactly("src/NewDir/File.swift");
        }

        @Test
        void partialRenameEmptyTarget() {
            String diffStat = """
                 iHabit/{HabitLogic => }/HabitAddView.swift | 3 ++-
                """;
            Set<String> paths = PullRequestReviewHandler.parseDiffStatPaths(diffStat);
            assertThat(paths).containsExactly("iHabit/HabitAddView.swift");
        }

        @Test
        void skipsSummaryAndEmpty() {
            String diffStat = """
                 File.swift | 1 +

                 1 file changed, 1 insertion(+)
                """;
            Set<String> paths = PullRequestReviewHandler.parseDiffStatPaths(diffStat);
            assertThat(paths).containsExactly("File.swift");
        }

        @Test
        void skipsSeparatorLine() {
            String diffStat = """
                ---
                 File.swift | 1 +
                """;
            Set<String> paths = PullRequestReviewHandler.parseDiffStatPaths(diffStat);
            assertThat(paths).containsExactly("File.swift");
        }

        @Test
        void emptyInput() {
            assertThat(PullRequestReviewHandler.parseDiffStatPaths("")).isEmpty();
        }
    }

    // filterByDiffScope

    @Nested
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
                "guidance",
                List.of()
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
                "guidance",
                List.of()
            );
        }

        @Test
        void emptyDiffFilesReturnsAll() {
            var finding = makeFinding("test", List.of("some/file.swift"));
            var result = PullRequestReviewHandler.filterByDiffScope(List.of(finding), Set.of());
            assertThat(result).hasSize(1);
        }

        @Test
        void keepsMatchingFindings() {
            var finding = makeFinding("test", List.of("src/Main.swift"));
            var result = PullRequestReviewHandler.filterByDiffScope(
                List.of(finding),
                Set.of("src/Main.swift", "src/Helper.swift")
            );
            assertThat(result).hasSize(1);
        }

        @Test
        void removesNonMatchingFindings() {
            var finding = makeFinding("test", List.of("src/Other.swift"));
            var result = PullRequestReviewHandler.filterByDiffScope(List.of(finding), Set.of("src/Main.swift"));
            assertThat(result).isEmpty();
        }

        @Test
        void keepsNoEvidenceFindings() {
            var finding = makeFindingNoEvidence("mr-description");
            var result = PullRequestReviewHandler.filterByDiffScope(List.of(finding), Set.of("src/Main.swift"));
            assertThat(result).hasSize(1);
        }

        @Test
        void keepsIfAnyLocationInScope() {
            var finding = makeFinding("test", List.of("out-of-scope.swift", "src/Main.swift"));
            var result = PullRequestReviewHandler.filterByDiffScope(List.of(finding), Set.of("src/Main.swift"));
            assertThat(result).hasSize(1);
        }

        @Test
        void keepsMetadataLevelPracticeEvenWithOutOfDiffLocation() {
            // A process/metadata-level practice (evidence = commit subjects, not a diff line) must survive
            // even when the agent attaches a stray non-diff location, or its finding is silently dropped.
            var finding = makeFinding("commit-subjects-explain-each-change", List.of("some-commit-ref"));
            var result = PullRequestReviewHandler.filterByDiffScope(List.of(finding), Set.of("src/Main.swift"));
            assertThat(result).hasSize(1);
        }
    }
}
