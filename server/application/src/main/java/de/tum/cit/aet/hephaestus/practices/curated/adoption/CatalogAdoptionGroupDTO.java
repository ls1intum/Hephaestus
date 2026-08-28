package de.tum.cit.aet.hephaestus.practices.curated.adoption;

import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedGroupRequestDTO;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record CatalogAdoptionGroupDTO(
        @NonNull CatalogGroupDisposition disposition,
        @Nullable String slug,
        @Nullable CuratedGroupRequestDTO definition) {}
