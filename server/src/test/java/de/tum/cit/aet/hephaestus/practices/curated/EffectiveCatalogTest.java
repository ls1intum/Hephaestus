package de.tum.cit.aet.hephaestus.practices.curated;

import static de.tum.cit.aet.hephaestus.practices.curated.CuratedCatalogFixtures.area;
import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;

class EffectiveCatalogTest extends BaseUnitTest {

    @Test
    void summaryCountsPracticesWithheldByARetiredAreaAsNotOffered() {
        var areaDefinition = area("Quality", "Make work easy to change");
        var retiredArea = new CatalogEntry<>("quality", areaDefinition, areaDefinition, null, null, true, 0, null);
        PracticeDefinition definition = new PracticeDefinition(
            "Small PRs",
            ArtifactKinds.PULL_REQUEST,
            List.of("PullRequestCreated"),
            "Criteria",
            null,
            PracticeTestEvidence.forArtifact(ArtifactKinds.PULL_REQUEST),
            "Reason",
            null,
            "quality"
        );
        var catalog = new EffectiveCatalog(
            List.of(retiredArea),
            List.of(CatalogEntry.shippedOnly("small-prs", definition, 0))
        );

        assertThat(catalog.summary().notOffered()).isEqualTo(2);
    }

    @Test
    void catalogTagIncludesWhetherTheInstanceOwnsTheOrder() {
        var areaDefinition = area("Quality", "Make work easy to change");
        var areas = List.of(CatalogEntry.shippedOnly("quality", areaDefinition, 0));

        assertThat(new EffectiveCatalog(areas, List.of(), true).etag()).isNotEqualTo(
            new EffectiveCatalog(areas, List.of(), false).etag()
        );
    }

    @Test
    void catalogTagIncludesEntryPositions() {
        var areaDefinition = area("Quality", "Make work easy to change");
        var first = CatalogEntry.shippedOnly("quality", areaDefinition, 0);
        var moved = CatalogEntry.shippedOnly("quality", areaDefinition, 1);

        assertThat(new EffectiveCatalog(List.of(first), List.of()).etag()).isNotEqualTo(
            new EffectiveCatalog(List.of(moved), List.of()).etag()
        );
    }
}
