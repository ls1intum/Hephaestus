package de.tum.cit.aet.hephaestus.practices.observation.trend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class PracticeTrendCalculatorTest {

    private final TrendProperties properties = new TrendProperties();

    @Test
    void shouldPreventThinEvidenceFromDominatingWellEvidencedPractice() {
        PracticeTrend wellEvidenced = trend(
            "well",
            BetaPosterior.from(24, 21).differenceFrom(BetaPosterior.from(24, 3))
        );
        PracticeTrend thin = trend("thin", BetaPosterior.from(3, 0).differenceFrom(BetaPosterior.from(3, 3)));

        PracticeTrend area = PracticeTrendCalculator.aggregateArea(
            "quality",
            List.of("well", "thin"),
            List.of(wellEvidenced, thin),
            Map.of(),
            properties
        );

        assertThat(area.direction()).isNotEqualTo(TrendDirection.DECLINING);
    }

    @Test
    void shouldExcludePracticeWithZeroAdminWeight() {
        PracticeTrend improving = trend(
            "testing",
            BetaPosterior.from(12, 11).differenceFrom(BetaPosterior.from(12, 1))
        );
        PracticeTrend declining = trend("naming", BetaPosterior.from(12, 1).differenceFrom(BetaPosterior.from(12, 11)));

        PracticeTrend area = PracticeTrendCalculator.aggregateArea(
            "quality",
            List.of("testing", "naming"),
            List.of(improving, declining),
            Map.of("naming", 0.0),
            properties
        );

        assertThat(area.direction()).isEqualTo(TrendDirection.IMPROVING);
        assertThat(area.support().comparablePractices()).isEqualTo(1);
    }

    private PracticeTrend trend(String slug, BetaPosterior.Difference difference) {
        TrendSupport support = new TrendSupport(
            4,
            4,
            0,
            null,
            null,
            null,
            null,
            null,
            properties.getBundleSize(),
            properties.getRopeHalfWidth(),
            properties.getCredibilityThreshold()
        );
        return new PracticeTrend(
            slug,
            TrendScope.PRACTICE,
            TrendDirection.UNCERTAIN,
            support,
            OutcomeVector.EMPTY,
            OutcomeVector.EMPTY,
            List.of(),
            difference
        );
    }
}
