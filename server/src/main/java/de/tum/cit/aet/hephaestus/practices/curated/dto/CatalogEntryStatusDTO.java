package de.tum.cit.aet.hephaestus.practices.curated.dto;

import de.tum.cit.aet.hephaestus.practices.CatalogDefinition;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogChangeKind;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogEntry;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogEntryState;
import java.time.Instant;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Where a catalog entry stands. Identical for practices and areas, so one badge renders either.
 *
 * @param changeKind what separates this instance's definition from the one Hephaestus ships now, so
 *     an update that cannot change what gets detected can be taken without weighing it up
 */
public record CatalogEntryStatusDTO(
    /** The tag a write to this entry must be based on; also returned as the response ETag. */
    @NonNull String etag,
    @NonNull CatalogEntryState state,
    @NonNull CatalogChangeKind changeKind,
    @NonNull Boolean offered,
    @NonNull Boolean retired,
    @Nullable Instant updatedAt
) {
    public static <D extends CatalogDefinition> CatalogEntryStatusDTO from(CatalogEntry<D> entry) {
        return new CatalogEntryStatusDTO(
            entry.etag(),
            entry.state(),
            entry.changeKind(),
            entry.offered(),
            entry.retired(),
            entry.updatedAt()
        );
    }
}
