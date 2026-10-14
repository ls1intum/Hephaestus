package de.tum.cit.aet.hephaestus.practices.result.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.result.trend.TrendSupport;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record TrendSupportDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int currentOpportunities,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int previousOpportunities,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int opportunities,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int opportunitiesUntilComparable,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int standingWork,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int reviewedWork,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String reviewedWorkKind,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Integer comparablePractices,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Integer eligiblePractices,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Instant firstOpportunityAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Instant lastOpportunityAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Integer calendarSpanDays,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int bundleSize,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double ropeHalfWidth,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double credibilityThreshold) {
    public static TrendSupportDTO from(TrendSupport s) {
        return new TrendSupportDTO(
                s.currentOpportunities(),
                s.previousOpportunities(),
                s.opportunities(),
                s.opportunitiesUntilComparable(),
                s.standingWork(),
                s.reviewedWork(),
                s.reviewedWorkKind() == null ? null : s.reviewedWorkKind().value(),
                s.comparablePractices(),
                s.eligiblePractices(),
                s.firstOpportunityAt(),
                s.lastOpportunityAt(),
                s.calendarSpanDays(),
                s.bundleSize(),
                s.ropeHalfWidth(),
                s.credibilityThreshold());
    }
}
