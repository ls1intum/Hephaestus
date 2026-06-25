package de.tum.cit.aet.hephaestus.practices.finding.reaction.dto;

import de.tum.cit.aet.hephaestus.practices.finding.reaction.FindingReactionAction;
import de.tum.cit.aet.hephaestus.practices.finding.reaction.Reaction;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO representing a developer's reaction on a practice finding.
 */
@Schema(description = "Developer reaction to a delivered unit of feedback")
public record FindingReactionDTO(
    @Schema(description = "Unique reaction ID", requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(description = "ID of the feedback unit this reaction is about", requiredMode = Schema.RequiredMode.REQUIRED)
    UUID feedbackId,
    @Schema(description = "The reaction action taken", requiredMode = Schema.RequiredMode.REQUIRED)
    FindingReactionAction action,
    @Schema(description = "Optional explanation for the reaction") String explanation,
    @Schema(description = "When the reaction was submitted", requiredMode = Schema.RequiredMode.REQUIRED)
    Instant createdAt
) {
    public static FindingReactionDTO from(Reaction reaction) {
        return new FindingReactionDTO(
            reaction.getId(),
            reaction.getFeedbackId(),
            reaction.getAction(),
            reaction.getExplanation(),
            reaction.getCreatedAt()
        );
    }
}
