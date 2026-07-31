package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedPractice;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedPracticeArea;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedPracticeAreaRepository;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedPracticeRepository;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedPracticeRevision;
import de.tum.cit.aet.hephaestus.practices.dto.TriggerEventsConverter;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.events.WorkspaceCreatedEvent;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.core.task.AsyncTaskExecutor;
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
    private CuratedPracticeRepository curatedPracticeRepository;

    @Mock
    private CuratedPracticeAreaRepository curatedAreaRepository;

    @Mock
    private PracticeCatalogInstallationRepository installationRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    private final AsyncTaskExecutor directExecutor = Runnable::run;

    @Test
    void shouldDoNothingWhenDisabled() {
        seeder(false, directExecutor).seed();

        verifyNoInteractions(workspaceRepository, curatedPracticeRepository, installationRepository);
    }

    @Test
    void shouldInstallEffectiveBundledDefinitionWithProvenance() {
        Workspace workspace = workspace(1L);
        CuratedPractice curated = curatedPractice();
        CuratedPracticeRevision revision = curated.getCurrentRevision();
        CuratedPracticeArea area = curatedArea();
        when(workspaceRepository.findAll()).thenReturn(List.of(workspace));
        when(curatedAreaRepository.findAllByOrderByDisplayOrderAscNameAsc()).thenReturn(List.of(area));
        when(curatedPracticeRepository.findInstallableBundledPractices()).thenReturn(List.of(curated));

        seeder(true, directExecutor).seed();

        verify(areaService).createArea(any(), eq("engineering"), any());
        ArgumentCaptor<PracticeDefinition> definition = ArgumentCaptor.forClass(PracticeDefinition.class);
        verify(practiceService).createPracticeFromCurated(
            any(),
            eq("review-failures"),
            definition.capture(),
            eq(curated),
            eq(revision)
        );
        assertThat(definition.getValue())
            .extracting(PracticeDefinition::name, PracticeDefinition::criteria, PracticeDefinition::areaSlug)
            .containsExactly("Review failures", "Evaluate failures", "engineering");
        verify(installationRepository).save(any());
    }

    @Test
    void shouldNotReinstallCompletedWorkspace() {
        when(workspaceRepository.findAll()).thenReturn(List.of(workspace(1L)));
        when(installationRepository.existsById(1L)).thenReturn(true);

        seeder(true, directExecutor).seed();

        verifyNoInteractions(curatedPracticeRepository, curatedAreaRepository, practiceService);
        verify(installationRepository, never()).save(any());
    }

    @Test
    void shouldNotRecordInstallationWhenCopyFails() {
        when(workspaceRepository.findAll()).thenReturn(List.of(workspace(1L)));
        CuratedPracticeArea area = curatedArea();
        CuratedPractice practice = curatedPractice();
        when(curatedAreaRepository.findAllByOrderByDisplayOrderAscNameAsc()).thenReturn(List.of(area));
        when(curatedPracticeRepository.findInstallableBundledPractices()).thenReturn(List.of(practice));
        when(practiceService.createPracticeFromCurated(any(), any(), any(), any(), any())).thenThrow(
            new RuntimeException("copy failed")
        );

        assertThatCode(() -> seeder(true, directExecutor).seed()).doesNotThrowAnyException();

        verify(installationRepository, never()).save(any());
    }

    @Test
    void shouldScheduleInstallationForCreatedWorkspace() {
        AsyncTaskExecutor executor = mock(AsyncTaskExecutor.class);
        when(workspaceRepository.findById(7L)).thenReturn(Optional.of(workspace(7L)));

        seeder(true, executor).onWorkspaceCreated(new WorkspaceCreatedEvent(7L, IntegrationKind.GITLAB));

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(task.capture());
        task.getValue().run();
        verify(installationRepository).save(any());
    }

    private DefaultPracticeCatalogSeeder seeder(boolean enabled, AsyncTaskExecutor executor) {
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
            curatedPracticeRepository,
            curatedAreaRepository,
            installationRepository,
            workspaceRepository,
            executor,
            TransactionOperations.withoutTransaction()
        );
    }

    private static CuratedPractice curatedPractice() {
        CuratedPracticeRevision revision = mock(CuratedPracticeRevision.class);
        when(revision.getName()).thenReturn("Review failures");
        when(revision.getArtifactType()).thenReturn(WorkArtifact.PULL_REQUEST);
        when(revision.getTriggerEvents()).thenReturn(TriggerEventsConverter.toJsonNode(List.of("PullRequestCreated")));
        when(revision.getCriteria()).thenReturn("Evaluate failures");
        when(revision.getAreaSlug()).thenReturn("engineering");
        CuratedPractice practice = mock(CuratedPractice.class);
        when(practice.getSlug()).thenReturn("review-failures");
        when(practice.getCurrentRevision()).thenReturn(revision);
        return practice;
    }

    private static CuratedPracticeArea curatedArea() {
        CuratedPracticeArea area = mock(CuratedPracticeArea.class);
        when(area.getSlug()).thenReturn("engineering");
        when(area.getName()).thenReturn("Engineering");
        when(area.getDisplayOrder()).thenReturn(1);
        return area;
    }

    private static Workspace workspace(long id) {
        Workspace workspace = new Workspace();
        workspace.setId(id);
        return workspace;
    }
}
