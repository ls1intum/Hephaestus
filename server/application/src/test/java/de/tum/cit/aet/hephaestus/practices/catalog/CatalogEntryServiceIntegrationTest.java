package de.tum.cit.aet.hephaestus.practices.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.core.EntityTagPrecondition;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.DefinitionTestFixtures;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogPracticeInputDTO;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CatalogEntryServiceIntegrationTest extends AbstractWorkspaceIntegrationTest {
    @Autowired
    CatalogService catalog;

    @Autowired
    CatalogEntryRepository entries;

    @Test
    void shouldKeepKindAndDefinitionInOneCatalogDocument() {
        String slug = "local-" + UUID.randomUUID();
        var definition = DefinitionTestFixtures.definition();
        var created = catalog.putPractice(
                slug, new CatalogPracticeInputDTO(slug, "scm.pull_request", null, definition, 2), true, null);
        var row = entries.findById(new CatalogEntry.Id(CatalogEntryType.PRACTICE, slug))
                .orElseThrow();
        assertThat(row.getKind()).isEqualTo(new ItemKind("scm.pull_request"));
        assertThat(row.getDefinition()).isNotNull();
        assertThat(created.effective().definition()).isEqualTo(definition);
        assertThatThrownBy(() -> catalog.putPractice(
                        slug,
                        new CatalogPracticeInputDTO(slug, "scm.issue", null, definition, 2),
                        false,
                        EntityTagPrecondition.parse(EntityTagPrecondition.format(created.etag()))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRestoreExclusionWithoutLosingTheAuthoredDefinition() {
        String slug = "exclude-" + UUID.randomUUID();
        var definition = DefinitionTestFixtures.definition();
        var created = catalog.putPractice(
                slug, new CatalogPracticeInputDTO(slug, "scm.pull_request", null, definition, 0), true, null);
        catalog.setExcluded(
                CatalogEntryType.PRACTICE,
                slug,
                true,
                EntityTagPrecondition.parse(EntityTagPrecondition.format(created.etag())));
        var excluded = catalog.practice(slug);
        assertThat(excluded.offered()).isFalse();
        catalog.setExcluded(
                CatalogEntryType.PRACTICE,
                slug,
                false,
                EntityTagPrecondition.parse(EntityTagPrecondition.format(excluded.etag())));
        assertThat(catalog.practice(slug).offered()).isTrue();
        assertThat(catalog.practice(slug).effective().definition()).isEqualTo(definition);
    }

    @Test
    void shouldResetTheDefinitionAfterDeclineAndKeepIndependentCatalogControls() {
        var shipped = catalog.effectiveCatalog().practices().stream()
                .filter(offer -> offer.shipped() != null)
                .findFirst()
                .orElseThrow();
        var local = DefinitionTestFixtures.rename(shipped.effective().definition(), "Local catalog name");
        var changed = catalog.putPractice(
                shipped.slug(),
                new CatalogPracticeInputDTO(
                        shipped.slug(),
                        shipped.effective().kind().value(),
                        shipped.effective().groupSlug(),
                        local,
                        7),
                false,
                EntityTagPrecondition.parse(EntityTagPrecondition.format(shipped.etag())));
        catalog.setExcluded(
                CatalogEntryType.PRACTICE,
                shipped.slug(),
                true,
                EntityTagPrecondition.parse(EntityTagPrecondition.format(changed.etag())));
        var excluded = catalog.practice(shipped.slug());
        catalog.acknowledge(
                CatalogEntryType.PRACTICE,
                shipped.slug(),
                EntityTagPrecondition.parse(EntityTagPrecondition.format(excluded.etag())));
        var declined = catalog.practice(shipped.slug());
        assertThat(declined.effective().definition()).isEqualTo(local);
        catalog.reset(
                CatalogEntryType.PRACTICE,
                shipped.slug(),
                EntityTagPrecondition.parse(EntityTagPrecondition.format(declined.etag())));
        var reset = catalog.practice(shipped.slug());
        assertThat(reset.effective()).isEqualTo(shipped.shipped());
        assertThat(reset.position()).isEqualTo(7);
        assertThat(reset.excluded()).isTrue();
        var row = entries.findById(new CatalogEntry.Id(CatalogEntryType.PRACTICE, shipped.slug()))
                .orElseThrow();
        assertThat(row.getDefinition()).isNull();
        assertThat(row.getBase()).isNull();
        assertThat(row.getKind()).isNull();
        catalog.setExcluded(
                CatalogEntryType.PRACTICE,
                shipped.slug(),
                false,
                EntityTagPrecondition.parse(EntityTagPrecondition.format(reset.etag())));
        assertThat(catalog.practice(shipped.slug()).offered()).isTrue();
    }
}
