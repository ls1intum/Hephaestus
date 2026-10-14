package de.tum.cit.aet.hephaestus.practices.curated.adoption;

import de.tum.cit.aet.hephaestus.practices.GroupDefinition;
import org.jspecify.annotations.Nullable;

public record CatalogAdoptionGroupDTO(
        CatalogGroupDisposition disposition,
        @Nullable String slug,
        @Nullable GroupDefinition definition) {}
