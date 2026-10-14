package de.tum.cit.aet.hephaestus.practices.result.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record DeveloperPracticeSummaryDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String practiceSlug,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String practiceName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalResults,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int met,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int notMet,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int doesNotApply,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int couldNotDetermine,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Instant lastRecordedAt) {}
