package de.tum.cit.aet.hephaestus.practices.review.schedule.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.core.auth.dto.AccountRefDTO;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewBatchOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewBatchPauseReason;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewBatchStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReviewBatchDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String itemKind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewBatchOrigin origin,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        UUID scheduleId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Instant from,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Instant to,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewBatchStatus status,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        ReviewBatchPauseReason pauseReason,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String budgetUsd,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int estimatedItems,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String estimatedCostUsd,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int submittedCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int skippedCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int failedCount,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        AccountRefDTO requestedBy,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        AccountRefDTO confirmedBy,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Instant startedAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Instant finishedAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String etag,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UUID> reviewIds,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", pattern = "^[1-9][0-9]*$")
        List<String> targetItemIds) {
    public ReviewBatchDTO {
        reviewIds = List.copyOf(reviewIds);
        targetItemIds = List.copyOf(targetItemIds);
    }
}
