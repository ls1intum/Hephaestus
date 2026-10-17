package de.tum.cit.aet.hephaestus.practices.scan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewMethodDTO;
import de.tum.cit.aet.hephaestus.practices.spi.Comparison;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewPracticeStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ScanPracticeReportDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", pattern = "^[1-9][0-9]*$")
        String practiceId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", pattern = "^[1-9][0-9]*$")
        String practiceVersionId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int number,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String practiceSlug,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String practiceName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String reviewDigest,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ScanCountsDTO counts,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean listingComplete,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewMethodDTO method,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Comparison comparison,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewPracticeStatus status,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String reason) {}
