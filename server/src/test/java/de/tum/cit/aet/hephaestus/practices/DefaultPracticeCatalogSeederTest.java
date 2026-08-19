package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogEntry;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedCatalogLock;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedCatalogService;
import de.tum.cit.aet.hephaestus.practices.curated.EffectiveCatalog;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.events.WorkspaceCreatedEvent;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.springframework.transaction.support.TransactionOperations;

class DefaultPracticeCatalogSeederTest extends BaseUnitTest {

    @Mock
    private PracticeAreaService areaService;

    @Mock
    private PracticeService practiceService;

    @Mock
    private PracticeAreaRepository areaRepository;

    @Mock
    private PracticeRepository practiceRepository;

    @Mock
    private CuratedCatalogService catalogService;

    @Mock
    private CuratedCatalogLock catalogLock;

    @Mock
    private PracticeCatalogInstallationRepository installationRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Test
    void doesNothingWhenDisabled() {
        seeder(false).seed();

        verifyNoInteractions(workspaceRepository, catalogService, installationRepository);
    }

    @Test
    void installsWhatTheInstanceOffers() {
        when(workspaceRepository.findAll()).thenReturn(List.of(workspace(1L)));
        when(catalogService.catalog()).thenReturn(catalog());
        when(areaService.createAreaFromCatalog(any(), any(), any(), anyInt())).thenReturn(new PracticeArea());

        seeder(true).seed();

        verify(areaService).createAreaFromCatalog(any(), eq("packaging"), any(), eq(0));
        ArgumentCaptor<PracticeDefinition> definition = ArgumentCaptor.forClass(PracticeDefinition.class);
        verify(practiceService).createPracticeFromCatalog(any(), eq("small-prs"), definition.capture());
        assertThat(definition.getValue().criteria()).isEqualTo("Seed criteria");
        verify(installationRepository).save(any());
        InOrder order = inOrder(catalogLock, workspaceRepository, catalogService);
        order.verify(catalogLock).acquire();
        order.verify(workspaceRepository).findByIdForUpdate(1L);
        order.verify(catalogService).catalog();
    }

    @Test
    void doesNotGiveAWorkspaceTheCatalogTwice() {
        when(workspaceRepository.findAll()).thenReturn(List.of(workspace(1L)));
        when(installationRepository.existsById(1L)).thenReturn(true);

        seeder(true).seed();

        verifyNoInteractions(catalogService, practiceService);
        verify(installationRepository, never()).save(any());
    }

    @Test
    void recordsNothingWhenCopyingFails() {
        when(workspaceRepository.findAll()).thenReturn(List.of(workspace(1L)));
        when(catalogService.catalog()).thenReturn(catalog());
        when(areaService.createAreaFromCatalog(any(), any(), any(), anyInt())).thenReturn(new PracticeArea());
        when(practiceService.createPracticeFromCatalog(any(), any(), any())).thenThrow(new RuntimeException("nope"));

        assertThatCode(() -> seeder(true).seed()).doesNotThrowAnyException();

        verify(installationRepository, never()).save(any());
    }

    @Test
    void shouldMarkWorkspaceWhenCreated() {
        seeder(true).onWorkspaceCreated(new WorkspaceCreatedEvent(7L, IntegrationKind.GITLAB));

        verify(installationRepository).save(any());
        verifyNoInteractions(catalogService, practiceService, areaService);
    }

    @Test
    void shouldMarkWorkspaceWhenLegacyRepairIsDisabled() {
        when(workspaceRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(workspace(7L)));

        seeder(false).onWorkspaceCreated(new WorkspaceCreatedEvent(7L, IntegrationKind.GITLAB));

        verify(installationRepository).save(any());
    }

    private static EffectiveCatalog catalog() {
        AreaDefinition area = new AreaDefinition("Packaging work", null, null, null);
        PracticeDefinition practice = new PracticeDefinition(
            "Small PRs",
            PracticeTestEvidence.bindings(ArtifactKinds.PULL_REQUEST),
            "Seed criteria",
            null,
            PracticeTestEvidence.forArtifact(ArtifactKinds.PULL_REQUEST),
            "Reason",
            null,
            "packaging"
        );
        return new EffectiveCatalog(
            List.of(CatalogEntry.shippedOnly("packaging", area, 0)),
            List.of(CatalogEntry.shippedOnly("small-prs", practice, 0))
        );
    }

    private DefaultPracticeCatalogSeeder seeder(boolean enabled) {
        if (enabled) {
            when(workspaceRepository.findByIdForUpdate(any())).thenAnswer(invocation ->
                Optional.of(workspace(invocation.getArgument(0)))
            );
        }
        return new DefaultPracticeCatalogSeeder(
            enabled,
            areaService,
            practiceService,
            areaRepository,
            practiceRepository,
            catalogService,
            catalogLock,
            installationRepository,
            workspaceRepository,
            TransactionOperations.withoutTransaction(),
            Clock.systemUTC()
        );
    }

    private static Workspace workspace(long id) {
        Workspace workspace = new Workspace();
        workspace.setId(id);
        return workspace;
    }
}
