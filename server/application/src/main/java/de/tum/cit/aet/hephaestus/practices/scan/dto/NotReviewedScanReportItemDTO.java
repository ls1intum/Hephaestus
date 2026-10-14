package de.tum.cit.aet.hephaestus.practices.scan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.result.dto.ResultListDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ItemRefDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record NotReviewedScanReportItemDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ItemRefDTO item,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String practiceSlug,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String practiceName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String itemVersion,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String reason,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        ResultListDTO lastResult,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "NOT_REVIEWED")
        String status)
        implements ScanReportItemDTO {
    public NotReviewedScanReportItemDTO {
        if (!"NOT_REVIEWED".equals(status)) {
            throw new IllegalArgumentException("Invalid status");
        }
    }
}
