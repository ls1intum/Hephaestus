package de.tum.cit.aet.hephaestus.practices.curated.dto;

import de.tum.cit.aet.hephaestus.practices.curated.CuratedPractice;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedPracticeSourceKind;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedPracticeStatus;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedPracticeSyncStatus;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.time.Instant;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record CuratedPracticeSummaryDTO(
    @NonNull Long id,
    @NonNull String slug,
    @NonNull String name,
    @NonNull WorkArtifact artifactType,
    @Nullable String areaSlug,
    @NonNull Integer revisionNumber,
    @NonNull Instant revisionCreatedAt,
    @NonNull CuratedPracticeStatus status,
    @NonNull CuratedPracticeSourceKind sourceKind,
    @NonNull CuratedPracticeSyncStatus syncStatus,
    @Nullable Long latestBundledCatalogRevision,
    @NonNull Instant updatedAt,
    @NonNull Long version
) {
    public static CuratedPracticeSummaryDTO from(CuratedPractice practice) {
        var revision = practice.getCurrentRevision();
        return new CuratedPracticeSummaryDTO(
            practice.getId(),
            practice.getSlug(),
            revision.getName(),
            revision.getArtifactType(),
            revision.getAreaSlug(),
            revision.getRevisionNumber(),
            revision.getCreatedAt(),
            practice.getStatus(),
            practice.getSourceKind(),
            practice.getSyncStatus(),
            practice.getLatestBundledRevision() == null
                ? null
                : practice.getLatestBundledRevision().getBundleRevision(),
            practice.getUpdatedAt(),
            practice.getVersion()
        );
    }
}
