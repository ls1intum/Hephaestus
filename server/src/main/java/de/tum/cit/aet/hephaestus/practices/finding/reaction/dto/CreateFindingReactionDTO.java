package de.tum.cit.aet.hephaestus.practices.finding.reaction.dto;

import de.tum.cit.aet.hephaestus.practices.finding.reaction.FindingReactionAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for submitting feedback on a practice finding.
 *
 * @param action      the feedback action (required)
 * @param explanation free-text explanation (optional for APPLIED/NOT_APPLICABLE, required for DISPUTED)
 */
@Schema(description = "Submit feedback on an AI-generated practice finding")
public record CreateFindingReactionDTO(
    @NotNull @Schema(description = "The feedback action to record") FindingReactionAction action,
    @Size(max = 2000)
    @Schema(description = "Explanation for the feedback. Required when action is DISPUTED.", maxLength = 2000)
    String explanation
) {}
