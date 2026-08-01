package de.tum.cit.aet.hephaestus.practices.curated.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** A new curated practice: the slug it will keep, and the definition it starts with. */
@Schema(description = "Request to add a practice to the instance catalog")
public record CreateCuratedPracticeRequestDTO(
    @CuratedSlug @Schema(example = "pr-description-quality") String slug,
    @NotNull @Valid CuratedPracticeRequestDTO definition
) {}
