package de.tum.cit.aet.hephaestus.practices.curated.dto;

import de.tum.cit.aet.hephaestus.practices.AreaDefinition;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogChangeKind;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogEntry;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record CuratedAreaDTO(
    @NonNull String slug,
    @NonNull Integer position,
    @NonNull CuratedAreaRequestDTO definition,
    @Nullable CuratedAreaRequestDTO shipped,
    @NonNull CatalogEntryStatusDTO status
) {
    public static CuratedAreaDTO from(CatalogEntry<AreaDefinition> entry) {
        return new CuratedAreaDTO(
            entry.slug(),
            entry.position(),
            CuratedAreaRequestDTO.of(entry.effective()),
            entry.changeKind() == CatalogChangeKind.NONE ? null : CuratedAreaRequestDTO.of(entry.shipped()),
            CatalogEntryStatusDTO.from(entry)
        );
    }
}
