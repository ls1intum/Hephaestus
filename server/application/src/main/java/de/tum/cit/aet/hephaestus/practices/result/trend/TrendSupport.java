package de.tum.cit.aet.hephaestus.practices.result.trend;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * @param standingWork reviewed work in the current standing
 * @param reviewedWork distinct reviewed work across both comparison bundles
 */
public record TrendSupport(
        int currentOpportunities,
        int previousOpportunities,
        int opportunities,
        int opportunitiesUntilComparable,
        int standingWork,
        int reviewedWork,
        @Nullable ItemKind reviewedWorkKind,
        @Nullable Integer comparablePractices,
        @Nullable Integer eligiblePractices,
        @Nullable Instant firstOpportunityAt,
        @Nullable Instant lastOpportunityAt,
        @Nullable Integer calendarSpanDays,
        int bundleSize,
        double ropeHalfWidth,
        double credibilityThreshold) {
    static TrendSupport forPractice(
            TrendProperties properties,
            int currentOpportunities,
            int previousOpportunities,
            int opportunitiesUntilComparable,
            Map<Long, ItemKind> reviewedWork,
            List<EvidenceOpportunity> trail) {
        // One practice's bundles are disjoint, so no piece of reviewed work is in both.
        return forGroup(
                properties,
                currentOpportunities,
                previousOpportunities,
                currentOpportunities + previousOpportunities,
                opportunitiesUntilComparable,
                currentOpportunities,
                reviewedWork,
                null,
                null,
                trail);
    }

    static TrendSupport forGroup(
            TrendProperties properties,
            int currentOpportunities,
            int previousOpportunities,
            int opportunities,
            int opportunitiesUntilComparable,
            int standingWork,
            Map<Long, ItemKind> reviewedWork,
            @Nullable Integer comparablePractices,
            @Nullable Integer eligiblePractices,
            List<EvidenceOpportunity> trail) {
        // The comparison span excludes work that offered no opportunity to apply the practice.
        List<Instant> dated = trail.stream()
                .filter(EvidenceOpportunity::decided)
                .map(EvidenceOpportunity::occurredAt)
                .toList();
        Instant first = dated.stream().min(Instant::compareTo).orElse(null);
        Instant last = dated.stream().max(Instant::compareTo).orElse(null);
        Integer span = first == null ? null : inclusiveUtcDaySpan(first, Objects.requireNonNull(last));
        return new TrendSupport(
                currentOpportunities,
                previousOpportunities,
                opportunities,
                opportunitiesUntilComparable,
                standingWork,
                reviewedWork.size(),
                sharedKind(reviewedWork),
                comparablePractices,
                eligiblePractices,
                first,
                last,
                span,
                properties.getBundleSize(),
                properties.getRopeHalfWidth(),
                properties.getCredibilityThreshold());
    }

    private static @Nullable ItemKind sharedKind(Map<Long, ItemKind> work) {
        List<ItemKind> kinds = work.values().stream().distinct().toList();
        return kinds.size() == 1 ? kinds.getFirst() : null;
    }

    public static int inclusiveUtcDaySpan(Instant first, Instant last) {
        return (int) (ChronoUnit.DAYS.between(
                        first.atZone(ZoneOffset.UTC).toLocalDate(),
                        last.atZone(ZoneOffset.UTC).toLocalDate())
                + 1);
    }
}
