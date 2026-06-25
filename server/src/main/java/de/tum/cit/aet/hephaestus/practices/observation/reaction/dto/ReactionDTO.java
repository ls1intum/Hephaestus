package de.tum.cit.aet.hephaestus.practices.observation.reaction.dto;

import de.tum.cit.aet.hephaestus.practices.observation.reaction.ReactionAction;
import de.tum.cit.aet.hephaestus.practices.observation.reaction.Reaction;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO representing a developer's reaction on a practice finding.
 */
@Schema(description = "Developer reaction to a delivered unit of feedback")
public record ReactionDTO(
    @Schema(description = "Unique reaction ID", requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(description = "ID of the feedback unit this reaction is about", requiredMode = Schema.RequiredMode.REQUIRED)
    UUID feedbackId,
    @Schema(description = "The reaction action taken", requiredMode = Schema.RequiredMode.REQUIRED)
    ReactionAction action,
    @Schema(description = "Optional explanation for the reaction") String explanation,
    @Schema(description = "When the reaction was submitted", requiredMode = Schema.RequiredMode.REQUIRED)
    Instant createdAt
) {
    public static ReactionDTO from(Reaction reaction) {
        return new ReactionDTO(
            reaction.getId(),
            reaction.getFeedbackId(),
            reaction.getAction(),
            reaction.getExplanation(),
            reaction.getCreatedAt()
        );
    }
}
