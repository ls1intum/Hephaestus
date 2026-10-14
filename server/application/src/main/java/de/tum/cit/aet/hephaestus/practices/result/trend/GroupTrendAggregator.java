package de.tum.cit.aet.hephaestus.practices.result.trend;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.spi.TrendDirection;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Shared reviewed work can correlate practice estimates, so use the conservative perfect-correlation variance bound.
final class GroupTrendAggregator {

    private GroupTrendAggregator() {}

    static PracticeTrend aggregate(
            Collection<String> eligiblePracticeSlugs,
            Collection<PracticeTrend> practiceTrends,
            TrendProperties properties) {
        List<PracticeTrend> comparable = practiceTrends.stream()
                .filter(trend -> trend.difference() != null)
                .toList();
        List<EvidenceOpportunity> trail = mergedTrail(practiceTrends, properties.getBundleSize());
        // Over every practice that joined, compared or not: each one votes in the group's standing.
        int standingWork = distinctOpportunityCount(practiceTrends, TrendBundle.CURRENT);
        Map<Long, ItemKind> reviewedWork = reviewedWork(practiceTrends);
        if (comparable.isEmpty()) {
            int previous = distinctOpportunityCount(practiceTrends, TrendBundle.PREVIOUS);
            int missing = practiceTrends.stream()
                    .mapToInt(trend -> trend.support().opportunitiesUntilComparable())
                    .min()
                    .orElse(properties.getBundleSize() + properties.getMinBundleSize());
            return new PracticeTrend(
                    TrendDirection.INSUFFICIENT_EVIDENCE,
                    TrendSupport.forGroup(
                            properties,
                            standingWork,
                            previous,
                            distinctComparedOpportunities(practiceTrends),
                            missing,
                            standingWork,
                            reviewedWork,
                            0,
                            eligiblePracticeSlugs.size(),
                            trail),
                    trail,
                    reviewedWork,
                    null);
        }

        Pooled pooled = pool(comparable);
        TrendDirection direction = classifyNormal(
                pooled.mean(), pooled.variance(), properties.getRopeHalfWidth(), properties.getCredibilityThreshold());
        return new PracticeTrend(
                direction,
                TrendSupport.forGroup(
                        properties,
                        distinctOpportunityCount(comparable, TrendBundle.CURRENT),
                        distinctOpportunityCount(comparable, TrendBundle.PREVIOUS),
                        distinctComparedOpportunities(comparable),
                        0,
                        standingWork,
                        reviewedWork,
                        comparable.size(),
                        eligiblePracticeSlugs.size(),
                        trail),
                trail,
                reviewedWork,
                null);
    }

    private record Pooled(double mean, double variance) {}

    private static Pooled pool(List<PracticeTrend> comparable) {
        double weightedMean = 0.0;
        double totalPrecision = 0.0;
        double correlatedStandardDeviation = 0.0;
        for (PracticeTrend trend : comparable) {
            BetaPosterior.Difference difference = Objects.requireNonNull(trend.difference());
            double precision = 1.0 / difference.variance();
            weightedMean += precision * difference.mean();
            totalPrecision += precision;
            // Practices are often reviewed on the same work. Treating their estimates as independent would
            // manufacture confidence, so use the conservative perfect-positive-correlation bound.
            correlatedStandardDeviation += precision * Math.sqrt(difference.variance());
        }
        double standardDeviation = correlatedStandardDeviation / totalPrecision;
        return new Pooled(weightedMean / totalPrecision, standardDeviation * standardDeviation);
    }

    private static TrendDirection classifyNormal(double mean, double variance, double rope, double threshold) {
        double standardDeviation = Math.sqrt(variance);
        double above = 1.0 - normalCdf((rope - mean) / standardDeviation);
        double below = normalCdf((-rope - mean) / standardDeviation);
        if (above >= threshold) return TrendDirection.IMPROVING;
        if (below >= threshold) return TrendDirection.DECLINING;
        return TrendDirection.UNCERTAIN;
    }

    // Abramowitz-Stegun 7.1.26 bounds absolute error below 1.5e-7.
    private static double normalCdf(double value) {
        double sign = value < 0 ? -1.0 : 1.0;
        double x = Math.abs(value) / Math.sqrt(2.0);
        double t = 1.0 / (1.0 + 0.3275911 * x);
        double polynomial =
                (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t;
        double erf = sign * (1.0 - polynomial * Math.exp(-x * x));
        return 0.5 * (1.0 + erf);
    }

    private static List<EvidenceOpportunity> mergedTrail(Collection<PracticeTrend> trends, int bundleSize) {
        Map<Long, EvidenceOpportunity> combined = new LinkedHashMap<>();
        for (PracticeTrend trend : trends) {
            for (EvidenceOpportunity opportunity : trend.opportunities()) {
                combined.merge(opportunity.key(), opportunity, (left, right) -> {
                    EvidenceOpportunity newer = left.occurredAt().isAfter(right.occurredAt()) ? left : right;
                    return new EvidenceOpportunity(
                            left.itemKind(),
                            left.itemId(),
                            newer.reviewId(),
                            newer.occurredAt(),
                            left.outcomes().plus(right.outcomes()),
                            strongerBundle(left.bundle(), right.bundle()));
                });
            }
        }
        List<EvidenceOpportunity> sorted = combined.values().stream()
                .sorted(Comparator.comparing(EvidenceOpportunity::occurredAt))
                .toList();
        return OpportunityBundler.cappedTrail(sorted, bundleSize);
    }

    private static TrendBundle strongerBundle(TrendBundle left, TrendBundle right) {
        if (left == TrendBundle.CURRENT || right == TrendBundle.CURRENT) return TrendBundle.CURRENT;
        if (left == TrendBundle.PREVIOUS || right == TrendBundle.PREVIOUS) return TrendBundle.PREVIOUS;
        return TrendBundle.OLDER;
    }

    // An item can appear in different practice bundles; count it once across the group.
    private static int distinctComparedOpportunities(Collection<PracticeTrend> trends) {
        return (int) trends.stream()
                .flatMap(trend -> trend.opportunities().stream())
                .filter(opportunity ->
                        opportunity.bundle() == TrendBundle.CURRENT || opportunity.bundle() == TrendBundle.PREVIOUS)
                .map(EvidenceOpportunity::key)
                .distinct()
                .count();
    }

    private static Map<Long, ItemKind> reviewedWork(Collection<PracticeTrend> trends) {
        Map<Long, ItemKind> work = new LinkedHashMap<>();
        trends.forEach(trend -> work.putAll(trend.reviewedWork()));
        return work;
    }

    private static int distinctOpportunityCount(Collection<PracticeTrend> trends, TrendBundle bundle) {
        return (int) trends.stream()
                .flatMap(trend -> trend.opportunities().stream())
                .filter(opportunity -> opportunity.bundle() == bundle)
                .map(EvidenceOpportunity::key)
                .distinct()
                .count();
    }
}
