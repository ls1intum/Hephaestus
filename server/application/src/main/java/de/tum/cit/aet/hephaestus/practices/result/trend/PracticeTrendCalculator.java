package de.tum.cit.aet.hephaestus.practices.result.trend;

import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.result.ReviewedWorkKey;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure opportunity-indexed Bayesian trend calculator for one practice.
 *
 * <p>Calendar units are never samples: bursty repository activity is ordered by evidence opportunity, while
 * timestamps survive only as provenance. Recalculation after every event is legitimate because this is a
 * Bayesian posterior, not a repeatedly peeked frequentist test. The package javadoc carries the grounding for
 * both halves of that sentence.
 *
 * <p>Aggregating several of these into a group is a different estimator and lives in
 * {@link GroupTrendAggregator}.
 */
final class PracticeTrendCalculator {

    private PracticeTrendCalculator() {}

    static PracticeTrend calculatePractice(
            String practiceSlug, List<PracticeResult> practiceResults, Instant cutoff, TrendProperties properties) {
        OpportunityBundler.Bundles bundles =
                OpportunityBundler.bundle(practiceResults, cutoff, properties.getBundleSize());
        int missing = bundles.opportunitiesUntilComparable(properties.getBundleSize(), properties.getMinBundleSize());
        // Counted before the cap: the cap shortens a chart, not the work a reader is told was reviewed.
        Set<ReviewedWorkKey> reviewedWork = bundles.trail().stream()
                .filter(EvidenceOpportunity::decided)
                .map(EvidenceOpportunity::key)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<EvidenceOpportunity> trail = OpportunityBundler.cappedTrail(bundles.trail(), properties.getBundleSize());
        TrendSupport support = TrendSupportFactory.forPractice(
                properties, bundles.current().size(), bundles.previous().size(), missing, reviewedWork, trail);
        if (missing > 0) {
            return new PracticeTrend(
                    practiceSlug,
                    TrendScope.PRACTICE,
                    TrendDirection.INSUFFICIENT_EVIDENCE,
                    support,
                    null,
                    null,
                    trail,
                    reviewedWork,
                    null);
        }

        BetaPosterior.Difference difference =
                posterior(bundles.current()).differenceFrom(posterior(bundles.previous()));
        TrendDirection direction = TrendDirectionRule.classify(
                difference, properties.getRopeHalfWidth(), properties.getCredibilityThreshold());
        return new PracticeTrend(
                practiceSlug,
                TrendScope.PRACTICE,
                direction,
                support,
                outcomes(bundles.current()),
                outcomes(bundles.previous()),
                trail,
                reviewedWork,
                difference);
    }

    private static BetaPosterior posterior(List<EvidenceOpportunity> opportunities) {
        double sum = opportunities.stream()
                .mapToDouble(opportunity -> opportunity.outcomes().metShare())
                .sum();
        return BetaPosterior.from(opportunities.size(), sum);
    }

    private static OutcomeVector outcomes(List<EvidenceOpportunity> opportunities) {
        return opportunities.stream()
                .map(EvidenceOpportunity::outcomes)
                .reduce(OutcomeVector.EMPTY, OutcomeVector::plus);
    }
}
