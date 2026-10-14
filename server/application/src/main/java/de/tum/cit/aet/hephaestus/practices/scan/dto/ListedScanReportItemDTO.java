package de.tum.cit.aet.hephaestus.practices.scan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.result.dto.ResultListDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ItemRefDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ListedScanReportItemDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ItemRefDTO item,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String practiceSlug,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String practiceName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String itemVersion,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "LISTED")
        String status,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String reason,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String reasonDescription,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        ResultListDTO lastResult)
        implements ScanReportItemDTO {
    public ListedScanReportItemDTO {
        if (!"LISTED".equals(status)) {
            throw new IllegalArgumentException("Invalid status");
        }
    }
}
