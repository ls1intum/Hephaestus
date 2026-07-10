package de.tum.cit.aet.hephaestus.integration.outline.collection;

import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection.MirrorState;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection.SyncStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.NonNull;

/**
 * Admin control-plane view of one mirrored Outline collection: its catalog identity (name, urlId,
 * color, icon captured server-side), the mirror lifecycle state, and the per-collection sync
 * bookkeeping (live document count, last clean pass, last failure). This is the surface the webapp
 * collections table lists, pauses, resumes, and removes.
 */
@Schema(description = "A mirrored Outline collection with its sync state and live document count")
public record OutlineCollectionDTO(
    @NonNull @Schema(description = "Internal registry row id") Long id,
    @NonNull @Schema(description = "Outline collection id (UUID; the natural key)") String collectionId,
    @Schema(description = "Collection name as shown in Outline, if known") String name,
    @Schema(description = "Outline url id (the short slug in collection URLs)") String urlId,
    @Schema(description = "Collection color as configured in Outline") String color,
    @Schema(description = "Collection icon as configured in Outline") String icon,
    @NonNull
    @Schema(description = "Mirror lifecycle state (PAUSED freezes sync but keeps documents)")
    MirrorState state,
    @NonNull
    @Schema(description = "Whether a clean full pass has completed since registration or the last resume")
    SyncStatus syncStatus,
    @NonNull @Schema(description = "Live (non-tombstoned) mirrored document count") Long documentCount,
    @Schema(
        description = "Documents upstream reported for this collection at the last enumeration (coverage denominator)"
    )
    Integer documentsUpstream,
    @Schema(description = "Exports the last pass skipped because the shared budget ran out (0 on a clean pass)")
    Integer exportsSkippedForBudget,
    @Schema(description = "When the last clean sync pass finished, if any") Instant lastSyncedAt,
    @Schema(description = "Last sync failure for this collection, cleared on the next clean pass") String lastSyncError,
    @NonNull @Schema(description = "When the collection was registered for mirroring") Instant createdAt
) {
    /** Projects a registry row plus its live document count into the DTO. */
    public static OutlineCollectionDTO from(OutlineCollection collection, long documentCount) {
        return new OutlineCollectionDTO(
            collection.getId(),
            collection.getCollectionId(),
            collection.getName(),
            collection.getUrlId(),
            collection.getColor(),
            collection.getIcon(),
            collection.getState(),
            collection.getSyncStatus(),
            documentCount,
            collection.getDocumentsUpstream(),
            collection.getExportsSkippedForBudget(),
            collection.getDocumentsSyncedAt(),
            collection.getLastSyncError(),
            collection.getCreatedAt()
        );
    }
}
