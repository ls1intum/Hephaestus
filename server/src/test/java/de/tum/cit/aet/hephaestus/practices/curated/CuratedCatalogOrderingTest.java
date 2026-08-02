package de.tum.cit.aet.hephaestus.practices.curated;

import static de.tum.cit.aet.hephaestus.practices.curated.CuratedCatalogFixtures.area;
import static de.tum.cit.aet.hephaestus.practices.curated.CuratedCatalogFixtures.practice;
import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.AreaDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CuratedCatalogOrderingTest extends BaseUnitTest {

    @Test
    void sourceOrderAppliesUntilAnAdministratorOrdersAreas() {
        var first = CatalogEntry.shippedOnly("first", area("First", "First"), 0);
        var second = CatalogEntry.shippedOnly("second", area("Second", "Second"), 1);

        assertThat(CuratedCatalogModel.orderAreas(List.of(second, first), Set.of()))
            .extracting(CatalogEntry::slug)
            .containsExactly("first", "second");
    }

    @Test
    void aNewShippedAreaAppendsToTheLocalOrder() {
        var first = positionedArea("first", area("First", "First"), 1);
        var second = positionedArea("second", area("Second", "Second"), 0);
        var newlyShipped = CatalogEntry.shippedOnly("new", area("A new area", "New"), 0);

        var ordered = CuratedCatalogModel.orderAreas(List.of(first, newlyShipped, second), Set.of("first", "second"));

        assertThat(ordered).extracting(CatalogEntry::slug).containsExactly("second", "first", "new");
        assertThat(ordered).extracting(CatalogEntry::position).containsExactly(0, 1, 2);
    }

    @Test
    void aNewShippedPracticeAppendsWithinItsLocallyOrderedArea() {
        var first = positionedPractice("first", practice("First", "First", "First"), 1);
        var second = positionedPractice("second", practice("Second", "Second", "Second"), 0);
        var newlyShipped = CatalogEntry.shippedOnly("new", practice("A new practice", "New", "New"), 0);

        var ordered = CuratedCatalogModel.orderPractices(
            List.of(first, newlyShipped, second),
            Set.of("first", "second")
        );

        assertThat(ordered).extracting(CatalogEntry::slug).containsExactly("second", "first", "new");
        assertThat(ordered).extracting(CatalogEntry::position).containsExactly(0, 1, 2);
    }

    private static CatalogEntry<AreaDefinition> positionedArea(String slug, AreaDefinition definition, int position) {
        return new CatalogEntry<>(slug, definition, definition, null, null, false, position, null);
    }

    private static CatalogEntry<PracticeDefinition> positionedPractice(
        String slug,
        PracticeDefinition definition,
        int position
    ) {
        return new CatalogEntry<>(slug, definition, definition, null, null, false, position, null);
    }
}
