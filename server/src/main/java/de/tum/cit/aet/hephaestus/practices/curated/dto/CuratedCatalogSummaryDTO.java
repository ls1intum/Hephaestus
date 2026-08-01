package de.tum.cit.aet.hephaestus.practices.curated.dto;

import de.tum.cit.aet.hephaestus.practices.curated.EffectiveCatalog;
import org.jspecify.annotations.NonNull;

/**
 * The catalog at a glance, so the state of the whole thing is one sentence rather than a scan of
 * every entry. Updates are counted separately by whether taking them would change what gets
 * detected — the ones that cannot are safe to take together.
 */
public record CuratedCatalogSummaryDTO(
    @NonNull Integer total,
    @NonNull Integer updatesChangingDetection,
    @NonNull Integer updatesChangingWordingOnly,
    @NonNull Integer editedHere,
    @NonNull Integer yours,
    @NonNull Integer retired,
    @NonNull Integer noLongerShipped
) {
    public static CuratedCatalogSummaryDTO from(EffectiveCatalog.CatalogSummary summary) {
        return new CuratedCatalogSummaryDTO(
            summary.total(),
            summary.updatesChangingDetection(),
            summary.updatesChangingWordingOnly(),
            summary.editedHere(),
            summary.yours(),
            summary.retired(),
            summary.noLongerShipped()
        );
    }
}
