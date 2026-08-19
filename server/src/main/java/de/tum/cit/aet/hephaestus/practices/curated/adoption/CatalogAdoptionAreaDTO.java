package de.tum.cit.aet.hephaestus.practices.curated.adoption;

import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedAreaRequestDTO;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record CatalogAdoptionAreaDTO(
    @NonNull CatalogAreaDisposition disposition,
    @Nullable String slug,
    @Nullable CuratedAreaRequestDTO definition
) {}
