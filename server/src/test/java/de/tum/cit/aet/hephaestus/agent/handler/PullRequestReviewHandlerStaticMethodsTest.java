package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
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

    @Nested
    class FilterByDiffScope {

        private PracticeDetectionResultParser.ValidatedObservation makeObservation(
            String slug,
            List<String> filePaths
        ) {
            return makeObservation(slug, "scm.pull-request.diff", filePaths);
        }

        private PracticeDetectionResultParser.ValidatedObservation makeObservation(
            String slug,
            String sourceKind,
            List<String> filePaths
        ) {
            ObjectNode evidence = objectMapper.createObjectNode();
            ArrayNode citations = objectMapper.createArrayNode();
            for (String path : filePaths) {
                ObjectNode citation = objectMapper.createObjectNode();
                citation.put("sourceKind", sourceKind);
                citation.put(
                    "artifactPath",
                    sourceKind.equals("scm.pull-request.diff")
                        ? "inputs/context/diff.patch"
                        : "inputs/context/metadata.json"
                );
                citation.put("path", path);
                if (sourceKind.equals("scm.pull-request.diff")) {
                    citation.put("side", "NEW");
                }
                citation.put("startLine", 1);
                citation.put("endLine", 1);
                citation.put("quote", "evidence");
                citations.add(citation);
            }
            evidence.set("citations", citations);
            return new PracticeDetectionResultParser.ValidatedObservation(
                slug,
                "Test Title",
                Presence.ABSENT,
                Assessment.BAD,
                Severity.MINOR,
                evidence,
                "reasoning"
            );
        }

        private PracticeDetectionResultParser.ValidatedObservation makeFindingNoEvidence(String slug) {
            return new PracticeDetectionResultParser.ValidatedObservation(
                slug,
                "Test Title",
                Presence.PRESENT,
                Assessment.GOOD,
                Severity.INFO,
                null,
                "reasoning"
            );
        }

        @Test
        void emptyDiffFilesReturnsAll() {
            var observation = makeObservation("test", List.of("some/file.swift"));
            var result = PullRequestReviewHandler.filterByDiffScope(List.of(observation), Set.of());
            assertThat(result).hasSize(1);
        }

        @Test
        void keepsMatchingObservations() {
            var observation = makeObservation("test", List.of("src/Main.swift"));
            var result = PullRequestReviewHandler.filterByDiffScope(
                List.of(observation),
                Set.of("src/Main.swift", "src/Helper.swift")
            );
            assertThat(result).hasSize(1);
        }

        @Test
        void removesNonMatchingObservations() {
            var observation = makeObservation("test", List.of("src/Other.swift"));
            var result = PullRequestReviewHandler.filterByDiffScope(List.of(observation), Set.of("src/Main.swift"));
            assertThat(result).isEmpty();
        }

        @Test
        void keepsNoEvidenceObservations() {
            var observation = makeFindingNoEvidence("mr-description");
            var result = PullRequestReviewHandler.filterByDiffScope(List.of(observation), Set.of("src/Main.swift"));
            assertThat(result).hasSize(1);
        }

        @Test
        void keepsIfAnyLocationInScope() {
            var observation = makeObservation("test", List.of("out-of-scope.swift", "src/Main.swift"));
            var result = PullRequestReviewHandler.filterByDiffScope(List.of(observation), Set.of("src/Main.swift"));
            assertThat(result).hasSize(1);
        }

        @Test
        void keepsCodeFindingWhenLocationCarriesTheRepoMountPrefix() {
            // The agent cites files it read under the repo mount as "inputs/sources/scm/repo/<path>" (ADR
            // 0020); diff paths are repo-relative. The mount prefix must be normalised or a valid code
            // observation on a changed file is dropped.
            var observation = makeObservation(
                "ships-tests-with-the-change",
                List.of("inputs/sources/scm/repo/src/Main.swift")
            );
            var result = PullRequestReviewHandler.filterByDiffScope(List.of(observation), Set.of("src/Main.swift"));
            assertThat(result).hasSize(1);
        }

        /**
         * A citation that names no source cannot be placed in or out of the diff, and the observation has no
         * other citation vouching for it — so it is dropped rather than posted on a file it may not touch.
         */
        @Test
        void dropsFindingWhoseOnlyCitationNamesNoSource() {
            var observation = makeObservation("test", "", List.of("src/Main.swift"));
            var result = PullRequestReviewHandler.filterByDiffScope(List.of(observation), Set.of("src/Main.swift"));
            assertThat(result).isEmpty();
        }

        @Test
        void keepsMetadataLevelPracticeEvenWithOutOfDiffLocation() {
            var observation = makeObservation(
                "commit-subjects-explain-each-change",
                "scm.pull-request.core",
                List.of("some-commit-ref")
            );
            var result = PullRequestReviewHandler.filterByDiffScope(List.of(observation), Set.of("src/Main.swift"));
            assertThat(result).hasSize(1);
        }
    }
}
