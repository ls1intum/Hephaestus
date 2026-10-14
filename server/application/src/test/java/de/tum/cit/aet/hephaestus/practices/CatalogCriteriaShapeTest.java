package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class CatalogCriteriaShapeTest extends BaseUnitTest {

    /** The section headings, in order; each practice carries all of them. */
    static final List<String> SECTIONS =
            List.of("## The standard", "## Sources", "## Judge", "## Severity", "## Grounding", "## Defer");

    @Test
    @DisplayName("every bundled practice states its review focus and carries the sections in order")
    void bundledPracticesFollowTheShape() throws IOException {
        List<String> failures = new ArrayList<>();
        for (JsonNode group : catalogue().path("groups")) {
            for (JsonNode practice : group.path("practices")) {
                String slug = practice.path("slug").asString();
                String criteria = practice.path("definition").path("criteria").asString("");
                if (criteria.indexOf("REVIEW FOCUS:") < 0
                        || criteria.indexOf("REVIEW FOCUS:") > criteria.indexOf("\n## The standard")) {
                    failures.add(slug + ": must state 'REVIEW FOCUS:' before the standard");
                }
                int last = -1;
                for (String section : SECTIONS) {
                    int at = criteria.indexOf("\n" + section);
                    if (at < 0) {
                        failures.add(slug + ": missing section '" + section + "'");
                    } else if (at < last) {
                        failures.add(slug + ": section '" + section + "' is out of order");
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
                if (practice.path("definition").path("scan").isNull()) {
                    assertThat(criteria).as("%s applicability gate", slug).contains("DOES_NOT_APPLY");
                } else {
                    assertThat(criteria)
                            .as("%s scan and change instructions", slug)
                            .contains("## On a change", "## In a scan");
                }
            }
        }
        assertThat(failures)
                .as("bundled practices off the decision-procedure shape")
                .isEmpty();
    }

    private static String sectionOf(String criteria, String heading) {
        int start = criteria.indexOf("\n" + heading);
        if (start < 0) return "";
        int end = criteria.indexOf("\n## ", start + 1);
        return end < 0 ? criteria.substring(start) : criteria.substring(start, end);
    }

    private static JsonNode catalogue() throws IOException {
        try (InputStream in =
                CatalogCriteriaShapeTest.class.getClassLoader().getResourceAsStream("practices/default-catalog.json")) {
            assertThat(in)
                    .as("practices/default-catalog.json must be on the classpath")
                    .isNotNull();
            return JsonMapper.builder()
                    .build()
                    .readTree(new String(Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
