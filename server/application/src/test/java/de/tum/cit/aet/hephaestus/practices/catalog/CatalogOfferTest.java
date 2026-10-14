package de.tum.cit.aet.hephaestus.practices.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.GroupDefinition;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogChangeKind;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogEntryState;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

class CatalogOfferTest extends BaseUnitTest {
    @Test
    void shouldKeepExclusionSeparateFromTheEffectiveDefinition() {
        var definition = new GroupDefinition("Reviews", null, null, null);
        var offer = new CatalogOffer<>(
                "reviews",
                null,
                definition,
                definition,
                null,
                0,
                true,
                CatalogEntryState.FROM_HEPHAESTUS,
                CatalogChangeKind.NONE,
                "etag");
        assertThat(offer.offered()).isFalse();
        assertThat(offer.effective()).isEqualTo(definition);
    }
}
