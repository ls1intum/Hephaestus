package de.tum.cit.aet.hephaestus.practices.curated.dto;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.PracticeAutomatedReview;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogEntry;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record CuratedPracticeSummaryDTO(
    @NonNull String slug,
    @NonNull String name,
    @NonNull ArtifactKind artifactKind,
    @NonNull PracticeAutomatedReview automatedReview,
    @Nullable String groupSlug,
    @NonNull Integer position,
    @NonNull Boolean effectivelyOffered,
    @NonNull CatalogEntryStatusDTO status
) {
    public static CuratedPracticeSummaryDTO from(CatalogEntry<PracticeDefinition> entry, boolean effectivelyOffered) {
        return new CuratedPracticeSummaryDTO(
            entry.slug(),
            entry.effective().name(),
            entry.effective().artifactKind(),
            entry.effective().automatedReviewPolicy().automatedReview(),
            entry.effective().groupSlug(),
            entry.position(),
            effectivelyOffered,
            CatalogEntryStatusDTO.from(entry)
        );
    }
}
