package de.tum.cit.aet.hephaestus.practices;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.practices.curated.CuratedCatalogService;
import de.tum.cit.aet.hephaestus.practices.curated.EffectiveCatalog;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.observation.PracticeAreaStatusService;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogOriginPresentationTest extends BaseUnitTest {

    @Test
    void practiceBatchReadsTheCatalogOnce() {
        CuratedCatalogService service = mock(CuratedCatalogService.class);
        when(service.catalog()).thenReturn(new EffectiveCatalog(List.of(), List.of()));
        CatalogOriginPresenter presenter = new CatalogOriginPresenter(service);

        presenter.presentPractices(List.of(mock(Practice.class), mock(Practice.class)));

        verify(service).catalog();
    }

    @Test
    void areaBatchReadsTheCatalogOnce() {
        CuratedCatalogService service = mock(CuratedCatalogService.class);
        when(service.catalog()).thenReturn(new EffectiveCatalog(List.of(), List.of()));
        CatalogOriginPresenter presenter = new CatalogOriginPresenter(service);

        presenter.presentAreas(List.of(mock(PracticeArea.class), mock(PracticeArea.class)));

        verify(service).catalog();
    }

    @Test
    void practiceListUsesOneCatalogSnapshotForTheWholeResponse() {
        PracticeService service = mock(PracticeService.class);
        CatalogOriginPresenter presenter = mock(CatalogOriginPresenter.class);
        WorkspaceContext context = mock(WorkspaceContext.class);
        List<Practice> practices = List.of(mock(Practice.class), mock(Practice.class));
        when(service.listPractices(context, null)).thenReturn(practices);
        PracticeCatalogController controller = new PracticeCatalogController(
            service,
            presenter,
            mock(PracticeAreaService.class)
        );

        controller.listPractices(context, null);

        verify(presenter).presentPractices(practices);
        verify(presenter, never()).present(any(Practice.class));
    }

    @Test
    void areaListUsesOneCatalogSnapshotForTheWholeResponse() {
        PracticeAreaService service = mock(PracticeAreaService.class);
        CatalogOriginPresenter presenter = mock(CatalogOriginPresenter.class);
        WorkspaceContext context = mock(WorkspaceContext.class);
        List<PracticeArea> areas = List.of(mock(PracticeArea.class), mock(PracticeArea.class));
        when(service.listAreas(context, null)).thenReturn(areas);
        PracticeAreaController controller = new PracticeAreaController(
            service,
            mock(PracticeAreaStatusService.class),
            presenter
        );

        controller.listAreas(context, null);

        verify(presenter).presentAreas(areas);
        verify(presenter, never()).present(any(PracticeArea.class));
    }
}
