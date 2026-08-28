package de.tum.cit.aet.hephaestus.practices.curated.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;

@Schema(description = "Request to add a practice to the instance catalog")
public record CreateCuratedPracticeRequestDTO(
        @NonNull @CuratedSlug @Schema(example = "pr-description-quality")
        String slug,

        @NonNull @NotNull @Valid CuratedPracticeRequestDTO definition) {}
