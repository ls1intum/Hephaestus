package de.tum.cit.aet.hephaestus.practices.result.trend;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Reproducible evidence support and provenance for a trend direction — how much was compared, over which
 * calendar span, and under which parameters, so a reader can see what a direction rests on.
 *
 * <p>Carries counts rather than a graded level. A level existed here and was removed: with both bundles
 * necessarily full whenever a comparison is possible at all, it had exactly one reachable value, and across
 * every combination reachable at four opportunities per bundle it never once changed a verdict. The counts
 * below say the same thing without pretending to grade it.
 *
 * <p>{@code opportunities} is not {@code currentOpportunities + previousOpportunities}. At group scope one
 * piece of reviewed work can be current evidence for one practice and previous evidence for another, and a
 * reader counts it once; at practice scope the two bundles are disjoint and the sum is the same number.
 *
 * <p>{@code standingWork} and {@code reviewedWork} answer what a standing is read from, not what a direction
 * compares. {@code standingWork} is the distinct work in the current bundles: at practice scope it is
 * {@code currentOpportunities}; at group scope it covers every practice that joined the group's trend, compared
 * or not, because every one of them votes in the group's standing. {@code reviewedWork} is every distinct piece
 * of work in the look-back whose newest live review reached a verdict on the practice, or on any of the group's
 * practices, so it is never below {@code standingWork}. {@code reviewedWorkKind} is the kind all of that work
 * shares, and absent when it is mixed or there is none.
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
        double credibilityThreshold) {}
