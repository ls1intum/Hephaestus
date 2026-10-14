package de.tum.cit.aet.hephaestus.practices.feedback.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSend;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackSendAction;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackSendState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record FeedbackSendDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID feedbackId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FeedbackSendAction action,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FeedbackSendState state,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int attemptCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant nextAttemptAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String lastError,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt) {

    public static FeedbackSendDTO from(FeedbackSend send) {
        return new FeedbackSendDTO(
                send.getFeedbackId(),
                send.getAction(),
                send.getState(),
                send.getAttempts(),
                send.getNextAttemptAt(),
                send.getLastError(),
                send.getCreatedAt(),
                send.getUpdatedAt());
    }
}
