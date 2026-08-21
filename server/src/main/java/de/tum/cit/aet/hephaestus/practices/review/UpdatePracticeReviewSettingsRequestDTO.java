package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.workspace.settings.PracticeDeliveryStatus;
import de.tum.cit.aet.hephaestus.workspace.settings.PracticeReviewField;
import de.tum.cit.aet.hephaestus.workspace.settings.WorkspaceReviewScope;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Set;

/**
 * PATCH body for per-workspace practice-review policy; {@code null} fields are unchanged. Reset is
 * applied before the value patch, so a field can be reset and re-set in one request.
 */
@Schema(description = "Update per-workspace practice-review policy. Null fields unchanged; 'reset' clears to inherit.")
public record UpdatePracticeReviewSettingsRequestDTO(
    @Schema(description = "Deliver feedback to already-merged PRs/MRs") Boolean deliverToMerged,
    @Min(value = 0, message = "Cooldown must not be negative")
    @Max(value = 1440, message = "Cooldown must not exceed 1440 minutes")
    @Schema(description = "Minimum minutes between reviews for the same PR; 0 disables the cooldown")
    Integer cooldownMinutes,
    @Schema(description = "Replaces repository and person coverage wholesale. Null leaves it unchanged.")
    WorkspaceReviewScope reviewScope,
    @Schema(description = "Pause or activate external feedback. Resume never releases work from an older revision.")
    PracticeDeliveryStatus deliveryStatus,
    @Schema(
        description = "How much autonomy the system has over practices and areas that hold no autonomy of " +
            "their own. The one decision that moves a whole workspace at once. Null leaves it " +
            "unchanged; name DEFAULT_AUTONOMY in 'reset' to clear it."
    )
    PracticeAutonomy defaultAutonomy,
    @Schema(description = "Fields to reset back to inherit") Set<PracticeReviewField> reset
) {}
