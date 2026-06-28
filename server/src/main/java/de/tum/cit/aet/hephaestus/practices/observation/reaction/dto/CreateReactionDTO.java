package de.tum.cit.aet.hephaestus.practices.observation.reaction.dto;

import de.tum.cit.aet.hephaestus.practices.observation.reaction.ReactionAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for submitting a reaction to a delivered feedback unit.
 *
 * @param action      the reaction action (required)
 * @param explanation free-text explanation (optional for ADDRESSED/NOT_APPLICABLE, required for DISPUTED)
 */
@Schema(description = "Submit a reaction to a delivered feedback unit")
public record CreateReactionDTO(
    @NotNull @Schema(description = "The reaction action to record") ReactionAction action,
    @Size(max = 2000)
    @Schema(description = "Explanation for the reaction. Required when action is DISPUTED.", maxLength = 2000)
    String explanation
) {}
