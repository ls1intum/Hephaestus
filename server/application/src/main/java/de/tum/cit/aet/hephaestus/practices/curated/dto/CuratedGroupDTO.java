package de.tum.cit.aet.hephaestus.practices.curated.dto;

import de.tum.cit.aet.hephaestus.practices.GroupDefinition;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogChangeKind;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogEntry;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record CuratedGroupDTO(
        @NonNull String slug,
        @NonNull Integer position,
        @NonNull CuratedGroupRequestDTO definition,
        @Nullable CuratedGroupRequestDTO shipped,
        @NonNull CatalogEntryStatusDTO status) {
    public static CuratedGroupDTO from(CatalogEntry<GroupDefinition> entry) {
        return new CuratedGroupDTO(
                entry.slug(),
                entry.position(),
                CuratedGroupRequestDTO.of(entry.effective()),
                entry.changeKind() == CatalogChangeKind.NONE
                        ? null
                        : CuratedGroupRequestDTO.of(Objects.requireNonNull(entry.shipped())),
                CatalogEntryStatusDTO.from(entry));
    }
}
