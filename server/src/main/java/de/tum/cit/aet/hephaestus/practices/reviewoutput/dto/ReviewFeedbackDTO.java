package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository.OperatorFeedbackRow;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.NonNull;

@Schema(description = "A composed feedback unit with its delivery disposition")
public record ReviewFeedbackDTO(
    @NonNull UUID id,
    @NonNull UUID agentJobId,
    @Schema(description = "Work item the unit targets; null for an unanchored unit") ReviewArtifactDTO artifact,
    @Schema(description = "Who the unit is addressed to; null when the identity is no longer resolvable")
    ReviewSubjectDTO recipient,
    @Schema(description = "Whose work the message addresses; may equal the recipient") ReviewSubjectDTO subject,
    @NonNull FeedbackChannel channel,
    @NonNull FeedbackDeliveryState deliveryState,
    @Schema(description = "Why the unit was withheld; null unless the state is SUPPRESSED")
    FeedbackSuppressionReason suppressionReason,
    @Schema(description = "The unit this one replaced on its continuity line; null on a first delivery")
    UUID replacesId,
    @NonNull Instant createdAt,
    @Schema(description = "When the feedback was placed; null if it never reached a delivery surface")
    Instant deliveredAt,
    @Schema(description = "Leading characters of the composed body; null when the unit carries no body")
    String bodyPreview,
    @Schema(
        description = "Whether the stored body is longer than the preview",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    boolean bodyTruncated,
    @NonNull @Schema(description = "Number of findings used to compose the unit") Long findingCount
) {
    public static ReviewFeedbackDTO from(
        OperatorFeedbackRow row,
        ReviewArtifactDTO artifact,
        Map<Long, ReviewSubjectDTO> subjects
    ) {
        return new ReviewFeedbackDTO(
            row.getId(),
            row.getAgentJobId(),
            artifact,
            subjects.get(row.getRecipientUserId()),
            subjects.get(row.getAboutUserId()),
            row.getChannel(),
            row.getDeliveryState(),
            row.getSuppressionReason(),
            row.getReplacesId(),
            row.getCreatedAt(),
            row.getDeliveredAt(),
            row.getBodyPreview(),
            Boolean.TRUE.equals(row.getBodyTruncated()),
            row.getObservationCount()
        );
    }
}
