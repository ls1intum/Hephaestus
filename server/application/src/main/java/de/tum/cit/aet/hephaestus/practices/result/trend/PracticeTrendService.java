package de.tum.cit.aet.hephaestus.practices.result.trend;

import de.tum.cit.aet.hephaestus.practices.result.ResultView;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.OptionalDouble;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PracticeTrendService {
    public static final int LOOKBACK_DAYS = 90;

    private final TrendProperties properties;
    private final Clock clock;

    /** The start of today's trend horizon, which is the practice profile's look-back. */
    public Instant horizon() {
        return clock.instant().minus(LOOKBACK_DAYS, ChronoUnit.DAYS);
    }

    /**
     * How many of a practice's newest opportunities its standing reads: its current bundle, so the standing work a
     * trend reports is the work the standing was read from.
     */
    public int standingWindow() {
        return properties.getBundleSize();
    }

    /** One practice's trend over its evidence observed from {@code since}; insufficient over none. */
    public PracticeTrend calculatePractice(List<ResultView> evidence, Instant since) {
        return PracticeTrendCalculator.calculatePractice(evidence, since, properties);
    }

    /** {@link PracticeTrend#recentMetShare} over a practice's requested and backfilled work alone. */
    public OptionalDouble selfSelectedMetShare(List<ResultView> evidence, Instant since, int window, double decay) {
        return PracticeTrend.recentMetShare(
                OpportunityBundler.opportunities(evidence, since, true).reversed(), window, decay);
    }

    public PracticeTrend calculateGroup(
            Collection<String> eligiblePracticeSlugs, Collection<PracticeTrend> practiceTrends) {
        return GroupTrendAggregator.aggregate(eligiblePracticeSlugs, practiceTrends, properties);
    }
}
