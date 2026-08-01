package de.tum.cit.aet.hephaestus.practices.curated.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** A new curated area: the slug it will keep, and the definition it starts with. */
@Schema(description = "Request to add an area to the instance catalog")
public record CreateCuratedAreaRequestDTO(
    @CuratedSlug @Schema(example = "reviewing-work") String slug,
    @NotNull @Valid CuratedAreaRequestDTO definition
) {}
