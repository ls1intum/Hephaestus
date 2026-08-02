package de.tum.cit.aet.hephaestus.practices.curated;

import static de.tum.cit.aet.hephaestus.practices.curated.CuratedCatalogFixtures.area;
import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.AreaDefinition;
import de.tum.cit.aet.hephaestus.practices.curated.BundledPracticeCatalog.BundledEntry;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CuratedCatalogModelTest extends BaseUnitTest {

    private static final Instant NOW = Instant.parse("2026-08-02T00:00:00Z");

    @Test
    void customEntryDoesNotFreezeTheBundledOrder() {
        CuratedAreaOverride custom = customArea("custom", "Custom");
        BundledPracticeCatalog first = catalog(entry("a", "A", 0), entry("b", "B", 1));
        BundledPracticeCatalog reordered = catalog(entry("b", "B", 0), entry("a", "A", 1));

        assertThat(slugs(CuratedCatalogModel.compose(first, List.of(custom), List.of()))).containsExactly(
            "a",
            "b",
            "custom"
        );
        assertThat(slugs(CuratedCatalogModel.compose(reordered, List.of(custom), List.of()))).containsExactly(
            "b",
            "a",
            "custom"
        );
    }

    @Test
    void sameNameCustomEntriesHaveAStableSlugTieBreak() {
        CuratedAreaOverride second = customArea("z-second", "Same name");
        CuratedAreaOverride first = customArea("a-first", "Same name");

        assertThat(slugs(CuratedCatalogModel.compose(catalog(), List.of(second, first), List.of()))).containsExactly(
            "a-first",
            "z-second"
        );
    }

    private static CuratedAreaOverride customArea(String slug, String name) {
        CuratedAreaOverride override = new CuratedAreaOverride(slug, NOW);
        override.write(area(name, "Description"), null, NOW);
        return override;
    }

    private static BundledEntry<AreaDefinition> entry(String slug, String name, int position) {
        return new BundledEntry<>(slug, area(name, "Description"), position);
    }

    @SafeVarargs
    private static BundledPracticeCatalog catalog(BundledEntry<AreaDefinition>... areas) {
        return new BundledPracticeCatalog(List.of(areas), List.of());
    }

    private static List<String> slugs(EffectiveCatalog catalog) {
        return catalog.areas().stream().map(CatalogEntry::slug).toList();
    }
}
