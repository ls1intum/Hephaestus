package de.tum.cit.aet.hephaestus.practices.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.core.EntityTagPrecondition;
import de.tum.cit.aet.hephaestus.core.exception.PreconditionFailedException;
import de.tum.cit.aet.hephaestus.core.exception.PreconditionRequiredException;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.CanonicalDigest;
import de.tum.cit.aet.hephaestus.practices.DefinitionTestFixtures;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinitionField;
import de.tum.cit.aet.hephaestus.practices.PracticeReleaseChoice;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogGroupInputDTO;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogPracticeInputDTO;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogPracticeUpdateDTO;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

class CatalogEntryServiceIntegrationTest extends AbstractWorkspaceIntegrationTest {
    @Autowired
    CatalogService catalog;

    @Autowired
    CatalogCustomizationService customization;

    @Autowired
    CatalogEntryRepository entries;

    @Autowired
    JsonMapper mapper;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void shouldKeepKindInItsColumnWithoutDuplicatingAuthoredContent() {
        String slug = "local-" + UUID.randomUUID();
        var definition = DefinitionTestFixtures.definition();
        var created = customization.createPractice(new CatalogPracticeInputDTO(
                slug, "scm.pull_request", null, DefinitionTestFixtures.authoring(definition), 2));
        var row = entries.findById(new CatalogEntry.Id(CatalogEntryType.PRACTICE, slug))
                .orElseThrow();
        assertThat(row.getKind()).isEqualTo(new ItemKind("scm.pull_request"));
        assertThat(created.state()).isEqualTo(CatalogEntryState.YOURS);
        assertThat(created.shipped()).isNull();
        assertThat(created.base()).isNull();
        assertThat(row.getBase()).isNull();
        assertThat(row.getDefinition()).isNotNull();
        assertThat(Objects.requireNonNull(row.getDefinition()).has("kind")).isFalse();
        assertThat(row.getKind()).isEqualTo(created.effective().kind());
        assertThat(created.effective().definition()).isEqualTo(definition);
        assertThatThrownBy(() -> customization.updatePractice(
                        slug,
                        new CatalogPracticeUpdateDTO(
                                "scm.issue",
                                null,
                                DefinitionTestFixtures.authoring(DefinitionTestFixtures.definition(
                                        new ItemKind("scm.issue"), "Issue practice", "Issue criteria", null)),
                                2),
                        EntityTagPrecondition.parse(EntityTagPrecondition.format(created.etag()))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(entries.findById(row.getId()).orElseThrow().getKind()).isEqualTo(new ItemKind("scm.pull_request"));
    }

    @Test
    void shouldRestoreExclusionWithoutLosingTheAuthoredDefinition() {
        String slug = "exclude-" + UUID.randomUUID();
        var definition = DefinitionTestFixtures.definition();
        var created = customization.createPractice(new CatalogPracticeInputDTO(
                slug, "scm.pull_request", null, DefinitionTestFixtures.authoring(definition), 0));
        customization.setExcluded(
                CatalogEntryType.PRACTICE,
                slug,
                true,
                EntityTagPrecondition.parse(EntityTagPrecondition.format(created.etag())));
        var excluded = catalog.practice(slug);
        assertThat(excluded.offered()).isFalse();
        customization.setExcluded(
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
        var changed = customization.updatePractice(
                shipped.slug(),
                new CatalogPracticeUpdateDTO(
                        shipped.effective().kind().value(),
                        shipped.effective().groupSlug(),
                        DefinitionTestFixtures.authoring(local),
                        7),
                EntityTagPrecondition.parse(EntityTagPrecondition.format(shipped.etag())));
        customization.setExcluded(
                CatalogEntryType.PRACTICE,
                shipped.slug(),
                true,
                EntityTagPrecondition.parse(EntityTagPrecondition.format(changed.etag())));
        var excluded = catalog.practice(shipped.slug());
        customization.acknowledge(
                CatalogEntryType.PRACTICE,
                shipped.slug(),
                EntityTagPrecondition.parse(EntityTagPrecondition.format(excluded.etag())));
        var declined = catalog.practice(shipped.slug());
        assertThat(declined.effective().definition()).isEqualTo(local);
        customization.reset(
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
        customization.setExcluded(
                CatalogEntryType.PRACTICE,
                shipped.slug(),
                false,
                EntityTagPrecondition.parse(EntityTagPrecondition.format(reset.etag())));
        assertThat(catalog.practice(shipped.slug()).offered()).isTrue();
    }

    @Test
    void shouldRejectAnOldEtagAfterTheDefinitionChangesAndIsRestored() {
        String slug = "stale-" + UUID.randomUUID();
        var definition = DefinitionTestFixtures.definition();
        var first = customization.createPractice(new CatalogPracticeInputDTO(
                slug, "scm.pull_request", null, DefinitionTestFixtures.authoring(definition), 0));
        var edited = customization.updatePractice(
                slug,
                new CatalogPracticeUpdateDTO(
                        "scm.pull_request",
                        null,
                        DefinitionTestFixtures.authoring(DefinitionTestFixtures.rename(definition, "Edited name")),
                        0),
                EntityTagPrecondition.parse(EntityTagPrecondition.format(first.etag())));
        var restored = customization.updatePractice(
                slug,
                new CatalogPracticeUpdateDTO("scm.pull_request", null, DefinitionTestFixtures.authoring(definition), 0),
                EntityTagPrecondition.parse(EntityTagPrecondition.format(edited.etag())));
        assertThat(restored.effective()).isEqualTo(first.effective());
        assertThat(restored.base()).isEqualTo(first.base());
        assertThat(restored.etag()).isNotEqualTo(first.etag());
        assertThatThrownBy(() -> customization.setExcluded(
                        CatalogEntryType.PRACTICE,
                        slug,
                        true,
                        EntityTagPrecondition.parse(EntityTagPrecondition.format(first.etag()))))
                .isInstanceOf(PreconditionFailedException.class);
        assertThat(catalog.practice(slug).excluded()).isFalse();
    }

    @Test
    void shouldRetainRemovedBundledContentAndItsProvenance() {
        String slug = "removed-" + UUID.randomUUID();
        var original = DefinitionTestFixtures.definition();
        var retained = DefinitionTestFixtures.rename(original, "Retained definition");
        var document = new CatalogPracticeDocument(new ItemKind("scm.pull_request"), null, original);
        var row = new CatalogEntry(CatalogEntryType.PRACTICE, slug);
        row.setKind(document.kind());
        row.setDefinition(mapper.valueToTree(new CatalogPracticeDocument(document.kind(), null, retained).content()));
        row.setBase(mapper.valueToTree(document));
        entries.saveAndFlush(row);

        var offer = catalog.practice(slug);
        assertThat(offer.state()).isEqualTo(CatalogEntryState.NO_LONGER_SHIPPED);
        assertThat(offer.effective().definition()).isEqualTo(retained);
        assertThat(offer.base()).isEqualTo(document);
        assertThat(offer.shipped()).isNull();
        assertThatThrownBy(() -> customization.reset(CatalogEntryType.PRACTICE, slug, tag(offer.etag())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(entries.findById(row.getId()).orElseThrow().getDefinition())
                .isEqualTo(mapper.valueToTree(new CatalogPracticeDocument(document.kind(), null, retained).content()));
    }

    @Test
    void shouldExcludeTheWholeGroupAndAllowInclusion() {
        String groupSlug = "group-" + UUID.randomUUID();
        var group = customization.createGroup(new CatalogGroupInputDTO(groupSlug, "Review", null, null, null, 0));
        String slug = "member-" + UUID.randomUUID();
        var definition = DefinitionTestFixtures.definition();
        customization.createPractice(new CatalogPracticeInputDTO(
                slug, "scm.pull_request", groupSlug, DefinitionTestFixtures.authoring(definition), 0));
        customization.setExcluded(CatalogEntryType.GROUP, groupSlug, true, tag(group.etag()));
        assertThat(catalog.practice(slug).offered()).isFalse();
        assertThat(catalog.effectiveCatalog().installablePractices())
                .noneMatch(offer -> offer.slug().equals(slug));
        assertThat(entries.findById(new CatalogEntry.Id(CatalogEntryType.GROUP, groupSlug))
                        .orElseThrow()
                        .getExcludedAt())
                .isNotNull();
        customization.setExcluded(
                CatalogEntryType.GROUP,
                groupSlug,
                false,
                tag(catalog.group(groupSlug).etag()));
        assertThat(catalog.practice(slug).offered()).isTrue();
        assertThat(catalog.practice(slug).effective().definition()).isEqualTo(definition);
    }

    @Test
    void shouldResequenceMovedPracticesAndClearOnlyOrderOverrides() {
        String groupSlug = "move-" + UUID.randomUUID();
        customization.createGroup(new CatalogGroupInputDTO(groupSlug, "Move", null, null, null, 3));
        String firstSlug = "first-" + UUID.randomUUID();
        String secondSlug = "second-" + UUID.randomUUID();
        var definition = DefinitionTestFixtures.definition();
        customization.createPractice(new CatalogPracticeInputDTO(
                firstSlug, "scm.pull_request", groupSlug, DefinitionTestFixtures.authoring(definition), 0));
        customization.createPractice(new CatalogPracticeInputDTO(
                secondSlug, "scm.pull_request", groupSlug, DefinitionTestFixtures.authoring(definition), 1));
        customization.move(
                secondSlug, groupSlug, 0, tag(catalog.effectiveCatalog().etag()));
        assertThat(catalog.practice(secondSlug).position()).isZero();
        assertThat(catalog.practice(firstSlug).position()).isEqualTo(1);
        var id = new CatalogEntry.Id(CatalogEntryType.PRACTICE, secondSlug);
        var before = entries.findById(id).orElseThrow();
        long version = before.getVersion();
        var content = before.getDefinition();
        customization.resetOrder(tag(catalog.effectiveCatalog().etag()));
        var reset = entries.findById(id).orElseThrow();
        assertThat(reset.getPosition()).isNull();
        assertThat(reset.getDefinition()).isEqualTo(content);
        assertThat(reset.getVersion()).isGreaterThan(version);
        assertThat(entries.findById(new CatalogEntry.Id(CatalogEntryType.PRACTICE, firstSlug))
                        .orElseThrow()
                        .getPosition())
                .isNull();
        assertThat(entries.findById(new CatalogEntry.Id(CatalogEntryType.GROUP, groupSlug))
                        .orElseThrow()
                        .getPosition())
                .isNull();
    }

    @Test
    void shouldRejectMissingAndStalePreconditionsWithoutWriting() {
        String slug = "guard-" + UUID.randomUUID();
        var current = customization.createPractice(new CatalogPracticeInputDTO(
                slug,
                "scm.pull_request",
                null,
                DefinitionTestFixtures.authoring(DefinitionTestFixtures.definition()),
                0));
        var id = new CatalogEntry.Id(CatalogEntryType.PRACTICE, slug);
        long version = entries.findById(id).orElseThrow().getVersion();
        assertThatThrownBy(() -> customization.setExcluded(CatalogEntryType.PRACTICE, slug, true, null))
                .isInstanceOf(PreconditionRequiredException.class);
        assertThatThrownBy(() -> customization.setExcluded(CatalogEntryType.PRACTICE, slug, true, tag("stale")))
                .isInstanceOf(PreconditionFailedException.class);
        assertThat(entries.findById(id).orElseThrow().getVersion()).isEqualTo(version);
        assertThat(catalog.practice(slug).etag()).isEqualTo(current.etag());
        assertThat(entries.findById(id).orElseThrow().getExcludedAt()).isNull();
    }

    @Test
    void shouldDeclineChangedUpstreamWithoutLosingLocalContent() {
        var original = catalog.effectiveCatalog().practices().stream()
                .filter(offer -> offer.shipped() != null)
                .findFirst()
                .orElseThrow();
        var shipped = Objects.requireNonNull(original.shipped());
        var local = DefinitionTestFixtures.rename(shipped.definition(), "Locally authored");
        var older = DefinitionTestFixtures.rename(shipped.definition(), "Previously shipped");
        var row = new CatalogEntry(CatalogEntryType.PRACTICE, original.slug());
        row.setKind(shipped.kind());
        row.setPosition(7);
        row.setDefinition(
                mapper.valueToTree(new CatalogPracticeDocument(shipped.kind(), shipped.groupSlug(), local).content()));
        row.setBase(mapper.valueToTree(new CatalogPracticeDocument(shipped.kind(), shipped.groupSlug(), older)));
        entries.saveAndFlush(row);
        var pending = catalog.practice(original.slug());
        assertThat(pending.state()).isEqualTo(CatalogEntryState.UPDATE_WAITING);
        assertThat(pending.changeKind()).isEqualTo(CatalogChangeKind.WORDING);
        customization.acknowledge(CatalogEntryType.PRACTICE, original.slug(), tag(pending.etag()));
        var declined = catalog.practice(original.slug());
        assertThat(declined.effective().definition()).isEqualTo(local);
        assertThat(declined.base()).isEqualTo(shipped);
        assertThat(declined.state()).isEqualTo(CatalogEntryState.EDITED_HERE);
        assertThat(entries.findById(row.getId()).orElseThrow().getBase()).isEqualTo(mapper.valueToTree(shipped));
        customization.reset(CatalogEntryType.PRACTICE, original.slug(), tag(declined.etag()));
        assertThat(catalog.practice(original.slug()).effective()).isEqualTo(shipped);
        assertThat(catalog.practice(original.slug()).position()).isEqualTo(7);
    }

    @Test
    void shouldRefuseResetWhenTheOfferedKindDiffersFromStoredIdentity() {
        var original = catalog.effectiveCatalog().practices().stream()
                .filter(offer -> offer.shipped() != null)
                .findFirst()
                .orElseThrow();
        var kind = new ItemKind("scm.issue");
        var local = DefinitionTestFixtures.definition(kind, "Local issue practice", "Issue criteria", null);
        var row = new CatalogEntry(CatalogEntryType.PRACTICE, original.slug());
        row.setKind(kind);
        row.setDefinition(mapper.valueToTree(new CatalogPracticeDocument(kind, null, local).content()));
        row.setBase(mapper.valueToTree(original.shipped()));
        entries.saveAndFlush(row);
        var current = catalog.practice(original.slug());
        assertThatThrownBy(() -> customization.reset(CatalogEntryType.PRACTICE, original.slug(), tag(current.etag())))
                .isInstanceOf(CatalogConflictException.class);
        assertThat(entries.findById(row.getId()).orElseThrow().getKind()).isEqualTo(kind);
        assertThat(catalog.practice(original.slug()).effective().definition()).isEqualTo(local);
    }

    @Test
    void shouldAuditFinalReleaseContentAndBaselineWithoutDefinitionText() {
        var original = catalog.effectiveCatalog().practices().stream()
                .filter(offer -> offer.shipped() != null)
                .findFirst()
                .orElseThrow();
        var shipped = Objects.requireNonNull(original.shipped());
        var local = DefinitionTestFixtures.rename(shipped.definition(), "Local name");
        var older = DefinitionTestFixtures.rename(shipped.definition(), "Previously shipped name");
        var row = new CatalogEntry(CatalogEntryType.PRACTICE, original.slug());
        row.setKind(shipped.kind());
        row.setDefinition(mapper.valueToTree(shipped.content()));
        row.setBase(mapper.valueToTree(new CatalogPracticeDocument(shipped.kind(), shipped.groupSlug(), older)));
        entries.saveAndFlush(row);
        var changed = customization.updatePractice(
                original.slug(),
                new CatalogPracticeUpdateDTO(
                        shipped.kind().value(), shipped.groupSlug(), DefinitionTestFixtures.authoring(local), 4),
                tag(catalog.practice(original.slug()).etag()));
        var result = customization.applyRelease(
                original.slug(),
                Map.of(PracticeDefinitionField.NAME, PracticeReleaseChoice.OFFERED),
                tag(changed.etag()));
        assertThat(result.base()).isEqualTo(shipped);
        assertThat(result.effective().definition()).isEqualTo(shipped.definition());
        var values = jdbc.queryForMap("""
            SELECT old_value, new_value FROM config_audit_event
             WHERE entity_type = 'CATALOG_PRACTICE' AND entity_id = ?
             ORDER BY id DESC LIMIT 1
            """, original.slug());
        var before =
                mapper.readTree(Objects.requireNonNull(values.get("old_value")).toString());
        var after =
                mapper.readTree(Objects.requireNonNull(values.get("new_value")).toString());
        assertThat(before.path("slug").asString()).isEqualTo(original.slug());
        assertThat(before.path("definitionDigest").asString())
                .isEqualTo(new CanonicalDigest()
                        .add(mapper.writeValueAsString(changed.effective()))
                        .hex());
        assertThat(after.path("definitionDigest").asString())
                .isEqualTo(new CanonicalDigest()
                        .add(mapper.writeValueAsString(result.effective()))
                        .hex());
        assertThat(after.path("baseDigest").asString())
                .isEqualTo(new CanonicalDigest()
                        .add(mapper.writeValueAsString(shipped))
                        .hex());
        assertThat(after.propertyNames())
                .containsExactlyInAnyOrder("slug", "definitionDigest", "baseDigest", "position", "excluded");
        assertThat(after.path("position").asInt()).isEqualTo(4);
        assertThat(after.path("excluded").asBoolean()).isFalse();
        assertThat(before.toString()).doesNotContain(local.criteria());
        assertThat(after.toString()).doesNotContain(local.criteria());
    }

    private static EntityTagPrecondition tag(String etag) {
        return EntityTagPrecondition.parse(EntityTagPrecondition.format(etag));
    }
}
