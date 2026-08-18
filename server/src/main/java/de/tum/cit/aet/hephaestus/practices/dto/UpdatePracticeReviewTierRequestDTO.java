package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * Sets — or clears — the autonomy tier held by one practice or one area.
 *
 * <p>The field is deliberately nullable. Without a way to say "hold nothing", the inheritance chain would
 * be write-once: an administrator who set one practice explicitly could never put it back under its area's
 * decision, and the setting would silently accumulate the same per-row opinions it exists to remove.
 */
@Schema(description = "Set how much autonomy the system has here, or clear it back to inherit")
public record UpdatePracticeReviewTierRequestDTO(
    @Nullable
    @Schema(
        description = "OFF = not reviewed at all · PROPOSE = the review runs and every observation is " +
            "recorded, and nothing is sent · DELIVER = feedback is delivered without asking. Send null (or " +
            "omit the field) to hold no tier here and inherit — a practice inherits its area's, an area " +
            "inherits the workspace default."
    )
    PracticeReviewTier reviewTier
) {}
