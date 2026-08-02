package de.tum.cit.aet.hephaestus.practices.curated.dto;

import de.tum.cit.aet.hephaestus.practices.CatalogDefinition;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogChangeKind;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogEntry;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogEntryState;
import org.jspecify.annotations.NonNull;

/**
 * Where a catalog entry stands. Identical for practices and areas, so one badge renders either.
 *
 * @param changeKind whether taking the Hephaestus definition changes wording, presentation, or
 *     detection
 */
public record CatalogEntryStatusDTO(
    /** The tag a write to this entry must be based on; also returned as the response ETag. */
    @NonNull String etag,
    @NonNull CatalogEntryState state,
    @NonNull CatalogChangeKind changeKind,
    @NonNull Boolean offered
) {
    public static <D extends CatalogDefinition> CatalogEntryStatusDTO from(CatalogEntry<D> entry) {
        return new CatalogEntryStatusDTO(entry.etag(), entry.state(), entry.changeKind(), entry.offered());
    }
}
