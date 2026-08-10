package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import de.tum.cit.aet.hephaestus.practices.review.tier.EffectiveReviewTier;
import de.tum.cit.aet.hephaestus.practices.review.tier.ReviewTierSource;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * How much autonomy the system has over one practice or area, and where that answer came from.
 *
 * <p>Carried as a group rather than as a bare tier because the effective value alone cannot be rendered
 * honestly: an administrator seeing {@code DELIVER} needs to know whether this thing decided that, or is
 * simply going along with its area or its workspace — the first offers a reset, the second sends them
 * somewhere else to change it.
 */
@Schema(
    description = "The autonomy tier in force here, whether it was set here or inherited, and the level " +
        "that decided it"
)
public record ReviewTierAssignmentDTO(
    @NonNull
    @Schema(
        description = "The tier actually in force. OFF = not reviewed · OBSERVE = reviewed and recorded, " +
            "nobody is told · PROPOSE = feedback prepared for a human to approve (not selectable yet) · " +
            "DELIVER = feedback delivered without asking"
    )
    PracticeReviewTier effective,
    @Nullable
    @Schema(
        description = "The tier set on this practice or area itself, or null when it holds none and " +
            "inherits. Send null to the tier endpoint to clear it back to this state."
    )
    PracticeReviewTier override,
    @NonNull
    @Schema(description = "Which level decided the effective tier: PRACTICE, AREA or WORKSPACE")
    ReviewTierSource source,
    @NonNull
    @Schema(description = "True when this practice or area holds no tier of its own and follows a level above")
    Boolean inherited
) {
    /**
     * {@code inherited} is derived from the override, not from the source, because "inherited" is a fact
     * about the level being described while the source is a fact about which level answered. They differ
     * exactly where it matters: an area that set its own tier reports source {@code AREA}, and to that area
     * the value is an override — to a practice under it, the same source is an inheritance. Reading it off
     * the source labelled every area that had made a decision as if it had not.
     */
    public static ReviewTierAssignmentDTO of(EffectiveReviewTier resolved, @Nullable PracticeReviewTier override) {
        return new ReviewTierAssignmentDTO(resolved.tier(), override, resolved.source(), override == null);
    }
}
