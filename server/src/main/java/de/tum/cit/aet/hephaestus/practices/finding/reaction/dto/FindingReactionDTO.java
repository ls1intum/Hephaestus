package de.tum.cit.aet.hephaestus.practices.finding.reaction.dto;

import de.tum.cit.aet.hephaestus.practices.finding.reaction.FindingReaction;
import de.tum.cit.aet.hephaestus.practices.finding.reaction.FindingReactionAction;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO representing a contributor's feedback on a practice finding.
 */
@Schema(description = "Contributor feedback on an AI-generated practice finding")
public record FindingReactionDTO(
    @Schema(description = "Unique feedback ID", requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(description = "ID of the finding this feedback is about", requiredMode = Schema.RequiredMode.REQUIRED)
    UUID findingId,
    @Schema(description = "The feedback action taken", requiredMode = Schema.RequiredMode.REQUIRED)
    FindingReactionAction action,
    @Schema(description = "Optional explanation for the feedback") String explanation,
    @Schema(description = "When the feedback was submitted", requiredMode = Schema.RequiredMode.REQUIRED)
    Instant createdAt
) {
    public static FindingReactionDTO from(FindingReaction feedback) {
        return new FindingReactionDTO(
            feedback.getId(),
            feedback.getFindingId(),
            feedback.getAction(),
            feedback.getExplanation(),
            feedback.getCreatedAt()
        );
    }
}
