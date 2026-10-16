package de.tum.cit.aet.hephaestus.practices.scan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReasonCountDTO;
import de.tum.cit.aet.hephaestus.practices.spi.Comparison;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ScanReportDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ScanSummaryDTO scan,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean complete,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean listingComplete,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        UUID baselineScanId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Comparison comparison,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        ScanCountsDTO recordedCounts,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ScanCountsDTO counts,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ProblemChangesDTO problemChanges,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ScanPracticeReportDTO> practices,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ReasonCountDTO> withheldReasons,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ReasonCountDTO> notReviewedReasons,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) RemovalGuardDTO removalGuard,
        @Nullable List<ScanExcludedFileDTO> exclusions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ScanAvailabilityDTO availability) {
    public ScanReportDTO {
        practices = List.copyOf(practices);
        exclusions = exclusions == null ? null : List.copyOf(exclusions);
        withheldReasons = List.copyOf(withheldReasons);
        notReviewedReasons = List.copyOf(notReviewedReasons);
    }
}
