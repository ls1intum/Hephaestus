package de.tum.cit.aet.hephaestus.practices.observation.trend;

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
 */
public record TrendSupport(
    int currentOpportunities,
    int previousOpportunities,
    int opportunitiesUntilComparable,
    @Nullable Integer comparablePractices,
    @Nullable Integer eligiblePractices,
    @Nullable Instant firstOpportunityAt,
    @Nullable Instant lastOpportunityAt,
    @Nullable Integer calendarSpanDays,
    int bundleSize,
    double ropeHalfWidth,
    double credibilityThreshold
) {}
