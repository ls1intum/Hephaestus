package de.tum.cit.aet.hephaestus.practices.result;

import static de.tum.cit.aet.hephaestus.practices.result.trend.TrendResults.clean;
import static de.tum.cit.aet.hephaestus.practices.result.trend.TrendResults.problem;
import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.result.PracticeStandingService.StandingSnapshot.PracticeStandingSnapshot;
import de.tum.cit.aet.hephaestus.practices.result.dto.PracticeStandingDTO;
import de.tum.cit.aet.hephaestus.practices.result.trend.PracticeTrend;
import de.tum.cit.aet.hephaestus.practices.result.trend.PracticeTrendService;
import de.tum.cit.aet.hephaestus.practices.result.trend.TrendProperties;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeStanding;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class PracticeStandingTest {

    private static final String SLUG = "testing";
    private static final Instant NOW = Instant.parse("2026-05-01T09:00:00Z");

    @Test
    void shouldHoldWhenTheOnlySlipIsOlderThanTheStandingWindow() {
        // Four clean in the window of four; the slip before them is history the bar does not reach.
        assertThat(standing(PracticeStanding.STRENGTH, trendOf(false, true, true, true, true))
                        .isHolding(4))
                .isTrue();
    }

    @Test
    void shouldNotHoldWhenAnOpportunityInTheWindowRaisedAProblem() {
        // Three clean of four in the window: a strength carried by weight, not a held one.
        assertThat(standing(PracticeStanding.STRENGTH, trendOf(false, true, true, true))
                        .isHolding(4))
                .isFalse();
    }

    @Test
    void shouldHoldAfterThreeCleanPiecesOfWorkWithinTheWindow() {
        // Three clean pieces of work meet the recurring-claim threshold.
        assertThat(standing(PracticeStanding.STRENGTH, trendOf(true, true, true))
                        .isHolding(4))
                .isTrue();
    }

    @Test
    void shouldNotHoldWhenTheStandingIsBelowStrength() {
        assertThat(standing(PracticeStanding.MIXED, trendOf(true, true, true, true))
                        .isHolding(4))
                .isFalse();
    }

    @Test
    void oneMetResultDoesNotMakeARecurringStrengthClaim() {
        assertThat(standing(PracticeStanding.STRENGTH, trendOf(true)).isHolding(4))
                .isFalse();
    }

    private static PracticeStandingSnapshot standing(PracticeStanding standing, PracticeTrend trend) {
        PracticeStandingDTO dto = new PracticeStandingDTO(
                SLUG, "Testing", null, null, null, null, standing, List.of(), List.of(), null, null, false);
        return new PracticeStandingSnapshot(dto, List.of(), List.of(), trend, null);
    }

    private static PracticeTrend trendOf(boolean... cleanOldestFirst) {
        List<ResultView> practiceResults = IntStream.range(0, cleanOldestFirst.length)
                .mapToObj(index -> result(index, cleanOldestFirst[index]))
                .toList();
        PracticeTrendService trends = new PracticeTrendService(new TrendProperties(), Clock.fixed(NOW, ZoneOffset.UTC));
        return trends.calculatePractice(practiceResults, trends.horizon());
    }

    private static ResultView result(int index, boolean isClean) {
        // Newest last, one day apart, well inside the trend horizon.
        String observedAt = NOW.minus(Duration.ofDays(30L - index)).toString();
        return isClean ? clean(index + 1L, observedAt) : problem(index + 1L, observedAt);
    }
}
