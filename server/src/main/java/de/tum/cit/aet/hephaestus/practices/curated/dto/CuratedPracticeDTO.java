package de.tum.cit.aet.hephaestus.practices.curated.dto;

import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogChangeKind;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogEntry;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A practice in the instance catalog.
 *
 * <p>{@code shipped} carries the definition Hephaestus offers right now whenever it differs from the
 * one in force. It is what makes taking an update a decision rather than a leap: the administrator
 * reads what they would be getting before they get it.
 */
public record CuratedPracticeDTO(
    @NonNull String slug,
    @NonNull Integer position,
    @NonNull CuratedPracticeRequestDTO definition,
    @Nullable CuratedPracticeRequestDTO shipped,
    @NonNull CatalogEntryStatusDTO status
) {
    public static CuratedPracticeDTO from(CatalogEntry<PracticeDefinition> entry) {
        return new CuratedPracticeDTO(
            entry.slug(),
            entry.position(),
            CuratedPracticeRequestDTO.of(entry.effective()),
            entry.changeKind() == CatalogChangeKind.NONE ? null : CuratedPracticeRequestDTO.of(entry.shipped()),
            CatalogEntryStatusDTO.from(entry)
        );
    }
}
