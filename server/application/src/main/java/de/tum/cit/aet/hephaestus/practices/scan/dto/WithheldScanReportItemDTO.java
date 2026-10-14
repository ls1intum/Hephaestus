package de.tum.cit.aet.hephaestus.practices.scan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.review.dto.ItemRefDTO;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record WithheldScanReportItemDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ItemRefDTO item,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String practiceSlug,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String practiceName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String itemVersion,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "WITHHELD")
        String status,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String reason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String reasonDescription)
        implements ScanReportItemDTO {
    public WithheldScanReportItemDTO {
        if (!"WITHHELD".equals(status)) {
            throw new IllegalArgumentException("Invalid status");
        }
    }
}
