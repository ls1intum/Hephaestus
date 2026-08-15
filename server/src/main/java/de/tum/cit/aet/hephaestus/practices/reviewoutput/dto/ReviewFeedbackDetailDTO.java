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
    @Schema(description = "Work item the feedback targets; null when it is unanchored") ReviewArtifactDTO artifact,
    @Schema(description = "Who the feedback is addressed to; null when the identity is no longer resolvable")
    ReviewSubjectDTO recipient,
    @Schema(description = "Whose work the feedback addresses; may equal the recipient") ReviewSubjectDTO subject,
    @NonNull FeedbackChannel channel,
    @NonNull FeedbackDeliveryState deliveryState,
    @Schema(description = "Why the feedback was withheld; null unless the state is SUPPRESSED")
    FeedbackSuppressionReason suppressionReason,
    @Schema(description = "The feedback this one replaced; null on a first delivery") UUID replacesId,
    @Schema(description = "Cross-run continuity key tying successive deliveries together") String threadKey,
    @NonNull Instant createdAt,
    @Schema(description = "When the feedback was placed; null if it was not delivered") Instant deliveredAt,
    @Schema(
        description = "Stored composed body; null when none was produced, and always null on the REFLECTION " +
            "channel — the developer's reflection surface is not readable by an operator"
    )
    String body,
    @NonNull @Schema(description = "Source observations in render order") List<ReviewBoundObservationDTO> observations,
    @NonNull @Schema(description = "Recorded placements; empty when none") List<ReviewPlacementDTO> placements
) {
    /**
     * @param bodyVisible whether this caller may read the composed text. A withheld body is rendered as
     *     absent rather than as an empty string, so "we are not showing you this" reads the same as
     *     "there was nothing to show" on the wire and neither invites a client to display a blank card.
     */
    public static ReviewFeedbackDetailDTO from(
        Feedback feedback,
        ReviewArtifactDTO artifact,
        ReviewSubjectDTO recipient,
        ReviewSubjectDTO subject,
        List<ReviewBoundObservationDTO> observations,
        List<ReviewPlacementDTO> placements,
        boolean bodyVisible
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
            bodyVisible ? feedback.getBody() : null,
            observations,
            placements
        );
    }
}
