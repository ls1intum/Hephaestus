package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NonNull;

@Schema(description = "Full feedback detail including the stored composed body")
public record ReviewFeedbackDetailDTO(
    @NonNull UUID id,
    @NonNull UUID agentJobId,
    @Schema(description = "Work item the message targets; null for an unanchored message") ReviewArtifactDTO artifact,
    @Schema(description = "Who the message is addressed to; null when the identity is no longer resolvable")
    ReviewSubjectDTO recipient,
    @Schema(description = "Whose work the message addresses; may equal the recipient") ReviewSubjectDTO subject,
    @NonNull FeedbackChannel channel,
    @NonNull FeedbackDeliveryState deliveryState,
    @Schema(description = "Why the message was withheld; null unless the state is SUPPRESSED")
    FeedbackSuppressionReason suppressionReason,
    @Schema(description = "The message this one replaced; null on a first delivery") UUID replacesId,
    @Schema(description = "Cross-run continuity key tying successive deliveries together") String threadKey,
    @NonNull Instant createdAt,
    @Schema(description = "When the message was placed; null if it was not delivered") Instant deliveredAt,
    @Schema(description = "Stored composed body; null when none was produced") String body,
    @NonNull @Schema(description = "Source findings in render order") List<ReviewBoundFindingDTO> findings,
    @NonNull @Schema(description = "Recorded placements; empty when none") List<ReviewPlacementDTO> placements
) {
    public static ReviewFeedbackDetailDTO from(
        Feedback feedback,
        ReviewArtifactDTO artifact,
        ReviewSubjectDTO recipient,
        ReviewSubjectDTO subject,
        List<ReviewBoundFindingDTO> findings,
        List<ReviewPlacementDTO> placements
    ) {
        return new ReviewFeedbackDetailDTO(
            feedback.getId(),
            feedback.getAgentJobId(),
            artifact,
            recipient,
            subject,
            feedback.getChannel(),
            feedback.getDeliveryState(),
            feedback.getSuppressionReason(),
            feedback.getReplacesId(),
            feedback.getThreadKey(),
            feedback.getCreatedAt(),
            feedback.getDeliveredAt(),
            feedback.getBody(),
            findings,
            placements
        );
    }
}
