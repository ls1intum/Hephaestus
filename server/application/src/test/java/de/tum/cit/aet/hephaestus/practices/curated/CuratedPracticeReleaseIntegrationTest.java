package de.tum.cit.aet.hephaestus.practices.curated;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.EntityTagPrecondition;
import de.tum.cit.aet.hephaestus.practices.DefinitionTestFixtures;
import de.tum.cit.aet.hephaestus.practices.catalog.CatalogReleaseService;
import de.tum.cit.aet.hephaestus.practices.catalog.CatalogService;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogPracticeInputDTO;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CuratedPracticeReleaseIntegrationTest extends AbstractWorkspaceIntegrationTest {
    @Autowired
    CatalogService catalog;

    @Autowired
    CatalogReleaseService releases;

    @Test
    void shouldKeepLocalContentWhenDecliningAShippedRelease() {
        var original = catalog.effectiveCatalog().practices().stream()
                .filter(entry -> entry.shipped() != null)
                .findFirst()
                .orElseThrow();
        var local = DefinitionTestFixtures.rename(original.effective().definition(), "Locally named practice");
        var edited = catalog.putPractice(
                original.slug(),
                new CatalogPracticeInputDTO(
                        original.slug(),
                        original.effective().kind().value(),
                        original.effective().groupSlug(),
                        local,
                        original.position()),
                false,
                EntityTagPrecondition.parse(EntityTagPrecondition.format(original.etag())));
        var declined = releases.decline(
                original.slug(), EntityTagPrecondition.parse(EntityTagPrecondition.format(edited.etag())));
        assertThat(declined.effective().definition()).isEqualTo(local);
        assertThat(declined.base()).isEqualTo(declined.shipped());
    }
}
