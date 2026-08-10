package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackReach;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NonNull;

/**
 * How many practices sit at each autonomy tier, for the whole workspace and for each area.
 *
 * <p>The single most useful control on a catalogue of a hundred practices, and the reason it is a server
 * response rather than something the client counts: a summary computed by fetching every practice is a
 * summary that costs a page load, so nobody puts it above the fold and nobody sees it.
 */
@Schema(description = "Practice counts per autonomy tier, for the workspace and for each of its areas")
public record ReviewTierRollupDTO(
    @NonNull
    @Schema(description = "The workspace-level decision every area and practice falls back to")
    ReviewTierAssignmentDTO workspaceDefault,
    @NonNull
    @Schema(description = "Where feedback may go in this workspace at all, ANDed with every tier")
    FeedbackReach feedbackReach,
    @NonNull
    @Schema(description = "Practice count per effective tier across the whole workspace; every tier is a key")
    Map<PracticeReviewTier, Integer> counts,
    @NonNull @Schema(description = "The same counts per area, in catalogue order") List<AreaReviewTierRollupDTO> areas
) {}
