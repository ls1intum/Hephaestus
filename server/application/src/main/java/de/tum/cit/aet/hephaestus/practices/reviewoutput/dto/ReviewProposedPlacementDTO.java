package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import de.tum.cit.aet.hephaestus.practices.feedback.PlacementType;
import de.tum.cit.aet.hephaestus.practices.feedback.ProposedPlacement;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "One exact provider message included in a review awaiting approval")
public record ReviewProposedPlacementDTO(
        @NonNull PlacementType type,
        @NonNull String body,

        @Schema(description = "Head-side file path; null for the summary") @Nullable
        String path,

        @Schema(description = "First 1-based line; null for the summary") @Nullable
        Integer startLine,

        @Schema(description = "Last 1-based line; null for a single-line comment or summary") @Nullable
        Integer endLine) {
    public static ReviewProposedPlacementDTO from(ProposedPlacement placement) {
        return new ReviewProposedPlacementDTO(
                placement.type(), placement.body(), placement.path(), placement.startLine(), placement.endLine());
    }
}
