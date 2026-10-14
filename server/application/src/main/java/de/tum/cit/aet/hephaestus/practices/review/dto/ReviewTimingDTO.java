package de.tum.cit.aet.hephaestus.practices.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReviewTimingDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant from,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant to,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewTimingStageDTO asked,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewTimingStageDTO queue,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewTimingStageDTO preparation,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewTimingStageDTO model,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewTimingStageDTO posted,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewTimingStageDTO total,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        ReviewTimeSharesDTO totalShares,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewTimingStageDTO finished,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        ReviewTimeSharesDTO finishedShares,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewTimingStageDTO read,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long waitingLong,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long finishedAfterMerge,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long finishedAfterMergeStartedByMerge,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long feedbackWithheldAsMerged) {}
