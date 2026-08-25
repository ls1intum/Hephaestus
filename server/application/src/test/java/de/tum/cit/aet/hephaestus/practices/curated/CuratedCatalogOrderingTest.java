package de.tum.cit.aet.hephaestus.practices.curated;

import static de.tum.cit.aet.hephaestus.practices.curated.CuratedCatalogFixtures.group;
import static de.tum.cit.aet.hephaestus.practices.curated.CuratedCatalogFixtures.practice;
import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.GroupDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CuratedCatalogOrderingTest extends BaseUnitTest {

    @Test
    void sourceOrderAppliesUntilAnAdministratorOrdersGroups() {
        var first = CatalogEntry.shippedOnly("first", group("First", "First"), 0);
        var second = CatalogEntry.shippedOnly("second", group("Second", "Second"), 1);

        assertThat(CuratedCatalogModel.orderGroups(List.of(second, first), Set.of()))
            .extracting(CatalogEntry::slug)
            .containsExactly("first", "second");
    }

    @Test
    void aNewShippedGroupAppendsToTheLocalOrder() {
        var first = positionedGroup("first", group("First", "First"), 1);
        var second = positionedGroup("second", group("Second", "Second"), 0);
        var newlyShipped = CatalogEntry.shippedOnly("new", group("A new group", "New"), 0);

        var ordered = CuratedCatalogModel.orderGroups(List.of(first, newlyShipped, second), Set.of("first", "second"));

        assertThat(ordered).extracting(CatalogEntry::slug).containsExactly("second", "first", "new");
        assertThat(ordered).extracting(CatalogEntry::position).containsExactly(0, 1, 2);
    }

    @Test
    void aNewShippedPracticeAppendsWithinItsLocallyOrderedGroup() {
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

    private static CatalogEntry<GroupDefinition> positionedGroup(
        String slug,
        GroupDefinition definition,
        int position
    ) {
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
