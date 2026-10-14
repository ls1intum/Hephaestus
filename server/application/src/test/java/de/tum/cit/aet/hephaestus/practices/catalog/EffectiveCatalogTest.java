package de.tum.cit.aet.hephaestus.practices.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.DefinitionTestFixtures;
import de.tum.cit.aet.hephaestus.practices.GroupDefinition;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogChangeKind;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogEntryState;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;

class EffectiveCatalogTest extends BaseUnitTest {
    @Test
    void shouldExcludeAChildFromInstallationWhenItsGroupIsExcluded() {
        var group = new CatalogOffer<>(
                "reviews",
                null,
                new GroupDefinition("Reviews", null, null, null),
                null,
                null,
                0,
                true,
                CatalogEntryState.YOURS,
                CatalogChangeKind.NONE,
                "group",
                false);
        var document = new CatalogPracticeDocument(
                new ItemKind("scm.pull_request"), "reviews", DefinitionTestFixtures.definition());
        var practice = new CatalogOffer<>(
                "description",
                document.kind(),
                document,
                null,
                null,
                0,
                false,
                CatalogEntryState.YOURS,
                CatalogChangeKind.NONE,
                "practice",
                true);
        assertThat(new EffectiveCatalog(List.of(group), List.of(practice), "catalog").installablePractices())
                .isEmpty();
    }
}
