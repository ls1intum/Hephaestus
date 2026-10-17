package de.tum.cit.aet.hephaestus.practices.feedback.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.feedback.response.FeedbackResponse;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackResolution;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackUsefulness;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record FeedbackResponseDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        FeedbackResolution resolution,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        FeedbackUsefulness usefulness,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String comment,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Instant createdAt) {

    public static FeedbackResponseDTO from(FeedbackResponse response) {
        return new FeedbackResponseDTO(
                response.getResolution(), response.getUsefulness(), response.getComment(), response.getCreatedAt());
    }

    public static FeedbackResponseDTO empty() {
        return new FeedbackResponseDTO(null, null, null, null);
    }
}
