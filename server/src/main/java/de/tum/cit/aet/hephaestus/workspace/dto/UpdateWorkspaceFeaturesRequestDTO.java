package de.tum.cit.aet.hephaestus.workspace.dto;

import de.tum.cit.aet.hephaestus.workspace.CohortVisibility;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for updating workspace feature flags.
 * All fields are nullable — {@code null} means "no change" (PATCH semantics).
 */
@Schema(description = "Request to update workspace feature flags. Null fields are left unchanged.")
public record UpdateWorkspaceFeaturesRequestDTO(
    @Schema(description = "Enable the practice review feature") Boolean practicesEnabled,
    @Schema(description = "Enable the Pi mentor chat feature") Boolean mentorEnabled,
    @Schema(description = "Enable the achievements system") Boolean achievementsEnabled,
    @Schema(description = "Enable automatic practice reviews triggered by PR events")
    Boolean practiceReviewAutoTriggerEnabled,
    @Schema(description = "Enable manual practice reviews triggered via bot command")
    Boolean practiceReviewManualTriggerEnabled,
    @Schema(
        description = "Audience for the k-anonymised cohort aggregate on the practice overview (MENTORS_ONLY, EVERYONE)"
    )
    CohortVisibility cohortVisibility
) {}
