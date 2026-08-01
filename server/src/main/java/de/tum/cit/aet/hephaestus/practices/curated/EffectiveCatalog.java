package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.practices.AreaDefinition;
import de.tum.cit.aet.hephaestus.practices.CanonicalDigest;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import java.util.List;
import java.util.Optional;

/**
 * The catalog this instance offers: what the build ships, with the administrator's overrides laid
 * over it.
 *
 * <p>Computed on demand, never stored. That is the whole design: there is no merged copy to keep in
 * step with its inputs, so an entry nobody has touched follows Hephaestus by simply having nothing
 * said about it, and a newer build changes what is offered without anything having to run.
 */
public record EffectiveCatalog(
    List<CatalogEntry<AreaDefinition>> areas,
    List<CatalogEntry<PracticeDefinition>> practices
) {
    public Optional<CatalogEntry<PracticeDefinition>> practice(String slug) {
        return practices
            .stream()
            .filter(entry -> entry.slug().equals(slug))
            .findFirst();
    }

    public Optional<CatalogEntry<AreaDefinition>> area(String slug) {
        return areas
            .stream()
            .filter(entry -> entry.slug().equals(slug))
            .findFirst();
    }

    public String structureEtag() {
        CanonicalDigest digest = new CanonicalDigest().addInt(areas.size());
        areas.forEach(entry -> digest.add(entry.slug()).addInt(entry.position()));
        digest.addInt(practices.size());
        practices.forEach(entry ->
            digest.add(entry.slug()).addNullable(entry.effective().areaSlug()).addInt(entry.position())
        );
        return digest.hex().substring(0, 16);
    }

    /**
     * What a workspace created now receives: every entry an administrator still offers, minus the
     * practices filed under an area they no longer offer — an area is how its practices are
     * presented, so withholding one withholds them with it.
     */
    public List<CatalogEntry<PracticeDefinition>> installablePractices() {
        return practices.stream().filter(this::isEffectivelyOffered).toList();
    }

    public boolean isEffectivelyOffered(CatalogEntry<PracticeDefinition> practice) {
        if (!practice.offered()) {
            return false;
        }
        String areaSlug = practice.effective().areaSlug();
        return areaSlug == null || area(areaSlug).map(CatalogEntry::offered).orElse(false);
    }

    public List<CatalogEntry<AreaDefinition>> installableAreas() {
        return areas.stream().filter(CatalogEntry::offered).toList();
    }

    /** Slugs of the practices an area still holds — what retiring it would withhold. */
    public List<String> offeredPracticesIn(String areaSlug) {
        return practices
            .stream()
            .filter(CatalogEntry::offered)
            .filter(entry -> areaSlug.equals(entry.effective().areaSlug()))
            .map(CatalogEntry::slug)
            .toList();
    }

    public CatalogSummary summary() {
        int total = practices.size() + areas.size();
        int notOffered = total - installableAreas().size() - installablePractices().size();
        return new CatalogSummary(
            total,
            count(CatalogEntryState.UPDATE_WAITING, CatalogChangeKind.DETECTION),
            count(CatalogEntryState.UPDATE_WAITING, CatalogChangeKind.WORDING),
            count(CatalogEntryState.UPDATE_WAITING, CatalogChangeKind.PRESENTATION),
            (int) entries()
                .filter(entry -> entry.state() == CatalogEntryState.EDITED_HERE)
                .count(),
            (int) entries()
                .filter(entry -> entry.state() == CatalogEntryState.YOURS)
                .count(),
            notOffered,
            (int) entries()
                .filter(entry -> entry.state() == CatalogEntryState.NO_LONGER_SHIPPED)
                .count()
        );
    }

    private int count(CatalogEntryState state, CatalogChangeKind kind) {
        return (int) entries()
            .filter(entry -> entry.state() == state && entry.changeKind() == kind)
            .count();
    }

    private java.util.stream.Stream<CatalogEntry<?>> entries() {
        return java.util.stream.Stream.concat(areas.stream(), practices.stream());
    }

    /** Counts the catalog states and the consequences of waiting updates. */
    public record CatalogSummary(
        int total,
        int updatesChangingDetection,
        int updatesChangingWordingOnly,
        int updatesChangingPresentation,
        int editedHere,
        int yours,
        int notOffered,
        int noLongerShipped
    ) {
        public int updatesWaiting() {
            return updatesChangingDetection + updatesChangingWordingOnly + updatesChangingPresentation;
        }
    }
}
