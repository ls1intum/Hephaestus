package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.practices.curated.CatalogEntry;
import de.tum.cit.aet.hephaestus.practices.curated.EffectiveCatalog;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogLink;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogOriginDTO;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import org.jspecify.annotations.Nullable;

/**
 * Reads a workspace copy's drift off three fingerprints: what the copy runs now, what it was made
 * from, and what the instance offers today.
 *
 * <p>Nothing is stored about the relationship beyond the slug and the fingerprint at the time of the
 * copy, so the answer cannot go stale — and a copy edited away and back reads as in sync again.
 */
public final class CatalogOrigin {

    private CatalogOrigin() {}

    public static @Nullable CatalogOriginDTO of(Practice practice, EffectiveCatalog catalog) {
        if (practice.getSourceCuratedSlug() == null || practice.getCurrentRevision() == null) {
            return null;
        }
        CatalogEntry<PracticeDefinition> entry = catalog.practice(practice.getSourceCuratedSlug()).orElse(null);
        return describe(
            practice.getSourceCuratedSlug(),
            practice.getCurrentRevision().getDetectionFingerprint(),
            practice.getSourceCuratedFingerprint(),
            entry == null ? null : entry.effective().detectionFingerprint(entry.slug()),
            entry != null && entry.offered()
        );
    }

    public static @Nullable CatalogOriginDTO of(PracticeArea area, EffectiveCatalog catalog) {
        if (area.getSourceCuratedSlug() == null) {
            return null;
        }
        CatalogEntry<AreaDefinition> entry = catalog.area(area.getSourceCuratedSlug()).orElse(null);
        return describe(
            area.getSourceCuratedSlug(),
            AreaDefinition.from(area).detectionFingerprint(area.getSlug()),
            area.getSourceCuratedFingerprint(),
            entry == null ? null : entry.effective().detectionFingerprint(entry.slug()),
            entry != null && entry.offered()
        );
    }

    private static CatalogOriginDTO describe(
        String slug,
        @Nullable String runningHere,
        @Nullable String copiedFrom,
        @Nullable String offeredNow,
        boolean sourceOffered
    ) {
        CatalogLink link;
        if (runningHere != null && runningHere.equals(offeredNow)) {
            link = CatalogLink.IN_SYNC;
        } else if (runningHere != null && runningHere.equals(copiedFrom)) {
            // Untouched here, so any difference is the instance having moved the catalog on.
            link = CatalogLink.UPDATE_AVAILABLE;
        } else {
            link = CatalogLink.LOCALLY_EDITED;
        }
        return new CatalogOriginDTO(slug, link, sourceOffered);
    }
}
