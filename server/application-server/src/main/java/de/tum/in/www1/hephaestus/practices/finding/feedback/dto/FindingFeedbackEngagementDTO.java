package de.tum.in.www1.hephaestus.practices.finding.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Engagement statistics for the current user's feedback actions in a workspace.
 *
 * <p>Each field represents the count of feedback events with that action type.
 * Zero counts are returned as 0, not omitted.
 */
@Schema(description = "Feedback engagement statistics for a contributor in a workspace")
public record FindingFeedbackEngagementDTO(
    @Schema(description = "Number of findings marked as applied/fixed", requiredMode = Schema.RequiredMode.REQUIRED)
    long applied,
    @Schema(description = "Number of findings disputed as incorrect", requiredMode = Schema.RequiredMode.REQUIRED)
    long disputed,
    @Schema(description = "Number of findings marked as not applicable", requiredMode = Schema.RequiredMode.REQUIRED)
    long notApplicable
) {}
