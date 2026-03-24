package de.tum.in.www1.hephaestus.practices.finding.feedback.dto;

import de.tum.in.www1.hephaestus.practices.finding.feedback.FindingFeedbackAction;
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
public record CreateFindingFeedbackDTO(
    @NotNull
    @Schema(description = "The feedback action to record", requiredMode = Schema.RequiredMode.REQUIRED)
    FindingFeedbackAction action,
    @Size(max = 2000)
    @Schema(description = "Explanation for the feedback. Required when action is DISPUTED.", maxLength = 2000)
    String explanation
) {}
