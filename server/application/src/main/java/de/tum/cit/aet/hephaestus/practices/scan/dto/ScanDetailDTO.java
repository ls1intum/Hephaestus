package de.tum.cit.aet.hephaestus.practices.scan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.core.auth.dto.AccountRefDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ItemRefDTO;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewStopReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ScanDetailDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ItemRefDTO repository,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String ref,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String itemVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewOrigin origin,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewStatus status,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        AccountRefDTO requestedBy,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Instant startedAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Instant completedAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int practiceCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ScanCountsDTO counts,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        UUID baselineScanId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        ReviewStopReason stopReason,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ScanPracticeRefDTO> practices,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String budgetUsd,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String chargedCostUsd,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ScanProgressDTO progress) {
    public ScanDetailDTO {
        practices = List.copyOf(practices);
    }
}
