package de.tum.cit.aet.hephaestus.practices.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Request to reorder the practices within ONE area (or the unassigned bucket). Each practice's
 * {@code displayOrder} is set to its index in {@code orderedSlugs}, so the whole bucket's ordering is
 * written atomically in one request.
 */
@Schema(description = "Reorder the practices in one area — displayOrder follows the list index")
public record ReorderPracticesRequestDTO(
    @Schema(
        description = "Slug of the area whose practices are being reordered; null reorders the unassigned bucket",
        nullable = true,
        example = "review-ready-work"
    )
    @Nullable
    String areaSlug,

    @NotEmpty(message = "orderedSlugs must not be empty")
    @Schema(
        description = "Practice slugs in the desired display order — the complete set for the area",
        example = "[\"describe-what-and-why\", \"ready-and-traceable-handoff\"]"
    )
    List<String> orderedSlugs
) {}
