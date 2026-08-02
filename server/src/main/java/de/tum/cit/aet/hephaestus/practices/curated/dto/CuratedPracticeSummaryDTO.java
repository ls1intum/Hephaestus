package de.tum.cit.aet.hephaestus.practices.curated.dto;

import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogEntry;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record CuratedPracticeSummaryDTO(
    @NonNull String slug,
    @NonNull String name,
    @NonNull WorkArtifact artifactType,
    @Nullable String areaSlug,
    @NonNull Integer position,
    @NonNull Boolean effectivelyOffered,
    @NonNull CatalogEntryStatusDTO status
) {
    public static CuratedPracticeSummaryDTO from(CatalogEntry<PracticeDefinition> entry, boolean effectivelyOffered) {
        return new CuratedPracticeSummaryDTO(
            entry.slug(),
            entry.effective().name(),
            entry.effective().artifactType(),
            entry.effective().areaSlug(),
            entry.position(),
            effectivelyOffered,
            CatalogEntryStatusDTO.from(entry)
        );
    }
}
