package de.tum.cit.aet.hephaestus.practices.observation.trend;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Builds the provenance a trend direction is published with.
 *
 * <p>Two entry points rather than one with optional arguments. {@link TrendSupport#comparablePractices()} and
 * {@link TrendSupport#eligiblePractices()} are absent at practice scope and present at area scope, and that
 * absence reaches the client, so it is part of the contract rather than a gap. Encoding it in the method name
 * means neither caller passes a null to say which case it is in, and adding a scope later forces a new
 * factory instead of a fourth nullable argument.
 */
final class TrendSupportFactory {

    private TrendSupportFactory() {}

    /** Support for one practice's own comparison — no practice counts, because it is the practice. */
    static TrendSupport forPractice(
        TrendProperties properties,
        int currentOpportunities,
        int previousOpportunities,
        int opportunitiesUntilComparable,
        List<EvidenceOpportunity> trail
    ) {
        return build(
            properties,
            currentOpportunities,
            previousOpportunities,
            opportunitiesUntilComparable,
            null,
            null,
            trail
        );
    }

    /** Support for an aggregate — additionally says how many of the area's practices could be compared. */
    static TrendSupport forArea(
        TrendProperties properties,
        int currentOpportunities,
        int previousOpportunities,
        int opportunitiesUntilComparable,
        int comparablePractices,
        int eligiblePractices,
        List<EvidenceOpportunity> trail
    ) {
        return build(
            properties,
            currentOpportunities,
            previousOpportunities,
            opportunitiesUntilComparable,
            comparablePractices,
            eligiblePractices,
            trail
        );
    }

    private static TrendSupport build(
        TrendProperties properties,
        int currentOpportunities,
        int previousOpportunities,
        int opportunitiesUntilComparable,
        @Nullable Integer comparablePractices,
        @Nullable Integer eligiblePractices,
        List<EvidenceOpportunity> trail
    ) {
        // Provenance for what was COMPARED, so only opportunities that produced a verdict date it. The trail
        // also carries the ones where the practice looked and found nothing to judge — they belong in it, and
        // in the chart drawn from it, because "we saw this work item" is a fact worth showing. But letting one
        // date the span would answer a question nobody asked: a stretch of work offering no opportunity would
        // stretch "these N comparisons span X days" without adding a comparison.
        List<Instant> dated = trail
            .stream()
            .filter(EvidenceOpportunity::applicable)
            .map(EvidenceOpportunity::occurredAt)
            .toList();
        Instant first = dated.stream().min(Instant::compareTo).orElse(null);
        Instant last = dated.stream().max(Instant::compareTo).orElse(null);
        Integer span =
            first == null || last == null
                ? null
                : (int) ChronoUnit.DAYS.between(
                      first.atZone(ZoneOffset.UTC).toLocalDate(),
                      last.atZone(ZoneOffset.UTC).toLocalDate()
                  ) +
                  1;
        return new TrendSupport(
            currentOpportunities,
            previousOpportunities,
            opportunitiesUntilComparable,
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
}
