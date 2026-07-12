package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;

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
 * The practice surfaces are criterion-referenced by design (ADR 0023): they must never expose a
 * field that orders or scores developers against each other. This pins that invariant on the wire
 * layer — a DTO field named like a score or rank invites a client to render a comparison, no matter
 * how the docs frame it.
 */
class NonCompetitiveSurfaceArchTest extends HephaestusArchitectureTest {

    /**
     * Camel-case segments that smell like norm-referenced comparison. Matched per segment so
     * substrings inside honest words (the "elo" in developingCount) cannot false-positive.
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
        "leaderboard"
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
            .resideInAnyPackage("..practices.report.dto..", "..practices.observation.dto..")
            .should(
                new ArchCondition<JavaField>("not be named like a score or rank") {
                    @Override
                    public void check(JavaField field, ConditionEvents events) {
                        if (looksCompetitive(field.getName())) {
                            events.add(
                                SimpleConditionEvent.violated(
                                    field,
                                    field.getFullName() +
                                        " looks norm-referenced — practice surfaces must stay criterion-referenced (ADR 0023)"
                                )
                            );
                        }
                    }
                }
            );
        rule.check(classes);
    }
}
