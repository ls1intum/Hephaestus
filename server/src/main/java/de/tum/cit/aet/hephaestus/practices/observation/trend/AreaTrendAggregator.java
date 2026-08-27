package de.tum.cit.aet.hephaestus.practices.observation.trend;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Combines a practice area's per-practice trends into one direction.
 *
 * <p>A different estimator from the practice level, deliberately. A practice compares two bundles of raw
 * opportunities, so its difference is computed exactly on {@link BetaPosterior}'s grid. An area has no
 * opportunities of its own — it has a handful of already-computed differences — so it combines them by
 * inverse-variance weighting, the standard fixed-effect meta-analysis: each practice counts in proportion to
 * how precisely its own change was measured, scaled by the weight the area gives it. The result is treated as
 * normal, which the sum of several independent differences approaches; at the practice level that
 * approximation would be wrong, which is why the exact grid stays there.
 *
 * <p>A practice with weight zero is excluded rather than down-weighted, so an area can be composed without
 * one of its practices moving it at all.
 */
final class AreaTrendAggregator {

    private AreaTrendAggregator() {}

    static PracticeTrend aggregate(
        String areaSlug,
        Collection<String> eligiblePracticeSlugs,
        Collection<PracticeTrend> practiceTrends,
        Map<String, Double> weights,
        TrendProperties properties
    ) {
        List<PracticeTrend> comparable = practiceTrends
            .stream()
            .filter(trend -> trend.difference() != null)
            .filter(trend -> weightFor(trend.slug(), weights) > 0.0)
            .toList();
        List<EvidenceOpportunity> trail = mergedTrail(practiceTrends, properties.getBundleSize());
        if (comparable.isEmpty()) {
            int current = distinctOpportunityCount(practiceTrends, TrendBundle.CURRENT);
            int previous = distinctOpportunityCount(practiceTrends, TrendBundle.PREVIOUS);
            int missing = Math.max(0, properties.getMinBundleSize() - previous);
            return new PracticeTrend(
                areaSlug,
                TrendScope.AREA,
                TrendDirection.INSUFFICIENT_EVIDENCE,
                TrendSupportFactory.forArea(
                    properties,
                    current,
                    previous,
                    missing,
                    0,
                    eligiblePracticeSlugs.size(),
                    trail
                ),
                null,
                null,
                trail,
                null
            );
        }

        Pooled pooled = pool(comparable, weights);
        TrendDirection direction = classifyNormal(
            pooled.mean(),
            pooled.variance(),
            properties.getRopeHalfWidth(),
            properties.getCredibilityThreshold()
        );
        return new PracticeTrend(
            areaSlug,
            TrendScope.AREA,
            direction,
            TrendSupportFactory.forArea(
                properties,
                distinctOpportunityCount(comparable, TrendBundle.CURRENT),
                distinctOpportunityCount(comparable, TrendBundle.PREVIOUS),
                0,
                comparable.size(),
                eligiblePracticeSlugs.size(),
                trail
            ),
            summed(comparable, PracticeTrend::currentOutcomes),
            summed(comparable, PracticeTrend::previousOutcomes),
            trail,
            // No pooled Difference: the area never diffed two bundles, and publishing one would invite a
            // reader to treat the normal approximation as though it were the exact posterior.
            null
        );
    }

    /** The inverse-variance pooled estimate of the area's change. */
    private record Pooled(double mean, double variance) {}

    private static Pooled pool(List<PracticeTrend> comparable, Map<String, Double> weights) {
        double weightedMean = 0.0;
        double totalPrecision = 0.0;
        double varianceNumerator = 0.0;
        for (PracticeTrend trend : comparable) {
            // `comparable` was filtered on difference() != null above; the nullness analysis
            // does not carry that across the stream boundary.
            BetaPosterior.Difference difference = Objects.requireNonNull(trend.difference());
            double weight = weightFor(trend.slug(), weights);
            double precision = weight / difference.variance();
            weightedMean += precision * difference.mean();
            totalPrecision += precision;
            // Var(Σ pᵢmᵢ / Σ pᵢ) = Σ(pᵢ²·vᵢ) / (Σ pᵢ)², and pᵢ²·vᵢ collapses to wᵢ²/vᵢ = wᵢ·pᵢ.
            // Only when every weight is 1 does that reduce to the familiar 1/Σ pᵢ, so the
            // numerator has to be carried separately — a weight of 2 would otherwise report
            // half the variance it has and turn an UNCERTAIN area into a confident verdict.
            varianceNumerator += weight * precision;
        }
        return new Pooled(weightedMean / totalPrecision, varianceNumerator / (totalPrecision * totalPrecision));
    }

    /**
     * Adds up one bundle's outcomes across the practices.
     *
     * <p>The accessor is nullable — a trend that was never comparable carries no vectors — but every trend
     * here passed the {@code difference() != null} filter, so in practice none is skipped. The filter states
     * that rather than asserting it, because the alternative is an exception if the two conditions ever part.
     */
    private static OutcomeVector summed(
        List<PracticeTrend> trends,
        Function<PracticeTrend, @Nullable OutcomeVector> axis
    ) {
        return trends.stream().map(axis).filter(Objects::nonNull).reduce(OutcomeVector.EMPTY, OutcomeVector::plus);
    }

    private static double weightFor(String slug, Map<String, Double> weights) {
        double weight = weights.getOrDefault(slug, 1.0);
        if (!Double.isFinite(weight) || weight < 0.0) {
            throw new IllegalArgumentException("Practice trend weight must be finite and non-negative");
        }
        return weight;
    }

    private static TrendDirection classifyNormal(double mean, double variance, double rope, double threshold) {
        double standardDeviation = Math.sqrt(variance);
        double above = 1.0 - normalCdf((rope - mean) / standardDeviation);
        double below = normalCdf((-rope - mean) / standardDeviation);
        if (above >= threshold) return TrendDirection.IMPROVING;
        if (below >= threshold) return TrendDirection.DECLINING;
        return TrendDirection.UNCERTAIN;
    }

    /** Abramowitz-Stegun 7.1.26: absolute error below 1.5e-7, far under what a 0.90 threshold can notice. */
    private static double normalCdf(double value) {
        double sign = value < 0 ? -1.0 : 1.0;
        double x = Math.abs(value) / Math.sqrt(2.0);
        double t = 1.0 / (1.0 + 0.3275911 * x);
        double polynomial =
            (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t;
        double erf = sign * (1.0 - polynomial * Math.exp(-x * x));
        return 0.5 * (1.0 + erf);
    }

    /**
     * One entry per artifact the area saw, whichever of its practices saw it.
     *
     * <p>Two practices reviewing the same pull request is one work item to a reader, so their outcomes are
     * summed into a single point rather than drawn twice. The merged point takes the stronger bundle of the
     * two: an artifact that is current evidence for any practice is current evidence for the area.
     */
    private static List<EvidenceOpportunity> mergedTrail(Collection<PracticeTrend> trends, int bundleSize) {
        record Key(ArtifactKind type, long id) {}
        Map<Key, EvidenceOpportunity> combined = new LinkedHashMap<>();
        for (PracticeTrend trend : trends) {
            for (EvidenceOpportunity opportunity : trend.opportunities()) {
                combined.merge(
                    new Key(opportunity.artifactKind(), opportunity.artifactId()),
                    opportunity,
                    (left, right) ->
                        new EvidenceOpportunity(
                            left.artifactKind(),
                            left.artifactId(),
                            left.occurredAt().isAfter(right.occurredAt()) ? left.occurredAt() : right.occurredAt(),
                            left.outcomes().plus(right.outcomes()),
                            strongerBundle(left.bundle(), right.bundle())
                        )
                );
            }
        }
        List<EvidenceOpportunity> sorted = combined
            .values()
            .stream()
            .sorted(Comparator.comparing(EvidenceOpportunity::occurredAt))
            .toList();
        return OpportunityBundler.cappedTrail(sorted, bundleSize);
    }

    private static TrendBundle strongerBundle(TrendBundle left, TrendBundle right) {
        if (left == TrendBundle.CURRENT || right == TrendBundle.CURRENT) return TrendBundle.CURRENT;
        if (left == TrendBundle.PREVIOUS || right == TrendBundle.PREVIOUS) return TrendBundle.PREVIOUS;
        return TrendBundle.OLDER;
    }

    private static int distinctOpportunityCount(Collection<PracticeTrend> trends, TrendBundle bundle) {
        record Key(ArtifactKind type, long id) {}
        return (int) trends
            .stream()
            .flatMap(trend -> trend.opportunities().stream())
            .filter(opportunity -> opportunity.bundle() == bundle)
            .map(opportunity -> new Key(opportunity.artifactKind(), opportunity.artifactId()))
            .distinct()
            .count();
    }
}
