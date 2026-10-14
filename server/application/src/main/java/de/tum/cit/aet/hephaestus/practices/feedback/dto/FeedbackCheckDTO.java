package de.tum.cit.aet.hephaestus.practices.feedback.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCheck;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCheckStage;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackStopReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record FeedbackCheckDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FeedbackCheckStage stage,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean allowed,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        FeedbackStopReason reason,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant checkedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FeedbackCheckFactsDTO facts) {

    public static FeedbackCheckDTO from(FeedbackCheck check) {
        return new FeedbackCheckDTO(
                check.stage(),
                check.allowed(),
                check.reason(),
                check.checkedAt(),
                FeedbackCheckFactsDTO.from(check.facts()));
    }
}
