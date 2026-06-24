package de.tum.cit.aet.hephaestus.practices.finding.reaction.dto;

import de.tum.cit.aet.hephaestus.practices.finding.reaction.FindingReactionAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for submitting reaction on a practice finding.
 *
 * @param action      the reaction action (required)
 * @param explanation free-text explanation (optional for ADDRESSED/NOT_APPLICABLE, required for DISPUTED)
 */
@Schema(description = "Submit a reaction to an AI-generated practice finding")
public record CreateFindingReactionDTO(
    @NotNull @Schema(description = "The reaction action to record") FindingReactionAction action,
    @Size(max = 2000)
    @Schema(description = "Explanation for the reaction. Required when action is DISPUTED.", maxLength = 2000)
    String explanation
) {}
