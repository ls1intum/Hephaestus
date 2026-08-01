package de.tum.cit.aet.hephaestus.practices.curated.dto;

import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogEntry;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A practice as the catalog list shows it.
 *
 * <p>Without its criteria: those are thousands of words of detection rubric each, and the list exists
 * to be scanned. The definition, and what Hephaestus ships beside it, come with the single entry.
 */
public record CuratedPracticeSummaryDTO(
    @NonNull String slug,
    @NonNull String name,
    @NonNull WorkArtifact artifactType,
    @Nullable String areaSlug,
    @NonNull CatalogEntryStatusDTO status
) {
    public static CuratedPracticeSummaryDTO from(CatalogEntry<PracticeDefinition> entry) {
        return new CuratedPracticeSummaryDTO(
            entry.slug(),
            entry.effective().name(),
            entry.effective().artifactType(),
            entry.effective().areaSlug(),
            CatalogEntryStatusDTO.from(entry)
        );
    }
}
