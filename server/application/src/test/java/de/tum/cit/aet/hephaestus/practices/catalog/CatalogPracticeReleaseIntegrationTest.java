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
    CatalogCustomizationService customization;

    @Autowired
    PracticeReviewDigest digest;

    @Test
    void shouldKeepLocalContentWhenDecliningAShippedRelease() {
        var original = catalog.effectiveCatalog().practices().stream()
                .filter(entry -> entry.shipped() != null)
                .findFirst()
                .orElseThrow();
        var local =
                DefinitionTestFixtures.withCriteria(original.effective().definition(), "Locally authored criteria.");
        var edited = customization.updatePractice(
                original.slug(),
                new CatalogPracticeInputDTO(
                        original.slug(),
                        original.effective().kind().value(),
                        original.effective().groupSlug(),
                        local,
                        original.position()),
                EntityTagPrecondition.parse(EntityTagPrecondition.format(original.etag())));
        var proposal = catalog.proposal(original.slug());
        assertThat(proposal.offeredDigest())
                .isEqualTo(digest.of(proposal.offered()))
                .isNotEqualTo(digest.of(local))
                .isNotEqualTo(proposal.etag());
        customization.acknowledge(
                CatalogEntryType.PRACTICE,
                original.slug(),
                EntityTagPrecondition.parse(EntityTagPrecondition.format(edited.etag())));
        var declined = catalog.practice(original.slug());
        assertThat(declined.effective().definition()).isEqualTo(local);
        assertThat(declined.base()).isEqualTo(declined.shipped());
    }
}
