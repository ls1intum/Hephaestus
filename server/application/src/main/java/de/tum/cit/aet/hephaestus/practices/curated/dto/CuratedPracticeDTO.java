package de.tum.cit.aet.hephaestus.practices.curated.dto;

import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogChangeKind;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogEntry;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record CuratedPracticeDTO(
        @NonNull String slug,
        @NonNull Integer position,
        @NonNull CuratedPracticeDefinitionDTO definition,
        @Nullable CuratedPracticeDefinitionDTO shipped,
        @NonNull CatalogEntryStatusDTO status) {
    public static CuratedPracticeDTO from(CatalogEntry<PracticeDefinition> entry) {
        return new CuratedPracticeDTO(
                entry.slug(),
                entry.position(),
                CuratedPracticeDefinitionDTO.from(entry.slug(), entry.effective()),
                entry.changeKind() == CatalogChangeKind.NONE
                        ? null
                        : CuratedPracticeDefinitionDTO.from(entry.slug(), Objects.requireNonNull(entry.shipped())),
                CatalogEntryStatusDTO.from(entry));
    }
}
