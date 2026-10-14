package de.tum.cit.aet.hephaestus.practices.result.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.review.dto.ItemRefDTO;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.Severity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PracticeStandingResultDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID resultId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String deliveredFeedback,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Severity severity,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ResultOutcome outcome,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ItemRefDTO item,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewOrigin origin) {}
