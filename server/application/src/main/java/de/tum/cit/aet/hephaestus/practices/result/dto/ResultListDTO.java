package de.tum.cit.aet.hephaestus.practices.result.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.review.dto.ItemRefDTO;
import de.tum.cit.aet.hephaestus.practices.spi.Currentness;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.Severity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ResultListDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID reviewId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ItemRefDTO item,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String practiceSlug,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String practiceName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ResultOutcome outcome,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Severity severity,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String summary,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewOrigin origin,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant recordedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Currentness currentness,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Instant markedIncorrectAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Instant acceptedAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ProblemSummaryDTO> problems) {
    public ResultListDTO {
        problems = List.copyOf(problems);
    }
}
