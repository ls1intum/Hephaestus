package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The practice surfaces are criterion-referenced by design (ADR 0028): they compare a person's work to the
 * practice's stated standard, never to their colleagues. This pins that where it is cheapest to break — the
 * wire layer, where a field named like a score invites a client to render a comparison no matter how the
 * documentation frames it.
 *
 * <p>Name and dependency checks: aimed at drift, not malice.
 */
class NonCompetitiveSurfaceArchTest extends HephaestusArchitectureTest {

    private static final String PRACTICE_REPORT_DTOS = "..practices.report.dto..";
    private static final String OBSERVATION_DTOS = "..practices.observation.dto..";

    /**
     * Camel-case segments that read as norm-referenced comparison. Matched per segment, so an honest word
     * that merely contains one ("composition" contains "position") cannot false-positive.
     */
    private static final Set<String> COMPETITIVE_SEGMENTS = Set.of(
        "rank",
        "ranks",
        "ranking",
        "score",
        "scores",
        "elo",
        "xp",
        "league",
        "leagues",
        "percentile",
        "leaderboard",
        "standings",
        "position",
        "podium"
    );

    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])|_");

    private static boolean looksCompetitive(String fieldName) {
        for (String segment : CAMEL_BOUNDARY.split(fieldName)) {
            if (COMPETITIVE_SEGMENTS.contains(segment.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    @Test
    void practiceSurfaceDtosCarryNoCompetitiveFields() {
        ArchRule rule = fields()
            .that()
            .areDeclaredInClassesThat()
            .resideInAnyPackage(PRACTICE_REPORT_DTOS, OBSERVATION_DTOS)
            .should(
                new ArchCondition<JavaField>("not be named like a score or a rank") {
                    @Override
                    public void check(JavaField field, ConditionEvents events) {
                        if (looksCompetitive(field.getName())) {
                            events.add(
                                SimpleConditionEvent.violated(
                                    field,
                                    field.getFullName() +
                                        " reads as norm-referenced — practice surfaces compare work to the practice's" +
                                        " standard, not developers to each other (ADR 0028)"
                                )
                            );
                        }
                    }
                }
            );
        rule.check(classes);
    }

    /**
     * The practice report must not reach into the leaderboard for anything, not even a shared enum or a
     * formatting helper. That is how the ordering comes back: not as a decision, but as a convenient import
     * that nobody reviews. It also keeps #1374 able to delete the package outright.
     */
    @Test
    void practiceReportDoesNotDependOnTheLeaderboard() {
        ArchRule rule = noClasses()
            .that()
            .resideInAPackage("..practices.report..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..leaderboard..")
            .because(
                "the practice report replaces the leaderboard rather than reusing it (ADR 0028), and #1374 " +
                    "deletes that package"
            );
        rule.check(classes);
    }
}
