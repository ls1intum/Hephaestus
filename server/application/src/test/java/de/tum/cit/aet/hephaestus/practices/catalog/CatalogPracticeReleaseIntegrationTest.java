package de.tum.cit.aet.hephaestus.practices.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.EntityTagPrecondition;
import de.tum.cit.aet.hephaestus.practices.DefinitionTestFixtures;
import de.tum.cit.aet.hephaestus.practices.PracticeReviewDigest;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogPracticeInputDTO;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CatalogPracticeReleaseIntegrationTest extends AbstractWorkspaceIntegrationTest {
    @Autowired
    CatalogService catalog;

    @Autowired
    PracticeReviewDigest digest;

    @Autowired
    CatalogReleaseService releases;

    @Test
    void shouldKeepLocalContentWhenDecliningAShippedRelease() {
        var original = catalog.effectiveCatalog().practices().stream()
                .filter(entry -> entry.shipped() != null)
                .findFirst()
                .orElseThrow();
        var local =
                DefinitionTestFixtures.withCriteria(original.effective().definition(), "Locally authored criteria.");
        var edited = catalog.updatePractice(
                original.slug(),
                new CatalogPracticeInputDTO(
                        original.slug(),
                        original.effective().kind().value(),
                        original.effective().groupSlug(),
                        local,
                        original.position()),
                EntityTagPrecondition.parse(EntityTagPrecondition.format(original.etag())));
        var proposal = releases.proposal(original.slug());
        assertThat(proposal.offeredDigest())
                .isEqualTo(digest.of(proposal.offered()))
                .isNotEqualTo(digest.of(local))
                .isNotEqualTo(proposal.etag());
        var declined = releases.decline(
                original.slug(), EntityTagPrecondition.parse(EntityTagPrecondition.format(edited.etag())));
        assertThat(declined.effective().definition()).isEqualTo(local);
        assertThat(declined.base()).isEqualTo(declined.shipped());
    }
}
