package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.tum.cit.aet.hephaestus.practices.curated.BundledPracticeCatalog;
import de.tum.cit.aet.hephaestus.practices.curated.BundledPracticeCatalogLoader;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CatalogCriteriaShapeTest extends BaseUnitTest {

    static final List<String> SECTIONS =
            List.of("## The standard", "## Sources", "## Judge", "## Severity", "## Grounding", "## Defer");

    @Test
    @DisplayName("every bundled practice states its review focus and carries the sections in order")
    void shouldProvideOrderedDecisionInstructionsForEveryBundledPractice() {
        List<String> failures = new ArrayList<>();
        var practices = catalogue().practices();
        assertThat(practices).isNotEmpty();
        for (var practice : practices) {
            String slug = practice.slug();
            var definition = practice.definition().definition();
            String criteria = definition.criteria();
            if (criteria.indexOf("REVIEW FOCUS:") < 0
                    || criteria.indexOf("REVIEW FOCUS:") > criteria.indexOf("\n## The standard")) {
                failures.add(slug + ": must state 'REVIEW FOCUS:' before the standard");
            }
            int last = -1;
            for (String section : SECTIONS) {
                int at = criteria.indexOf("\n" + section, Math.max(last, 0));
                if (at < 0) {
                    failures.add(slug + ": missing or out-of-order section '" + section + "'");
                } else {
                    last = at;
                }
            }
            String judge = sectionOf(criteria, "## Judge");
            for (String outcome : List.of("MET", "NOT_MET")) {
                if (!Pattern.compile("(?m)^- " + outcome + "(?:[: ]|$)")
                        .matcher(judge)
                        .find()) {
                    failures.add(slug + ": the Judge section does not decide " + outcome);
                }
            }
            if (!judge.contains("COULD_NOT_DETERMINE")) {
                failures.add(slug + ": the Judge section does not say when the review is COULD_NOT_DETERMINE");
            }
            if (definition.scan() == null) {
                assertThat(criteria).as("%s applicability gate", slug).contains("DOES_NOT_APPLY");
            } else {
                assertThat(criteria)
                        .as("%s scan and change instructions", slug)
                        .contains("## On a change", "## In a scan");
            }
        }
        assertThat(failures)
                .as("bundled practices off the decision-procedure shape")
                .isEmpty();
    }

    @Test
    void shouldUseTheSwiftApiNameAcrossStructuredConcurrencyInstructions() {
        var definition = catalogue().practices().stream()
                .filter(entry -> entry.slug().equals("uses-structured-concurrency-safely"))
                .findFirst()
                .orElseThrow()
                .definition()
                .definition();
        assertThat(definition.criteria()).contains("`DispatchQueue`").doesNotContain("delivery queue");
        assertThat(Objects.requireNonNull(definition.guidance().guide()).markdown())
                .contains("`DispatchQueue`")
                .doesNotContain("delivery queue");
        assertThat(definition.script()).contains("DispatchQueue").doesNotContain("delivery queue");
    }

    private static String sectionOf(String criteria, String heading) {
        int start = criteria.indexOf("\n" + heading);
        if (start < 0) return "";
        int end = criteria.indexOf("\n## ", start + 1);
        return end < 0 ? criteria.substring(start) : criteria.substring(start, end);
    }

    private static BundledPracticeCatalog catalogue() {
        return new BundledPracticeCatalogLoader(JsonMapper.builder().build(), mock(PracticeDefinitionValidator.class))
                .catalog();
    }
}
