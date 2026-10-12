package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedCatalogService;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedPracticeOverride;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedPracticeOverrideRepository;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedPracticeReleaseService;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeReleaseFieldDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeReleaseProposalDTO;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeGroup;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The one-shot practice script upgrade as the server runs it after startup: an unchanged copy of a bundled practice
 * script takes the script the catalog now offers, at the instance and then in each workspace not yet upgraded, and a
 * diverged copy keeps its own.
 */
@Tag("integration")
class PracticeScriptUpgradeIntegrationTest extends AbstractWorkspaceIntegrationTest {

    /** Two bundled practices of one group, both shipping a practice script. */
    private static final String SLUG = "states-how-to-verify-the-change";

    private static final String OTHER = "ready-and-traceable-handoff";

    private static final String RETIRED_SCRIPT = "// Retired leads contract\nexport const leads = () => [];\n";

    private static final String EDITED_SCRIPT = "// Edited by this workspace\nexport const leads = () => [];\n";

    @Autowired
    private PracticeCatalogInstallationManager manager;

    @Autowired
    private PracticeCatalogInstallationRepository installations;

    @Autowired
    private PracticeReleaseService releases;

    @Autowired
    private PracticeService practiceService;

    @Autowired
    private PracticeRepository practices;

    @Autowired
    private PracticeGroupRepository groups;

    @Autowired
    private WorkspaceRepository workspaces;

    @Autowired
    private CuratedCatalogService catalog;

    @Autowired
    private CuratedPracticeReleaseService instanceReleases;

    @Autowired
    private CuratedPracticeOverrideRepository overrides;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void shouldMoveAnUnchangedCopyToTheOfferedScriptWhenTheWorkspaceAwaitsTheUpgrade() {
        WorkspaceContext ctx = pendingWorkspace("script-upgrade-unchanged");
        PracticeDefinition offered = catalog.practice(SLUG).effective();
        String offeredScript = Objects.requireNonNull(offered.practiceScript());
        adopt(ctx, SLUG, offered.withPracticeScript(RETIRED_SCRIPT));
        Practice before = practice(ctx, SLUG);
        int oldRevision = before.getCurrentRevision().getRevisionNumber();

        manager.upgradePracticeScripts();

        Practice upgraded = practice(ctx, SLUG);
        assertThat(upgraded.getPracticeScript()).isEqualTo(offeredScript);
        assertThat(upgraded.getAdoptedBase()).isEqualTo(offered);
        assertThat(upgraded.getAdoptedBaseSource()).isEqualTo(AdoptedBaseSource.EXACT_ADOPTION);
        assertThat(upgraded.getSourceCuratedFingerprint()).isEqualTo(before.getSourceCuratedFingerprint());
        assertThat(upgraded.getCurrentRevision().getRevisionNumber()).isEqualTo(oldRevision + 1);
        assertThat(upgraded.getCurrentRevision().getPracticeScript()).isEqualTo(offeredScript);
        assertThat(releases.list(ctx))
                .extracting(PracticeReleaseProposalDTO::slug)
                .doesNotContain(SLUG);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM config_audit_event WHERE workspace_id = ?"
                                + " AND entity_type = 'PRACTICE_DEFINITION' AND entity_id = ?"
                                + " AND action = 'UPDATED' AND actor_kind = 'SYSTEM'"
                                + " AND changed_keys @> ARRAY['practiceScriptSha256']::text[]",
                        Long.class,
                        ctx.id(),
                        upgraded.getId().toString()))
                .isEqualTo(1L);
        assertThat(upgradedAt(ctx)).isNotNull();
    }

    @Test
    void shouldKeepADivergedCopyAndItsOfferWhenTheWorkspaceChangedTheScript() {
        WorkspaceContext ctx = pendingWorkspace("script-upgrade-diverged");
        PracticeDefinition offered = catalog.practice(SLUG).effective();
        PracticeDefinition adoptedBase = offered.withPracticeScript(RETIRED_SCRIPT);
        adopt(ctx, SLUG, adoptedBase);
        Practice edited = practice(ctx, SLUG);
        edited.setPracticeScript(EDITED_SCRIPT);
        practices.save(edited);
        PracticeReleaseProposalDTO offerBefore = releases.get(ctx, SLUG);
        int oldRevision = practice(ctx, SLUG).getCurrentRevision().getRevisionNumber();

        manager.upgradePracticeScripts();

        Practice kept = practice(ctx, SLUG);
        assertThat(kept.getPracticeScript()).isEqualTo(EDITED_SCRIPT);
        assertThat(kept.getAdoptedBase()).isEqualTo(adoptedBase);
        assertThat(kept.getCurrentRevision().getRevisionNumber()).isEqualTo(oldRevision);
        PracticeReleaseProposalDTO offerAfter = releases.get(ctx, SLUG);
        assertThat(offerAfter.etag()).isEqualTo(offerBefore.etag());
        assertThat(offerAfter.fields()).anySatisfy(field -> {
            assertThat(field.field()).isEqualTo(PracticeDefinitionField.PRACTICE_SCRIPT);
            assertThat(field.conflict()).isTrue();
        });
        assertThat(upgradedAt(ctx)).isNotNull();
    }

    @Test
    void shouldAdoptOnlyTheScriptAndLeaveTheRestOfTheOfferWhenTheOfferChangedOtherFieldsToo() {
        WorkspaceContext ctx = pendingWorkspace("script-upgrade-partial");
        PracticeDefinition offered = catalog.practice(SLUG).effective();
        String offeredScript = Objects.requireNonNull(offered.practiceScript());
        PracticeDefinition earlier = withCriteria(offered, "Earlier criteria").withPracticeScript(RETIRED_SCRIPT);
        adopt(ctx, SLUG, earlier);

        manager.upgradePracticeScripts();

        Practice upgraded = practice(ctx, SLUG);
        assertThat(upgraded.getPracticeScript()).isEqualTo(offeredScript);
        assertThat(upgraded.getCriteria()).isEqualTo("Earlier criteria");
        assertThat(upgraded.getAdoptedBase()).isEqualTo(earlier.withPracticeScript(offeredScript));
        PracticeReleaseProposalDTO offer = releases.get(ctx, SLUG);
        assertThat(offer.offered()).isEqualTo(offered);
        assertThat(offer.fields())
                .extracting(PracticeReleaseFieldDTO::field)
                .contains(PracticeDefinitionField.CRITERIA)
                .doesNotContain(PracticeDefinitionField.PRACTICE_SCRIPT);
    }

    @Test
    void shouldSkipTheWorkspaceWhenTheUpgradeRunsAgain() {
        WorkspaceContext ctx = pendingWorkspace("script-upgrade-once");
        PracticeDefinition offered = catalog.practice(SLUG).effective();
        adopt(ctx, SLUG, offered.withPracticeScript(RETIRED_SCRIPT));
        manager.upgradePracticeScripts();
        Timestamp firstUpgrade = Objects.requireNonNull(upgradedAt(ctx));
        int upgradedRevision = practice(ctx, SLUG).getCurrentRevision().getRevisionNumber();
        // An unchanged copy of the retired script that appears after the mark is no longer the upgrade's to move.
        adopt(ctx, OTHER, catalog.practice(OTHER).effective().withPracticeScript(RETIRED_SCRIPT));
        int otherRevision = practice(ctx, OTHER).getCurrentRevision().getRevisionNumber();

        manager.upgradePracticeScripts();

        assertThat(practice(ctx, OTHER).getPracticeScript()).isEqualTo(RETIRED_SCRIPT);
        assertThat(practice(ctx, OTHER).getCurrentRevision().getRevisionNumber())
                .isEqualTo(otherRevision);
        assertThat(practice(ctx, SLUG).getCurrentRevision().getRevisionNumber()).isEqualTo(upgradedRevision);
        assertThat(upgradedAt(ctx)).isEqualTo(firstUpgrade);
    }

    @Test
    void shouldMoveAnUnchangedInstanceCopyAndKeepADivergedOneWhenTheCatalogShipsNewScripts() {
        PracticeDefinition shipped =
                Objects.requireNonNull(catalog.practice(SLUG).shipped());
        String shippedScript = Objects.requireNonNull(shipped.practiceScript());
        PracticeDefinition earlier = withCriteria(shipped, "Earlier criteria").withPracticeScript(RETIRED_SCRIPT);
        customizeInstance(SLUG, withCriteria(shipped, "Instance criteria").withPracticeScript(RETIRED_SCRIPT), earlier);
        PracticeDefinition otherShipped =
                Objects.requireNonNull(catalog.practice(OTHER).shipped());
        PracticeDefinition otherBase = otherShipped.withPracticeScript(RETIRED_SCRIPT);
        customizeInstance(OTHER, otherShipped.withPracticeScript(EDITED_SCRIPT), otherBase);
        @Nullable
        String acceptedDigest = overrides.findBySlug(SLUG).orElseThrow().getAcceptedBundledDigest();
        // The instance hop runs while a workspace still awaits the upgrade.
        pendingWorkspace("script-upgrade-instance");

        manager.upgradePracticeScripts();

        CuratedPracticeOverride moved = overrides.findBySlug(SLUG).orElseThrow();
        PracticeDefinition movedDefinition = Objects.requireNonNull(moved.definition());
        assertThat(movedDefinition.practiceScript()).isEqualTo(shippedScript);
        assertThat(movedDefinition.criteria()).isEqualTo("Instance criteria");
        assertThat(moved.getAdoptedBase()).isEqualTo(earlier.withPracticeScript(shippedScript));
        assertThat(moved.getAcceptedBundledDigest()).isEqualTo(acceptedDigest);
        assertThat(catalog.practice(SLUG).effective().practiceScript()).isEqualTo(shippedScript);
        assertThat(instanceReleases.practiceRelease(SLUG).fields())
                .extracting(PracticeReleaseFieldDTO::field)
                .contains(PracticeDefinitionField.CRITERIA)
                .doesNotContain(PracticeDefinitionField.PRACTICE_SCRIPT);

        CuratedPracticeOverride kept = overrides.findBySlug(OTHER).orElseThrow();
        assertThat(Objects.requireNonNull(kept.definition()).practiceScript()).isEqualTo(EDITED_SCRIPT);
        assertThat(kept.getAdoptedBase()).isEqualTo(otherBase);
        assertThat(instanceReleases.practiceRelease(OTHER).fields()).anySatisfy(field -> {
            assertThat(field.field()).isEqualTo(PracticeDefinitionField.PRACTICE_SCRIPT);
            assertThat(field.conflict()).isTrue();
        });
    }

    @Test
    void shouldMoveAWorkspaceCopyToTheShippedScriptWhenItFollowsAnUnchangedInstanceCopy() {
        PracticeDefinition shipped =
                Objects.requireNonNull(catalog.practice(SLUG).shipped());
        String shippedScript = Objects.requireNonNull(shipped.practiceScript());
        PracticeDefinition instanceCopy =
                withCriteria(shipped, "Instance criteria").withPracticeScript(RETIRED_SCRIPT);
        customizeInstance(SLUG, instanceCopy, shipped.withPracticeScript(RETIRED_SCRIPT));
        WorkspaceContext ctx = pendingWorkspace("script-upgrade-through-instance");
        adopt(ctx, SLUG, catalog.practice(SLUG).effective());

        manager.upgradePracticeScripts();

        Practice upgraded = practice(ctx, SLUG);
        assertThat(upgraded.getPracticeScript()).isEqualTo(shippedScript);
        assertThat(upgraded.getCriteria()).isEqualTo("Instance criteria");
        assertThat(upgraded.getAdoptedBase()).isEqualTo(catalog.practice(SLUG).effective());
        assertThat(upgraded.getCurrentRevision().getPracticeScript()).isEqualTo(shippedScript);
        assertThat(releases.list(ctx))
                .extracting(PracticeReleaseProposalDTO::slug)
                .doesNotContain(SLUG);
        assertThat(upgradedAt(ctx)).isNotNull();
    }

    /** A workspace installed before the upgrade shipped: its installation is not yet marked. */
    private WorkspaceContext pendingWorkspace(String slug) {
        User owner = persistUser(slug + "-owner");
        Workspace workspace = createWorkspace(slug, slug, slug + "-org", AccountType.ORG, owner);
        Instant installedAt = Instant.now();
        installations.save(new PracticeCatalogInstallation(workspace.getId(), installedAt, installedAt));
        PracticeGroup group = new PracticeGroup();
        group.setWorkspace(workspace);
        group.setSlug(Objects.requireNonNull(catalog.practice(SLUG).effective().groupSlug()));
        group.setName("Review ready work");
        groups.save(group);
        return WorkspaceContext.fromWorkspace(
                workspaces.findById(workspace.getId()).orElseThrow(),
                Set.of(WorkspaceMembership.WorkspaceRole.ADMIN),
                null);
    }

    private void adopt(WorkspaceContext ctx, String slug, PracticeDefinition definition) {
        practiceService.createPracticeFromCatalog(ctx, slug, definition);
    }

    private void customizeInstance(String slug, PracticeDefinition current, PracticeDefinition adoptedBase) {
        CuratedPracticeOverride override = new CuratedPracticeOverride(slug, Instant.now());
        override.write(current, null, Instant.now());
        override.adoptBundledBase(adoptedBase);
        overrides.save(override);
    }

    private Practice practice(WorkspaceContext ctx, String slug) {
        return practices.findByWorkspaceIdAndSlug(ctx.id(), slug).orElseThrow();
    }

    private @Nullable Timestamp upgradedAt(WorkspaceContext ctx) {
        return jdbc.queryForObject(
                "SELECT practice_scripts_upgraded_at FROM practice_catalog_installation WHERE workspace_id = ?",
                Timestamp.class,
                ctx.id());
    }

    private static PracticeDefinition withCriteria(PracticeDefinition source, String criteria) {
        return new PracticeDefinition(
                source.name(),
                source.signals(),
                source.evidenceRequirements(),
                source.reviewWhen(),
                source.subject(),
                source.precondition(),
                criteria,
                source.practiceScript(),
                source.automatedReviewPolicy(),
                source.whyItMatters(),
                source.whatGoodLooksLike(),
                source.groupSlug(),
                source.deliveryBehavior(),
                source.visual(),
                source.guide());
    }
}
