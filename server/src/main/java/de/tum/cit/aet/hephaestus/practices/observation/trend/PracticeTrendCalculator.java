package de.tum.cit.aet.hephaestus.practices.observation.trend;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Pure opportunity-indexed Bayesian trend calculator.
 *
 * <p>Calendar units are never samples: bursty repository activity is ordered by evidence opportunity, while
 * timestamps survive only as provenance. Recalculation after every event is legitimate because this is a
 * Bayesian posterior, not a repeatedly peeked frequentist test. The anytime-valid and human-dynamics grounding
 * is documented in {@code practice-trend-display-spec.md} §0.
 */
final class PracticeTrendCalculator {

    private PracticeTrendCalculator() {}

    static PracticeTrend calculatePractice(
        String practiceSlug,
        List<Observation> observations,
        Instant cutoff,
        TrendProperties properties
    ) {
        OpportunityBundler.Bundles bundles = OpportunityBundler.bundle(
            practiceSlug,
            observations,
            cutoff,
            properties.getBundleSize()
        );
        int missing = bundles.opportunitiesUntilComparable(properties.getMinBundleSize());
        List<EvidenceOpportunity> trail = cappedTrail(bundles.trail(), properties.getBundleSize());
        if (missing > 0) {
            return new PracticeTrend(
                practiceSlug,
                TrendScope.PRACTICE,
                TrendDirection.INSUFFICIENT_EVIDENCE,
                support(properties, SupportLevel.NONE, bundles, missing, null, null, trail),
                null,
                null,
                trail,
                null
            );
        }

        OutcomeVector currentOutcomes = outcomes(bundles.current());
        OutcomeVector previousOutcomes = outcomes(bundles.previous());
        BetaPosterior current = posterior(bundles.current());
        BetaPosterior previous = posterior(bundles.previous());
        BetaPosterior.Difference difference = current.differenceFrom(previous);
        TrendDirection direction = TrendDirectionRule.classify(
            difference,
            properties.getRopeHalfWidth(),
            properties.getCredibilityThreshold()
        );
        SupportLevel level =
            bundles.current().size() <= properties.getTentativeBundleSize() ||
            bundles.previous().size() <= properties.getTentativeBundleSize()
                ? SupportLevel.TENTATIVE
                : SupportLevel.WELL_SUPPORTED;
        return new PracticeTrend(
            practiceSlug,
            TrendScope.PRACTICE,
            direction,
            support(properties, level, bundles, 0, null, null, trail),
            currentOutcomes,
            previousOutcomes,
            trail,
            difference
        );
    }

    static PracticeTrend aggregateArea(
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
        List<EvidenceOpportunity> areaTrail = areaTrail(practiceTrends, properties.getBundleSize());
        if (comparable.isEmpty()) {
            int current = distinctOpportunityCount(practiceTrends, TrendBundle.CURRENT);
            int previous = distinctOpportunityCount(practiceTrends, TrendBundle.PREVIOUS);
            int missing = Math.max(0, properties.getMinBundleSize() - previous);
            return new PracticeTrend(
                areaSlug,
                TrendScope.AREA,
                TrendDirection.INSUFFICIENT_EVIDENCE,
                areaSupport(
                    properties,
                    SupportLevel.NONE,
                    current,
                    previous,
                    missing,
                    0,
                    eligiblePracticeSlugs.size(),
                    areaTrail
                ),
                null,
                null,
                areaTrail,
                null
            );
        }

        double weightedMean = 0.0;
        double totalPrecision = 0.0;
        for (PracticeTrend trend : comparable) {
            BetaPosterior.Difference difference = trend.difference();
            double precision = weightFor(trend.slug(), weights) / difference.variance();
            weightedMean += precision * difference.mean();
            totalPrecision += precision;
        }
        double mean = weightedMean / totalPrecision;
        double variance = 1.0 / totalPrecision;
        TrendDirection direction = classifyNormal(
            mean,
            variance,
            properties.getRopeHalfWidth(),
            properties.getCredibilityThreshold()
        );
        OutcomeVector current = comparable
            .stream()
            .map(PracticeTrend::currentOutcomes)
            .filter(java.util.Objects::nonNull)
            .reduce(OutcomeVector.EMPTY, OutcomeVector::plus);
        OutcomeVector previous = comparable
            .stream()
            .map(PracticeTrend::previousOutcomes)
            .filter(java.util.Objects::nonNull)
            .reduce(OutcomeVector.EMPTY, OutcomeVector::plus);
        int currentCount = distinctOpportunityCount(comparable, TrendBundle.CURRENT);
        int previousCount = distinctOpportunityCount(comparable, TrendBundle.PREVIOUS);
        SupportLevel level = comparable
            .stream()
            .allMatch(trend -> trend.support().level() == SupportLevel.WELL_SUPPORTED)
            ? SupportLevel.WELL_SUPPORTED
            : SupportLevel.TENTATIVE;
        return new PracticeTrend(
            areaSlug,
            TrendScope.AREA,
            direction,
            areaSupport(
                properties,
                level,
                currentCount,
                previousCount,
                0,
                comparable.size(),
                eligiblePracticeSlugs.size(),
                areaTrail
            ),
            current,
            previous,
            areaTrail,
            null
        );
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
        double inside = Math.max(0.0, 1.0 - above - below);
        if (above >= threshold) return TrendDirection.IMPROVING;
        if (below >= threshold) return TrendDirection.DECLINING;
        if (inside >= threshold) return TrendDirection.STABLE;
        return TrendDirection.UNCERTAIN;
    }

    private static double normalCdf(double value) {
        double sign = value < 0 ? -1.0 : 1.0;
        double x = Math.abs(value) / Math.sqrt(2.0);
        double t = 1.0 / (1.0 + 0.3275911 * x);
        double polynomial =
            (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t;
        double erf = sign * (1.0 - polynomial * Math.exp(-x * x));
        return 0.5 * (1.0 + erf);
    }

    private static BetaPosterior posterior(List<EvidenceOpportunity> opportunities) {
        double sum = opportunities
            .stream()
            .mapToDouble(opportunity -> opportunity.outcomes().positiveShare())
            .sum();
        return BetaPosterior.from(opportunities.size(), sum);
    }

    private static OutcomeVector outcomes(List<EvidenceOpportunity> opportunities) {
        return opportunities
            .stream()
            .map(EvidenceOpportunity::outcomes)
            .reduce(OutcomeVector.EMPTY, OutcomeVector::plus);
    }

    private static TrendSupport support(
        TrendProperties properties,
        SupportLevel level,
        OpportunityBundler.Bundles bundles,
        int missing,
        @Nullable Integer comparablePractices,
        @Nullable Integer eligiblePractices,
        List<EvidenceOpportunity> trail
    ) {
        return support(
            properties,
            level,
            bundles.current().size(),
            bundles.previous().size(),
            missing,
            comparablePractices,
            eligiblePractices,
            trail
        );
    }

    private static TrendSupport areaSupport(
        TrendProperties properties,
        SupportLevel level,
        int current,
        int previous,
        int missing,
        int comparablePractices,
        int eligiblePractices,
        List<EvidenceOpportunity> trail
    ) {
        return support(properties, level, current, previous, missing, comparablePractices, eligiblePractices, trail);
    }

    private static TrendSupport support(
        TrendProperties properties,
        SupportLevel level,
        int current,
        int previous,
        int missing,
        @Nullable Integer comparablePractices,
        @Nullable Integer eligiblePractices,
        List<EvidenceOpportunity> trail
    ) {
        Instant first = trail.stream().map(EvidenceOpportunity::occurredAt).min(Instant::compareTo).orElse(null);
        Instant last = trail.stream().map(EvidenceOpportunity::occurredAt).max(Instant::compareTo).orElse(null);
        Integer span =
            first == null || last == null
                ? null
                : (int) ChronoUnit.DAYS.between(
                      first.atZone(ZoneOffset.UTC).toLocalDate(),
                      last.atZone(ZoneOffset.UTC).toLocalDate()
                  ) +
                  1;
        return new TrendSupport(
            level,
            current,
            previous,
            missing,
            comparablePractices,
            eligiblePractices,
            first,
            last,
            span,
            properties.getBundleSize(),
            properties.getRopeHalfWidth(),
            properties.getCredibilityThreshold()
        );
    }

    private static List<EvidenceOpportunity> cappedTrail(List<EvidenceOpportunity> trail, int bundleSize) {
        int cap = 2 * bundleSize + 4;
        return trail.stream().skip(Math.max(0, trail.size() - cap)).toList();
    }

    private static List<EvidenceOpportunity> areaTrail(Collection<PracticeTrend> trends, int bundleSize) {
        record Key(ArtifactKind type, long id) {}
        Map<Key, EvidenceOpportunity> combined = new LinkedHashMap<>();
        for (PracticeTrend trend : trends) {
            for (EvidenceOpportunity opportunity : trend.opportunities()) {
                Key key = new Key(opportunity.artifactKind(), opportunity.artifactId());
                combined.merge(
                    key,
                    new EvidenceOpportunity(
                        "",
                        opportunity.artifactKind(),
                        opportunity.artifactId(),
                        opportunity.occurredAt(),
                        opportunity.outcomes(),
                        opportunity.bundle()
                    ),
                    (left, right) ->
                        new EvidenceOpportunity(
                            "",
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
        return cappedTrail(sorted, bundleSize);
    }

    private static TrendBundle strongerBundle(TrendBundle left, TrendBundle right) {
        if (left == TrendBundle.CURRENT || right == TrendBundle.CURRENT) return TrendBundle.CURRENT;
        if (left == TrendBundle.PREVIOUS || right == TrendBundle.PREVIOUS) return TrendBundle.PREVIOUS;
        return TrendBundle.OLDER;
    }

    private static int distinctOpportunityCount(Collection<PracticeTrend> trends, TrendBundle bundle) {
        return (int) trends
            .stream()
            .flatMap(trend -> trend.opportunities().stream())
            .filter(opportunity -> opportunity.bundle() == bundle)
            .map(opportunity -> opportunity.artifactKind() + ":" + opportunity.artifactId())
            .distinct()
            .count();
    }
}
