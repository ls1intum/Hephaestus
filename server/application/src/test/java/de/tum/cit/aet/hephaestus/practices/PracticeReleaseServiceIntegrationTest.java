package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.core.EntityTagPrecondition;
import de.tum.cit.aet.hephaestus.practices.catalog.CatalogService;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogPracticeInputDTO;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PracticeReleaseServiceIntegrationTest extends AbstractWorkspaceIntegrationTest {
    @Autowired
    CatalogService catalog;

    @Autowired
    PracticeVersionService versions;

    @Autowired
    PracticeService service;

    @Autowired
    PracticeReleaseService releases;

    @Test
    @WithAdminUser
    void shouldKeepLocalDefinitionWhenDecliningAnInstanceOffer() {
        var owner = persistUser("admin");
        var workspace = createWorkspace("release-decline", "Release", "release-org", AccountType.ORG, owner);
        var source = catalog.effectiveCatalog().installablePractices().getFirst();
        var existing = service.getPractice(WorkspaceContext.fromWorkspace(workspace, Set.of(), null), source.slug());
        var first = existing.getCurrentVersion();
        var changed = DefinitionTestFixtures.rename(source.effective().definition(), "Updated catalog name");
        catalog.putPractice(
                source.slug(),
                new CatalogPracticeInputDTO(
                        source.slug(),
                        source.effective().kind().value(),
                        source.effective().groupSlug(),
                        changed,
                        source.position()),
                false,
                EntityTagPrecondition.parse(EntityTagPrecondition.format(source.etag())));
        var ctx = WorkspaceContext.fromWorkspace(workspace, Set.of(), null);
        var proposal = releases.get(ctx, source.slug());
        releases.decline(
                ctx, source.slug(), EntityTagPrecondition.parse(EntityTagPrecondition.format(proposal.etag())));
        var reloaded = service.getPractice(ctx, source.slug());
        assertThat(reloaded.getCurrentVersion().getId()).isEqualTo(first.getId());
        assertThat(versions.catalogBaseOf(reloaded))
                .isEqualTo(catalog.practice(source.slug()).effective());
        assertThat(releases.list(ctx))
                .noneMatch(release -> release.practiceSlug().equals(source.slug()));
        var declinedProposal = releases.get(ctx, source.slug());
        assertThatThrownBy(() -> releases.reset(ctx, source.slug(), null))
                .isInstanceOf(PracticeReleasePreconditionRequiredException.class);
        assertThatThrownBy(() -> releases.reset(
                        ctx, source.slug(), EntityTagPrecondition.parse(EntityTagPrecondition.format(proposal.etag()))))
                .isInstanceOf(StalePracticeReleaseException.class);
        var reset = releases.reset(
                ctx, source.slug(), EntityTagPrecondition.parse(EntityTagPrecondition.format(declinedProposal.etag())));
        assertThat(reset.getCurrentVersion().getId()).isNotEqualTo(first.getId());
        assertThat(versions.definitionOf(reset.getCurrentVersion())).isEqualTo(changed);
        assertThat(versions.definitionOf(first)).isEqualTo(source.effective().definition());
        assertThat(versions.history(reset)).hasSize(2);
    }
}
