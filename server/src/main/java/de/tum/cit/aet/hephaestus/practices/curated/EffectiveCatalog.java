package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.practices.AreaDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

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

    /**
     * What a workspace created now receives: every entry an administrator still offers, minus the
     * practices filed under an area they no longer offer — an area is how its practices are
     * presented, so withholding one withholds them with it.
     */
    public List<CatalogEntry<PracticeDefinition>> installablePractices() {
        Map<String, CatalogEntry<AreaDefinition>> bySlug = areas
            .stream()
            .collect(Collectors.toMap(CatalogEntry::slug, Function.identity()));
        return practices
            .stream()
            .filter(CatalogEntry::offered)
            .filter(entry -> isAreaOffered(bySlug, entry.effective().areaSlug()))
            .toList();
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
        return new CatalogSummary(
            practices.size() + areas.size(),
            count(CatalogEntryState.UPDATE_WAITING, CatalogChangeKind.DETECTION),
            count(CatalogEntryState.UPDATE_WAITING, CatalogChangeKind.WORDING),
            (int) entries()
                .filter(entry -> entry.state() == CatalogEntryState.EDITED_HERE)
                .count(),
            (int) entries()
                .filter(entry -> entry.state() == CatalogEntryState.YOURS)
                .count(),
            (int) entries()
                .filter(entry -> !entry.offered())
                .count(),
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

    private static boolean isAreaOffered(Map<String, CatalogEntry<AreaDefinition>> areas, @Nullable String slug) {
        if (slug == null) {
            return true;
        }
        CatalogEntry<AreaDefinition> area = areas.get(slug);
        return area != null && area.offered();
    }

    /**
     * The catalog at a glance, so an administrator reads one line instead of scanning every entry.
     * Updates are split by whether taking them would change what gets detected — the cheap ones can
     * then be taken together without weighing each up.
     */
    public record CatalogSummary(
        int total,
        int updatesChangingDetection,
        int updatesChangingWordingOnly,
        int editedHere,
        int yours,
        int retired,
        int noLongerShipped
    ) {
        public int updatesWaiting() {
            return updatesChangingDetection + updatesChangingWordingOnly;
        }
    }
}
