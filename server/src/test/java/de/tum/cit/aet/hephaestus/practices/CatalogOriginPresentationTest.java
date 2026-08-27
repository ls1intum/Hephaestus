package de.tum.cit.aet.hephaestus.practices;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedCatalogService;
import de.tum.cit.aet.hephaestus.practices.curated.EffectiveCatalog;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.observation.PracticeAreaStandingService;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaults;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaultsProvider;
import de.tum.cit.aet.hephaestus.practices.review.autonomy.AutonomyRollupService;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CatalogOriginPresentationTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 1L;

    private static final WorkspaceContext CTX = new WorkspaceContext(
        WORKSPACE_ID,
        "acme",
        "Acme",
        AccountType.ORG,
        null,
        false,
        false,
        Set.of()
    );

    @Test
    void practiceBatchReadsTheCatalogOnce() {
        CuratedCatalogService service = mock(CuratedCatalogService.class);
        when(service.catalog()).thenReturn(new EffectiveCatalog(List.of(), List.of()));
        CatalogOriginPresenter presenter = new CatalogOriginPresenter(service, workspaceDefaults());
        Practice first = mock(Practice.class);
        Practice second = mock(Practice.class);
        stubDefinition(first, "first");
        stubDefinition(second, "second");

        presenter.presentPractices(WORKSPACE_ID, List.of(first, second));

        verify(service).catalog();
    }

    /**
     * The autonomy in force is the bottom of a chain that ends at the workspace, so a hundred-row response
     * must not ask the workspace a hundred times either.
     */
    @Test
    void practiceBatchResolvesTheWorkspaceDefaultOnce() {
        CuratedCatalogService service = mock(CuratedCatalogService.class);
        when(service.catalog()).thenReturn(new EffectiveCatalog(List.of(), List.of()));
        WorkspaceReviewDefaultsProvider defaults = workspaceDefaults();
        CatalogOriginPresenter presenter = new CatalogOriginPresenter(service, defaults);
        Practice first = mock(Practice.class);
        Practice second = mock(Practice.class);
        stubDefinition(first, "first");
        stubDefinition(second, "second");

        presenter.presentPractices(WORKSPACE_ID, List.of(first, second));

        verify(defaults).forWorkspace(WORKSPACE_ID);
    }

    private static void stubDefinition(Practice practice, String slug) {
        when(practice.getSlug()).thenReturn(slug);
        when(practice.getName()).thenReturn(slug);
        when(practice.getArtifactKind()).thenReturn(ArtifactKinds.PULL_REQUEST);
        when(practice.getBindings()).thenReturn(PracticeTestEvidence.bindings(ScmSignals.PULL_REQUEST_OPENED));
        when(practice.getCriteria()).thenReturn("Review the change");
        when(practice.getAutomatedReviewPolicy()).thenReturn(PracticeTestEvidence.pullRequest());
    }

    private static WorkspaceReviewDefaultsProvider workspaceDefaults() {
        WorkspaceReviewDefaultsProvider defaults = mock(WorkspaceReviewDefaultsProvider.class);
        when(defaults.forWorkspace(anyLong())).thenReturn(WorkspaceReviewDefaults.UNSET);
        return defaults;
    }

    @Test
    void areaBatchReadsTheCatalogOnce() {
        CuratedCatalogService service = mock(CuratedCatalogService.class);
        when(service.catalog()).thenReturn(new EffectiveCatalog(List.of(), List.of()));
        CatalogOriginPresenter presenter = new CatalogOriginPresenter(service, workspaceDefaults());

        presenter.presentAreas(WORKSPACE_ID, List.of(mock(PracticeArea.class), mock(PracticeArea.class)));

        verify(service).catalog();
    }

    @Test
    void practiceListUsesOneCatalogSnapshotForTheWholeResponse() {
        PracticeService service = mock(PracticeService.class);
        CatalogOriginPresenter presenter = mock(CatalogOriginPresenter.class);
        List<Practice> practices = List.of(mock(Practice.class), mock(Practice.class));
        when(service.listPractices(CTX, null)).thenReturn(practices);
        PracticeCatalogController controller = new PracticeCatalogController(
            service,
            presenter,
            mock(AutonomyRollupService.class),
            mock(PracticeAreaService.class),
            mock(PracticeDefinitionOptionsService.class)
        );

        controller.listPractices(CTX, null);

        verify(presenter).presentPractices(WORKSPACE_ID, practices);
        verify(presenter, never()).present(anyLong(), any(Practice.class));
    }

    @Test
    void areaListUsesOneCatalogSnapshotForTheWholeResponse() {
        PracticeAreaService service = mock(PracticeAreaService.class);
        CatalogOriginPresenter presenter = mock(CatalogOriginPresenter.class);
        List<PracticeArea> areas = List.of(mock(PracticeArea.class), mock(PracticeArea.class));
        when(service.listAreas(CTX, null)).thenReturn(areas);
        PracticeAreaController controller = new PracticeAreaController(
            service,
            mock(PracticeAreaStandingService.class),
            presenter
        );

        controller.listAreas(CTX, null);

        verify(presenter).presentAreas(WORKSPACE_ID, areas);
        verify(presenter, never()).present(anyLong(), any(PracticeArea.class));
    }
}
