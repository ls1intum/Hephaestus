package de.tum.cit.aet.hephaestus.practices.review.schedule.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record CreateReviewBatchRequestDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String itemKind,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Instant from,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Instant to,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String budgetUsd) {}
