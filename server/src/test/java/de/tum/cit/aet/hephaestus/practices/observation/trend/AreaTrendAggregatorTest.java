package de.tum.cit.aet.hephaestus.practices.observation.trend;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class AreaTrendAggregatorTest {

    private final TrendProperties properties = new TrendProperties();

    @Test
    void shouldPreventThinEvidenceFromDominatingWellEvidencedPractice() {
        PracticeTrend wellEvidenced = trend(
            "well",
            BetaPosterior.from(24, 21).differenceFrom(BetaPosterior.from(24, 3))
        );
        PracticeTrend thin = trend("thin", BetaPosterior.from(3, 0).differenceFrom(BetaPosterior.from(3, 3)));

        PracticeTrend area = AreaTrendAggregator.aggregate(
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

        PracticeTrend area = AreaTrendAggregator.aggregate(
            "quality",
            List.of("testing", "naming"),
            List.of(improving, declining),
            Map.of("naming", 0.0),
            properties
        );

        assertThat(area.direction()).isEqualTo(TrendDirection.IMPROVING);
        assertThat(area.support().comparablePractices()).isEqualTo(1);
    }

    @Test
    void shouldNotGainConfidenceWhenEveryWeightIsScaledAlike() {
        // Scaling every practice's weight by the same factor says nothing new about the area, so the pooled
        // variance has to come out unchanged. It only does when the numerator carries Σ(w²/v) — the shorthand
        // 1/Σ(w/v) holds for unit weights alone and would shrink the variance by the factor instead, turning
        // this borderline case into a confident verdict on the strength of an admin setting.
        BetaPosterior.Difference difference = BetaPosterior.from(4, 4).differenceFrom(BetaPosterior.from(4, 2));

        TrendDirection unweighted = AreaTrendAggregator.aggregate(
            "quality",
            List.of("testing"),
            List.of(trend("testing", difference)),
            Map.of(),
            properties
        ).direction();
        TrendDirection doubled = AreaTrendAggregator.aggregate(
            "quality",
            List.of("testing"),
            List.of(trend("testing", difference)),
            Map.of("testing", 2.0),
            properties
        ).direction();

        assertThat(doubled).isEqualTo(unweighted);
    }

    @Test
    void shouldDateAnAreaSpanFromComparedOpportunitiesOnly() {
        // The merged trail carries every practice's opportunities by artifact, so a verdictless one from a
        // sibling practice could otherwise date the whole area.
        PracticeTrend looked = PracticeTrendCalculator.calculatePractice(
            "naming",
            List.of(inapplicable(7L, "2026-03-01T09:00:00Z")),
            Instant.parse("2026-01-01T00:00:00Z"),
            properties
        );
        PracticeTrend judged = PracticeTrendCalculator.calculatePractice(
            "testing",
            List.of(
                observation(40L, "2026-05-01T09:00:00Z", Assessment.BAD),
                observation(55L, "2026-06-01T09:00:00Z", Assessment.GOOD)
            ),
            Instant.parse("2026-01-01T00:00:00Z"),
            properties
        );

        PracticeTrend area = AreaTrendAggregator.aggregate(
            "quality",
            List.of("naming", "testing"),
            List.of(looked, judged),
            Map.of(),
            properties
        );

        assertThat(area.support().firstOpportunityAt()).isEqualTo(Instant.parse("2026-05-01T09:00:00Z"));
        assertThat(area.support().calendarSpanDays()).isEqualTo(32);
    }

    @Test
    void shouldMergeOneArtifactSeveralPracticesSawIntoOnePoint() {
        // Two practices reviewing the same pull request is one work item to a reader. Merging them by artifact
        // is what stops an area's chart from drawing the same PR once per practice — and what stops the
        // opportunity counts from inflating with the number of practices rather than the amount of work.
        PracticeTrend naming = PracticeTrendCalculator.calculatePractice(
            "naming",
            List.of(observation(40L, "2026-05-01T09:00:00Z", Assessment.GOOD)),
            Instant.parse("2026-01-01T00:00:00Z"),
            properties
        );
        PracticeTrend testing = PracticeTrendCalculator.calculatePractice(
            "testing",
            List.of(observation(40L, "2026-05-01T10:00:00Z", Assessment.BAD)),
            Instant.parse("2026-01-01T00:00:00Z"),
            properties
        );

        PracticeTrend area = AreaTrendAggregator.aggregate(
            "quality",
            List.of("naming", "testing"),
            List.of(naming, testing),
            Map.of(),
            properties
        );

        assertThat(area.opportunities()).hasSize(1);
        assertThat(area.opportunities().getFirst().outcomes().applicable()).isEqualTo(2);
    }

    private static Observation observation(long artifactId, String observedAt, Assessment assessment) {
        return Observation.builder()
            .id(UUID.randomUUID())
            .agentJobId(UUID.randomUUID())
            .artifactKind(ArtifactKinds.PULL_REQUEST)
            .artifactId(artifactId)
            .presence(Presence.PRESENT)
            .assessment(assessment)
            .observedAt(Instant.parse(observedAt))
            .build();
    }

    private static Observation inapplicable(long artifactId, String observedAt) {
        return Observation.builder()
            .id(UUID.randomUUID())
            .agentJobId(UUID.randomUUID())
            .artifactKind(ArtifactKinds.PULL_REQUEST)
            .artifactId(artifactId)
            .presence(Presence.NOT_APPLICABLE)
            .observedAt(Instant.parse(observedAt))
            .build();
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
