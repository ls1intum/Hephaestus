package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import de.tum.cit.aet.hephaestus.practices.feedback.EvidenceRole;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository.BoundFeedbackUnit;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "Feedback composed from an observation")
public record ReviewBoundFeedbackDTO(
        @NonNull UUID feedbackId,

        @NonNull @Schema(description = "Whether the observation led the feedback or reinforced it")
        EvidenceRole role,

        @NonNull UUID agentJobId,
        @NonNull FeedbackChannel channel,
        @NonNull FeedbackDeliveryState deliveryState,

        @Schema(description = "Why the message was withheld; null unless the state is SUPPRESSED") @Nullable
        FeedbackSuppressionReason suppressionReason,

        @NonNull Instant createdAt,

        @Schema(description = "When the message was placed; null if it was not delivered") @Nullable
        Instant deliveredAt) {
    public static ReviewBoundFeedbackDTO from(BoundFeedbackUnit row) {
        return new ReviewBoundFeedbackDTO(
                row.getFeedbackId(),
                row.getRole(),
                row.getAgentJobId(),
                row.getChannel(),
                row.getDeliveryState(),
                row.getSuppressionReason(),
                row.getCreatedAt(),
                row.getDeliveredAt());
    }
}
