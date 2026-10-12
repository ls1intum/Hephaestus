package de.tum.cit.aet.hephaestus.practices.result.trend;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.result.ReviewedWorkKey;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Builds the provenance a trend direction is published with.
 *
 * <p>Two entry points rather than one with optional arguments. {@link TrendSupport#comparablePractices()} and
 * {@link TrendSupport#eligiblePractices()} are absent at practice scope and present at group scope, and that
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
            Set<ReviewedWorkKey> reviewedWork,
            List<EvidenceOpportunity> trail) {
        // One practice's bundles are disjoint, so no piece of reviewed work is in both.
        return build(
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

    /**
     * Support for an aggregate — additionally says how many of the group's practices could be compared.
     *
     * <p>{@code opportunities} is passed in rather than summed, because a group's two bundles can name the same
     * piece of reviewed work through different practices.
     */
    static TrendSupport forGroup(
            TrendProperties properties,
            int currentOpportunities,
            int previousOpportunities,
            int opportunities,
            int opportunitiesUntilComparable,
            int standingWork,
            Set<ReviewedWorkKey> reviewedWork,
            int comparablePractices,
            int eligiblePractices,
            List<EvidenceOpportunity> trail) {
        return build(
                properties,
                currentOpportunities,
                previousOpportunities,
                opportunities,
                opportunitiesUntilComparable,
                standingWork,
                reviewedWork,
                comparablePractices,
                eligiblePractices,
                trail);
    }

    private static TrendSupport build(
            TrendProperties properties,
            int currentOpportunities,
            int previousOpportunities,
            int opportunities,
            int opportunitiesUntilComparable,
            int standingWork,
            Set<ReviewedWorkKey> reviewedWork,
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
        Integer span = first == null || last == null
                ? null
                : (int) ChronoUnit.DAYS.between(
                                first.atZone(ZoneOffset.UTC).toLocalDate(),
                                last.atZone(ZoneOffset.UTC).toLocalDate())
                        + 1;
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

    /** The kind every piece of work shares, so a reader can be told "pull requests"; none when mixed or empty. */
    private static @Nullable ArtifactKind sharedKind(Set<ReviewedWorkKey> work) {
        List<ArtifactKind> kinds =
                work.stream().map(ReviewedWorkKey::kind).distinct().toList();
        return kinds.size() == 1 ? kinds.getFirst() : null;
    }
}
