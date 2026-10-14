package de.tum.cit.aet.hephaestus.practices.feedback.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewBoundResultDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record FeedbackBasisDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID resultId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        UUID problemId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int ordinal,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewBoundResultDTO result) {}
