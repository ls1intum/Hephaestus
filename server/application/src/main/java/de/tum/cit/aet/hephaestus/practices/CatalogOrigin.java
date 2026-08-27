package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.practices.curated.CatalogEntry;
import de.tum.cit.aet.hephaestus.practices.curated.EffectiveCatalog;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogLink;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogOriginDTO;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeGroup;
import org.jspecify.annotations.Nullable;

/** Derives workspace drift from current, source, and effective catalog fingerprints. */
public final class CatalogOrigin {

    private CatalogOrigin() {}

    public static @Nullable CatalogOriginDTO of(Practice practice, EffectiveCatalog catalog) {
        if (practice.getSourceCuratedSlug() == null || practice.getCurrentRevision() == null) {
            return null;
        }
        CatalogEntry<PracticeDefinition> entry =
                catalog.practice(practice.getSourceCuratedSlug()).orElse(null);
        boolean sourceOffered = entry != null && catalog.isEffectivelyOffered(entry);
        return describe(
                practice.getSourceCuratedSlug(),
                practice.getCurrentRevision().getReviewRuleFingerprint(),
                practice.getSourceCuratedFingerprint(),
                entry == null ? null : entry.effective().provenanceFingerprint(entry.slug()),
                sourceOffered);
    }

    public static @Nullable CatalogOriginDTO of(PracticeGroup group, EffectiveCatalog catalog) {
        if (group.getSourceCuratedSlug() == null) {
            return null;
        }
        CatalogEntry<GroupDefinition> entry =
                catalog.group(group.getSourceCuratedSlug()).orElse(null);
        return describe(
                group.getSourceCuratedSlug(),
                GroupDefinition.from(group).provenanceFingerprint(group.getSlug()),
                group.getSourceCuratedFingerprint(),
                entry == null ? null : entry.effective().provenanceFingerprint(entry.slug()),
                entry != null && entry.offered());
    }

    private static CatalogOriginDTO describe(
            String slug,
            @Nullable String runningHere,
            @Nullable String copiedFrom,
            @Nullable String offeredNow,
            boolean sourceOffered) {
        boolean matchesCatalog = runningHere != null && runningHere.equals(offeredNow);
        boolean matchesSource = runningHere != null && runningHere.equals(copiedFrom);
        CatalogLink link;
        if (matchesCatalog) {
            link = CatalogLink.IN_SYNC;
        } else if (matchesSource) {
            link = CatalogLink.UPDATE_AVAILABLE;
        } else {
            link = CatalogLink.LOCALLY_EDITED;
        }
        return new CatalogOriginDTO(slug, link, sourceOffered);
    }
}
