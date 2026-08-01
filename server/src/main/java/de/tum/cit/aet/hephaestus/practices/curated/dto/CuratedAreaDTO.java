package de.tum.cit.aet.hephaestus.practices.curated.dto;

import de.tum.cit.aet.hephaestus.practices.AreaDefinition;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogChangeKind;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogEntry;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** An area in the instance catalog, with what Hephaestus ships now when it differs. */
public record CuratedAreaDTO(
    @NonNull String slug,
    @NonNull CuratedAreaRequestDTO definition,
    @Nullable CuratedAreaRequestDTO shipped,
    @NonNull CatalogEntryStatusDTO status
) {
    public static CuratedAreaDTO from(CatalogEntry<AreaDefinition> entry) {
        return new CuratedAreaDTO(
            entry.slug(),
            CuratedAreaRequestDTO.of(entry.effective()),
            entry.changeKind() == CatalogChangeKind.NONE ? null : CuratedAreaRequestDTO.of(entry.shipped()),
            CatalogEntryStatusDTO.from(entry)
        );
    }
}
