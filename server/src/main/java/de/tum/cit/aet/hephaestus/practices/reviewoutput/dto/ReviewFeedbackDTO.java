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

public record ReviewFeedbackDTO(
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
    @NonNull Instant createdAt,
    @Schema(description = "When the feedback was placed; null if it was not delivered") Instant deliveredAt,
    @Schema(description = "Leading characters of the composed body; null when the feedback carries no body")
    String bodyPreview,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean bodyTruncated,
    @NonNull @Schema(description = "Number of observations used to compose the feedback") Long observationCount
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
