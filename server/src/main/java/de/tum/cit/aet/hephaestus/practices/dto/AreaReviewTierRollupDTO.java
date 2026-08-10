package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * One area's slice of the review-tier rollup.
 *
 * <p>A top-level record rather than a member of {@link ReviewTierRollupDTO}, because the OpenAPI filter
 * keeps a schema only when its name ends in {@code DTO} or it is named in the allowlist. A nested record is
 * emitted under its bare simple name, gets dropped by that filter, and leaves a dangling {@code $ref} that
 * fails the client generator rather than the spec build — the silent-drop trap the allowlist comment
 * warns about.
 *
 * @param areaSlug the area, or {@code null} for the practices belonging to no area at all — they skip the
 *     middle level entirely and inherit straight from the workspace
 */
@Schema(description = "One area's practice counts per effective tier, and the area's own tier")
public record AreaReviewTierRollupDTO(
    @Nullable @Schema(description = "Area slug; null groups the practices that belong to no area") String areaSlug,
    @Nullable @Schema(description = "Area name; null for the no-area group") String areaName,
    @NonNull
    @Schema(description = "The tier in force for this area, and where it came from")
    ReviewTierAssignmentDTO reviewTier,
    @NonNull
    @Schema(description = "Practice count per effective tier in this area; every tier is a key")
    Map<PracticeReviewTier, Integer> counts,
    @NonNull
    @Schema(description = "How many of this area's practices set their own tier rather than inheriting")
    Integer overriddenCount
) {}
