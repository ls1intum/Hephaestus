package de.tum.cit.aet.hephaestus.practices.observation.trend;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Reproducible evidence support and provenance for a trend direction. */
public record TrendSupport(
    SupportLevel level,
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
