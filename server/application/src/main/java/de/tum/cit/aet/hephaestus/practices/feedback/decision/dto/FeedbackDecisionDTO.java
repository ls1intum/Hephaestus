package de.tum.cit.aet.hephaestus.practices.feedback.decision.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.core.auth.dto.AccountRefDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.decision.FeedbackDecision;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDecisionAction;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDecisionReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record FeedbackDecisionDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID feedbackId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FeedbackDecisionAction action,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        AccountRefDTO actor,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        FeedbackDecisionReason reasonCode,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String note,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String contentDigest,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant decidedAt) {

    public static FeedbackDecisionDTO from(FeedbackDecision decision, @Nullable AccountRefDTO actor) {
        FeedbackDecisionReason reason =
                decision.getReason() == null ? null : FeedbackDecisionReason.valueOf(decision.getReason());
        @Nullable String note = decision.getNote();
        return new FeedbackDecisionDTO(
                decision.getId(),
                decision.getFeedbackId(),
                decision.getAction(),
                actor,
                reason,
                note,
                decision.getContentDigest(),
                decision.getDecidedAt());
    }
}
