package de.tum.cit.aet.hephaestus.practices.result.trend.dto;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.result.trend.TrendSupport;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record TrendSupportDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int currentOpportunities,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int previousOpportunities,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Distinct pieces of reviewed work the trend rests on, across both bundles")
        int opportunities,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int opportunitiesUntilComparable,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Distinct pieces of reviewed work the standing is read from: the newest decided pieces,"
                        + " at most bundleSize per practice, counted once across a group's practices")
        int standingWork,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Distinct pieces of work in the look-back whose newest live review reached a verdict on"
                        + " this practice, or on any practice of this group; never below standingWork")
        int reviewedWork,

        @Nullable
        @Schema(
                description = "The kind every piece counted in reviewedWork shares; absent when they are of several"
                        + " kinds or there are none")
        ArtifactKind reviewedWorkKind,

        @Nullable Integer comparablePractices,
        @Nullable Integer eligiblePractices,
        @Nullable Instant firstOpportunityAt,
        @Nullable Instant lastOpportunityAt,
        @Nullable Integer calendarSpanDays,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int bundleSize,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double ropeHalfWidth,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double credibilityThreshold) {
    public static TrendSupportDTO from(TrendSupport support) {
        return new TrendSupportDTO(
                support.currentOpportunities(),
                support.previousOpportunities(),
                support.opportunities(),
                support.opportunitiesUntilComparable(),
                support.standingWork(),
                support.reviewedWork(),
                support.reviewedWorkKind(),
                support.comparablePractices(),
                support.eligiblePractices(),
                support.firstOpportunityAt(),
                support.lastOpportunityAt(),
                support.calendarSpanDays(),
                support.bundleSize(),
                support.ropeHalfWidth(),
                support.credibilityThreshold());
    }
}
