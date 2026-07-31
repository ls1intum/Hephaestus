package de.tum.cit.aet.hephaestus.practices.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@Schema(description = "Request to move a practice to an area or Unassigned")
public record BindPracticeAreaRequestDTO(
    @Schema(
        description = "Destination area slug; omit or set to null for Unassigned",
        nullable = true,
        example = "review-ready-work"
    )
    @Nullable
    String areaSlug
) {}
