package de.tum.cit.aet.hephaestus.practices.scan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.review.dto.ItemRefDTO;
import de.tum.cit.aet.hephaestus.practices.spi.Currentness;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.Severity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record JudgedScanReportItemDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ItemRefDTO item,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String practiceSlug,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String practiceName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String itemVersion,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "JUDGED")
        String status,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID resultId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID sourceReviewId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ResultOutcome outcome,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Severity severity,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String summary,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int problemCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Currentness currentness,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant recordedAt)
        implements ScanReportItemDTO {
    public JudgedScanReportItemDTO {
        if (!"JUDGED".equals(status)) {
            throw new IllegalArgumentException("Invalid status");
        }
    }
}
