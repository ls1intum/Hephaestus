package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.Severity;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CatalogCriteriaCasesTest extends BaseUnitTest {

    @Test
    void shouldKeepEvaluationCasesBoundToShippedPractices() throws IOException {
        var mapper = JsonMapper.builder().build();
        try (var catalogueStream = getClass().getResourceAsStream("/practices/default-catalog.json");
                var casesStream = getClass().getResourceAsStream("/practices/criteria-cases.json")) {
            assertThat(catalogueStream).isNotNull();
            assertThat(casesStream).isNotNull();
            var catalogue = mapper.readTree(Objects.requireNonNull(catalogueStream));
            Set<String> slugs = new HashSet<>();
            for (var group : catalogue.path("groups")) {
                for (var practice : group.path("practices")) {
                    slugs.add(practice.path("slug").asString());
                }
            }
            Set<String> ids = new HashSet<>();
            for (var scenario : mapper.readTree(Objects.requireNonNull(casesStream))) {
                assertThat(ids.add(scenario.path("id").asString()))
                        .as("unique case ID")
                        .isTrue();
                assertThat(scenario.path("reason").asString()).isNotBlank();
                assertThat(scenario.path("workType").asString()).isIn("issue", "pull_request");
                assertThat(scenario.path("files").properties()).isNotEmpty();
                assertThat(scenario.path("expected").properties()).isNotEmpty();
                for (var expected : scenario.path("expected").properties()) {
                    assertThat(slugs).contains(expected.getKey());
                    assertThat(ResultOutcome.values())
                            .extracting(ResultOutcome::name)
                            .contains(expected.getValue().asString());
                }
                // Severity is asserted only where the case names one, and only an expected NOT_MET carries one.
                for (var severity : scenario.path("expectedSeverity").properties()) {
                    assertThat(scenario.path("expected").path(severity.getKey()).asString())
                            .as("%s expects a severity only for NOT_MET", scenario.path("id"))
                            .isEqualTo(ResultOutcome.NOT_MET.name());
                    assertThat(Severity.values())
                            .extracting(Severity::name)
                            .contains(severity.getValue().asString());
                }
                for (var file : scenario.path("files").properties()) {
                    assertThat(file.getKey()).matches("(?:repo|context)/.+").doesNotContain("..");
                }
                // Base files are the repository at the change's base commit, so they hold repository content only.
                for (var file : scenario.path("baseFiles").properties()) {
                    assertThat(file.getKey()).matches("repo/.+").doesNotContain("..");
                    assertThat(file.getValue().isString())
                            .as("%s base file %s", scenario.path("id"), file.getKey())
                            .isTrue();
                }
            }
        }
    }

    // The cases stage only an issue or a pull request. A review, a chat thread or a document has its witness in the
    // test of its capture source, and a practice that people review is never captured.
    @Test
    void shouldGiveEachAutomatedIssueAndPullRequestPracticeACase() throws IOException {
        var mapper = JsonMapper.builder().build();
        try (var catalogueStream = getClass().getResourceAsStream("/practices/default-catalog.json");
                var casesStream = getClass().getResourceAsStream("/practices/criteria-cases.json")) {
            Set<String> automated = new HashSet<>();
            for (var group :
                    mapper.readTree(Objects.requireNonNull(catalogueStream)).path("groups")) {
                for (var practice : group.path("practices")) {
                    if (Set.of("scm.issue", "scm.pull_request")
                                    .contains(practice.path("kind").asString())
                            && practice.path("definition")
                                    .path("reviewedBy")
                                    .path("by")
                                    .asString()
                                    .equals("HEPHAESTUS")) {
                        automated.add(practice.path("slug").asString());
                    }
                }
            }
            Set<String> covered = new HashSet<>();
            for (var scenario : mapper.readTree(Objects.requireNonNull(casesStream))) {
                covered.addAll(scenario.path("expected").propertyNames());
            }
            assertThat(automated).isNotEmpty();
            assertThat(covered).containsAll(automated);
        }
    }
}
