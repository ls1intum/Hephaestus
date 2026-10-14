package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.review.dto.ItemRefDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewDeveloperDTO;
import de.tum.cit.aet.hephaestus.practices.spi.Currentness;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.Severity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReviewBoundResultDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID reviewId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String practiceSlug,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String practiceName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ResultOutcome outcome,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Severity severity,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String summary,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ItemRefDTO item,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        ReviewDeveloperDTO developer,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant recordedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Currentness currentness) {}
