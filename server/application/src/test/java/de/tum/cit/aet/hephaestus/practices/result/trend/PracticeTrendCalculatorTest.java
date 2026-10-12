package de.tum.cit.aet.hephaestus.practices.result.trend;

import static de.tum.cit.aet.hephaestus.practices.result.trend.TrendResults.clean;
import static de.tum.cit.aet.hephaestus.practices.result.trend.TrendResults.judged;
import static de.tum.cit.aet.hephaestus.practices.result.trend.TrendResults.noVerdict;
import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class PracticeTrendCalculatorTest {

    private final TrendProperties properties = new TrendProperties();

    @Test
    void shouldDateAPracticeSpanFromComparedOpportunitiesOnly() {
        // The run in March looked and found nothing to judge. It belongs in the trail — the practice DID see
        // that piece of reviewed work — but "these comparisons span N days" must not count it, or a quiet stretch would
        // stretch the provenance without adding a comparison.
        PracticeTrend trend = PracticeTrendCalculator.calculatePractice(
                "testing",
                List.of(
                        noVerdict(7L, "2026-03-01T09:00:00Z"),
                        judged(40L, "2026-05-01T09:00:00Z", Outcome.NOT_MET),
                        judged(55L, "2026-06-01T09:00:00Z", Outcome.MET)),
                Instant.parse("2026-01-01T00:00:00Z"),
                properties);

        assertThat(trend.support().firstOpportunityAt()).isEqualTo(Instant.parse("2026-05-01T09:00:00Z"));
        assertThat(trend.support().lastOpportunityAt()).isEqualTo(Instant.parse("2026-06-01T09:00:00Z"));
        assertThat(trend.support().calendarSpanDays()).isEqualTo(32);
        // Still visible in the trail, which is what the chart draws.
        assertThat(trend.opportunities()).hasSize(3);
    }

    @Test
    void shouldReportPracticeScopeWithoutPracticeCounts() {
        // The two practice counts are a group fact. At practice scope they are absent, and that absence
        // reaches the client — which is why the support factory has two entry points rather than one with
        // optional arguments.
        PracticeTrend trend = PracticeTrendCalculator.calculatePractice(
                "testing",
                List.of(judged(40L, "2026-05-01T09:00:00Z", Outcome.MET)),
                Instant.parse("2026-01-01T00:00:00Z"),
                properties);

        assertThat(trend.support().comparablePractices()).isNull();
        assertThat(trend.support().eligiblePractices()).isNull();
    }

    @Test
    void shouldCountEveryJudgedPieceOfWorkPastTheChartsCap() {
        // Sixteen judged pull requests and one with nothing to judge. The chart keeps twelve; the reader is told
        // about all sixteen, and that the standing reads the newest four of them.
        List<Observation> observations = new ArrayList<>();
        for (int day = 1; day <= 16; day++) {
            observations.add(judged(day, "2026-05-%02dT09:00:00Z".formatted(day), Outcome.MET));
        }
        observations.add(noVerdict(99L, "2026-05-20T09:00:00Z"));

        PracticeTrend trend = PracticeTrendCalculator.calculatePractice(
                "testing", observations, Instant.parse("2026-01-01T00:00:00Z"), properties);

        assertThat(trend.support().reviewedWork()).isEqualTo(16);
        assertThat(trend.support().standingWork()).isEqualTo(4);
        assertThat(trend.support().reviewedWorkKind()).isEqualTo(ArtifactKinds.PULL_REQUEST);
        assertThat(trend.opportunities().stream().filter(EvidenceOpportunity::decided))
                .hasSize(12);
    }

    @Test
    void shouldCountAPieceOfWorkReviewedTwiceOnceAndNameNoKindForMixedWork() {
        PracticeTrend trend = PracticeTrendCalculator.calculatePractice(
                "testing",
                List.of(
                        judged(40L, "2026-05-01T09:00:00Z", Outcome.NOT_MET),
                        judged(40L, "2026-05-02T09:00:00Z", Outcome.MET),
                        clean(40L, "2026-05-03T09:00:00Z", ArtifactKinds.ISSUE)),
                Instant.parse("2026-01-01T00:00:00Z"),
                properties);

        assertThat(trend.support().reviewedWork()).isEqualTo(2);
        assertThat(trend.support().reviewedWorkKind()).isNull();
    }

    @Test
    void shouldCountNothingWhereNoReviewReachedAVerdict() {
        PracticeTrend trend = PracticeTrendCalculator.calculatePractice(
                "testing",
                List.of(noVerdict(7L, "2026-03-01T09:00:00Z")),
                Instant.parse("2026-01-01T00:00:00Z"),
                properties);

        assertThat(trend.support().reviewedWork()).isZero();
        assertThat(trend.support().standingWork()).isZero();
        assertThat(trend.support().reviewedWorkKind()).isNull();
    }
}
