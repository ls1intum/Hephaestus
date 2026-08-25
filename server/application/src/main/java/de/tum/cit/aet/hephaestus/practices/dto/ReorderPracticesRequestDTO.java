package de.tum.cit.aet.hephaestus.practices.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.jspecify.annotations.Nullable;

@Schema(description = "Reorder one group's practices to match the supplied list")
public record ReorderPracticesRequestDTO(
    @Schema(
        description = "Slug of the group whose practices are being reordered; null reorders the unassigned bucket",
        nullable = true,
        example = "review-ready-work"
    )
    @Nullable
    String groupSlug,

    @NotEmpty(message = "orderedSlugs must not be empty")
    @Schema(
        description = "Practice slugs in the desired display order — the complete set for the group",
        example = "[\"describe-what-and-why\", \"ready-and-traceable-handoff\"]"
    )
    List<String> orderedSlugs
) {}
