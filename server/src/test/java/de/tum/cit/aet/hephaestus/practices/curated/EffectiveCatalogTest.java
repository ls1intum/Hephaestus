package de.tum.cit.aet.hephaestus.practices.curated;

import static de.tum.cit.aet.hephaestus.practices.curated.CuratedCatalogFixtures.area;
import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
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
            WorkArtifact.PULL_REQUEST,
            List.of("PullRequestCreated"),
            "Criteria",
            null,
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
}
