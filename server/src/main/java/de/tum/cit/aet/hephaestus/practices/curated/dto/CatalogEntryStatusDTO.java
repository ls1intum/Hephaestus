package de.tum.cit.aet.hephaestus.practices.curated.dto;

import de.tum.cit.aet.hephaestus.practices.CatalogDefinition;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogChangeKind;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogEntry;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogEntryState;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

public record CatalogEntryStatusDTO(
    @NonNull @Schema(description = "Strong entity tag to send in If-Match when updating this entry") String etag,
    @NonNull CatalogEntryState state,
    @NonNull CatalogChangeKind changeKind,
    @NonNull Boolean offered
) {
    public static <D extends CatalogDefinition> CatalogEntryStatusDTO from(CatalogEntry<D> entry) {
        return new CatalogEntryStatusDTO(entry.etag(), entry.state(), entry.changeKind(), entry.offered());
    }
}
