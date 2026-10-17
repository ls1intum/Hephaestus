package de.tum.cit.aet.hephaestus.practices.result.trend;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.result.ResultView;
import de.tum.cit.aet.hephaestus.practices.spi.TrendDirection;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class PracticeTrendCalculator {

    private PracticeTrendCalculator() {}

    static PracticeTrend calculatePractice(
            List<ResultView> practiceResults, Instant cutoff, TrendProperties properties) {
        OpportunityBundler.Bundles bundles =
                OpportunityBundler.bundle(practiceResults, cutoff, properties.getBundleSize());
        int missing = bundles.opportunitiesUntilComparable(properties.getBundleSize(), properties.getMinBundleSize());
        // Counted before the cap: the cap shortens a chart, not the work a reader is told was reviewed.
        Map<Long, ItemKind> reviewedWork = bundles.trail().stream()
                .filter(EvidenceOpportunity::decided)
                .collect(Collectors.toMap(
                        EvidenceOpportunity::key, EvidenceOpportunity::itemKind, (left, right) -> left));
        List<EvidenceOpportunity> trail = OpportunityBundler.cappedTrail(bundles.trail(), properties.getBundleSize());
        TrendSupport support = TrendSupport.forGroup(
                properties,
                bundles.current().size(),
                bundles.previous().size(),
                bundles.current().size() + bundles.previous().size(),
                missing,
                bundles.current().size(),
                reviewedWork,
                null,
                null,
                trail);
        if (missing > 0) {
            return new PracticeTrend(TrendDirection.INSUFFICIENT_EVIDENCE, support, trail, reviewedWork, null);
        }

        BetaPosterior.Difference difference =
                posterior(bundles.current()).differenceFrom(posterior(bundles.previous()));
        TrendDirection direction = TrendDirectionRule.classify(
                difference, properties.getRopeHalfWidth(), properties.getCredibilityThreshold());
        return new PracticeTrend(direction, support, trail, reviewedWork, difference);
    }

    private static BetaPosterior posterior(List<EvidenceOpportunity> opportunities) {
        double sum = opportunities.stream()
                .mapToDouble(opportunity -> opportunity.outcomes().metShare())
                .sum();
        return BetaPosterior.from(opportunities.size(), sum);
    }
}
