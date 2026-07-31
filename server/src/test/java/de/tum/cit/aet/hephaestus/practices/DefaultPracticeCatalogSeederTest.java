package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.practices.dto.CreatePracticeRequestDTO;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.events.WorkspaceCreatedEvent;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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
    private PracticeCatalogInstallationRepository installationRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    private final AsyncTaskExecutor directExecutor = Runnable::run;

    private DefaultPracticeCatalogSeeder seeder(boolean enabled) {
        return seeder(enabled, directExecutor);
    }

    private DefaultPracticeCatalogSeeder seeder(boolean enabled, AsyncTaskExecutor executor) {
        lenient()
            .when(workspaceRepository.findByIdForUpdate(any()))
            .thenAnswer(invocation -> Optional.of(workspace(invocation.getArgument(0))));
        return new DefaultPracticeCatalogSeeder(
            enabled,
            JsonMapper.builder().build(),
            areaService,
            practiceService,
            areaRepository,
            practiceRepository,
            installationRepository,
            workspaceRepository,
            executor,
            TransactionOperations.withoutTransaction()
        );
    }

    @Test
    void disabled_doesNothing() {
        seeder(false).seed();
        verifyNoInteractions(workspaceRepository, installationRepository, areaRepository, areaService, practiceService);
    }

    @Test
    void noWorkspace_skips() {
        when(workspaceRepository.findAll()).thenReturn(List.of());
        seeder(true).seed();
        verify(areaService, never()).createArea(any(), any(), any());
    }

    @Test
    void happyPath_seedsTheGroundedCatalog() {
        when(workspaceRepository.findAll()).thenReturn(List.of(workspace(1L)));
        when(areaRepository.existsByWorkspaceIdAndSlug(any(), any())).thenReturn(false);

        seeder(true).seed();

        verify(areaService).createArea(any(), eq("review-ready-work"), any());
        verify(areaService).createArea(any(), eq("acting-on-review-feedback"), any());
        verify(areaService).createArea(any(), eq("actionable-issue-authoring"), any());
        verify(areaService).createArea(any(), eq("constructive-code-review"), any());
        verify(areaService).createArea(any(), eq("testing-discipline"), any());
        verify(areaService).createArea(any(), eq("communication"), any());
        verify(areaService, times(12)).createArea(any(), any(), any());

        var practiceCaptor = ArgumentCaptor.forClass(CreatePracticeRequestDTO.class);
        verify(practiceService, times(37)).createPractice(any(), practiceCaptor.capture());
        verify(installationRepository).save(any());
        verify(areaService, never()).bindPractice(any(), any(), any());
        assertThat(practiceCaptor.getAllValues()).allSatisfy(request -> assertThat(request.areaSlug()).isNotBlank());

        var foci = practiceCaptor.getAllValues().stream().map(CreatePracticeRequestDTO::artifactType).toList();
        assertThat(foci).contains(WorkArtifact.ISSUE, WorkArtifact.PULL_REQUEST, WorkArtifact.CONVERSATION_THREAD);
        assertThat(
            foci
                .stream()
                .filter(f -> f == WorkArtifact.ISSUE)
                .count()
        ).isEqualTo(7);
        assertThat(
            foci
                .stream()
                .filter(f -> f == WorkArtifact.CONVERSATION_THREAD)
                .count()
        ).isEqualTo(3);

        for (var request : practiceCaptor.getAllValues()) {
            int fence = request.criteria().indexOf("\n\n---\n\n");
            assertThat(fence).as("preamble fenced ahead of the practice criteria").isGreaterThan(40);
            String preamble = request.criteria().substring(0, fence);
            String expectedFocusWord = switch (request.artifactType()) {
                case ISSUE -> "issue";
                case CONVERSATION_THREAD -> "conversation";
                case PULL_REQUEST -> "pull request";
            };
            assertThat(preamble).containsIgnoringCase(expectedFocusWord);
        }
    }

    @Test
    void startup_installsCatalogForUnmarkedWorkspaces() {
        Workspace first = new Workspace();
        first.setId(1L);
        Workspace second = new Workspace();
        second.setId(2L);
        when(workspaceRepository.findAll()).thenReturn(List.of(second, first));
        when(areaRepository.existsByWorkspaceIdAndSlug(any(), any())).thenReturn(false);

        seeder(true).seed();

        verify(areaService, times(24)).createArea(any(), any(), any());
        verify(practiceService, times(74)).createPractice(any(), any());
        verify(installationRepository, times(2)).save(any());
    }

    @Test
    void existingCatalog_isRecordedWithoutChangingRows() {
        Practice bound = new Practice();
        bound.setArea(new PracticeArea());
        when(workspaceRepository.findAll()).thenReturn(List.of(workspace(1L)));
        when(areaRepository.existsByWorkspaceIdAndSlug(any(), any())).thenReturn(true);
        when(practiceRepository.findByWorkspaceIdAndSlug(any(), any())).thenReturn(Optional.of(bound));

        seeder(true).seed();

        verify(areaService, never()).createArea(any(), any(), any());
        verify(practiceService, never()).createPractice(any(), any());
        verify(areaService, never()).bindPractice(any(), any(), any());
        verify(installationRepository).save(any());
    }

    @Test
    void initialInstall_addsMissingPracticesToExistingAreas() {
        when(workspaceRepository.findAll()).thenReturn(List.of(workspace(1L)));
        when(areaRepository.existsByWorkspaceIdAndSlug(any(), any())).thenReturn(true);
        when(practiceRepository.findByWorkspaceIdAndSlug(any(), any())).thenReturn(Optional.empty());

        seeder(true).seed();

        verify(areaService, never()).createArea(any(), any(), any());
        verify(practiceService, times(37)).createPractice(any(), any());
        verify(areaService, never()).bindPractice(any(), any(), any());
        verify(installationRepository).save(any());
    }

    @Test
    void existingUnassignedPractice_isLeftUntouched() {
        Practice unbound = new Practice();
        when(workspaceRepository.findAll()).thenReturn(List.of(workspace(1L)));
        when(areaRepository.existsByWorkspaceIdAndSlug(any(), any())).thenReturn(true);
        when(practiceRepository.findByWorkspaceIdAndSlug(any(), any())).thenReturn(Optional.of(unbound));

        seeder(true).seed();

        verify(practiceService, never()).createPractice(any(), any());
        verify(areaService, never()).bindPractice(any(), any(), any());
        verify(installationRepository).save(any());
    }

    @Test
    void failedInstall_doesNotRecordCompletion() {
        when(workspaceRepository.findAll()).thenReturn(List.of(workspace(1L)));
        when(areaRepository.existsByWorkspaceIdAndSlug(any(), any())).thenReturn(false);
        when(practiceRepository.findByWorkspaceIdAndSlug(any(), any())).thenReturn(Optional.empty());
        when(practiceService.createPractice(any(), any())).thenThrow(new RuntimeException("malformed artifactType"));

        assertThatCode(() -> seeder(true).seed()).doesNotThrowAnyException();

        verify(practiceService).createPractice(any(), any());
        verify(installationRepository, never()).save(any());
    }

    @Test
    void completedInstall_doesNotChangeCatalogAgain() {
        Workspace workspace = workspace(1L);
        when(workspaceRepository.findAll()).thenReturn(List.of(workspace));
        when(installationRepository.existsById(1L)).thenReturn(true);

        seeder(true).seed();

        verifyNoInteractions(areaRepository, practiceRepository, areaService, practiceService);
        verify(installationRepository, never()).save(any());
    }

    @Test
    void onWorkspaceCreated_dispatchesCatalogSeeding() {
        AsyncTaskExecutor executor = org.mockito.Mockito.mock(AsyncTaskExecutor.class);
        when(workspaceRepository.findById(7L)).thenReturn(Optional.of(workspace(7L)));
        when(areaRepository.existsByWorkspaceIdAndSlug(any(), any())).thenReturn(false);
        when(practiceRepository.findByWorkspaceIdAndSlug(any(), any())).thenReturn(Optional.empty());

        seeder(true, executor).onWorkspaceCreated(new WorkspaceCreatedEvent(7L, IntegrationKind.GITLAB));

        var task = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(task.capture());
        verifyNoInteractions(workspaceRepository, areaService, practiceService);

        task.getValue().run();

        verify(areaService, times(12)).createArea(any(), any(), any());
        verify(practiceService, times(37)).createPractice(any(), any());
        verify(areaService, never()).bindPractice(any(), any(), any());
        verify(installationRepository).save(any());
    }

    @Test
    void onWorkspaceCreated_noOpsWhenWorkspaceRowIsGone() {
        when(workspaceRepository.findById(any())).thenReturn(Optional.empty());

        seeder(true).onWorkspaceCreated(new WorkspaceCreatedEvent(99L, IntegrationKind.GITLAB));

        verify(areaService, never()).createArea(any(), any(), any());
        verify(practiceService, never()).createPractice(any(), any());
    }

    @Test
    void onWorkspaceCreated_noOpsWhenDisabled() {
        seeder(false).onWorkspaceCreated(new WorkspaceCreatedEvent(7L, IntegrationKind.GITLAB));

        verifyNoInteractions(workspaceRepository, areaService, practiceService);
    }

    @Test
    void onWorkspaceCreated_containsExecutorRejection() {
        AsyncTaskExecutor executor = org.mockito.Mockito.mock(AsyncTaskExecutor.class);
        doThrow(new RuntimeException("queue full")).when(executor).execute(any());

        assertThatCode(() ->
            seeder(true, executor).onWorkspaceCreated(new WorkspaceCreatedEvent(7L, IntegrationKind.GITLAB))
        ).doesNotThrowAnyException();
    }

    @Test
    void seedingFailure_isIsolatedAndDoesNotThrow() {
        when(workspaceRepository.findAll()).thenReturn(List.of(workspace(1L)));
        when(areaRepository.existsByWorkspaceIdAndSlug(any(), any())).thenReturn(false);
        when(areaService.createArea(any(), any(), any())).thenThrow(new RuntimeException("boom"));

        assertThatCode(() -> seeder(true).seed()).doesNotThrowAnyException();
    }

    @Test
    void workspaceLookupFailureAtBoot_isIsolatedAndDoesNotThrow() {
        when(workspaceRepository.findAll()).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> seeder(true).seed()).doesNotThrowAnyException();
    }

    @Test
    void onWorkspaceCreated_lookupFailure_isContainedInsideTheTask() {
        when(workspaceRepository.findById(any())).thenThrow(new RuntimeException("db down"));

        assertThatCode(() ->
            seeder(true).onWorkspaceCreated(new WorkspaceCreatedEvent(7L, IntegrationKind.GITLAB))
        ).doesNotThrowAnyException();
    }

    @Test
    void shippedCatalogLearnerCopy_carriesNoDetectorVocab() {
        var detectorVocab = Pattern.compile("\\b(?:PRESENT|ABSENT|GOOD|BAD|NOT_APPLICABLE)\\b");
        JsonNode catalog = JsonMapper.builder()
            .build()
            .readTree(getClass().getClassLoader().getResourceAsStream("practices/default-catalog.json"));
        for (JsonNode area : catalog.path("areas")) {
            for (JsonNode practice : area.path("practices")) {
                String slug = practice.path("slug").asString();
                for (String field : List.of("whyItMatters", "whatGoodLooksLike")) {
                    assertThat(detectorVocab.matcher(practice.path(field).asString()).find())
                        .as("learner-facing %s for '%s' must not contain detector vocabulary", field, slug)
                        .isFalse();
                }
            }
        }
    }

    @Test
    void shippedCatalogCriteria_useRealNewlinesNotLiteralEscapes() {
        JsonNode catalog = JsonMapper.builder()
            .build()
            .readTree(getClass().getClassLoader().getResourceAsStream("practices/default-catalog.json"));
        for (JsonNode area : catalog.path("areas")) {
            for (JsonNode practice : area.path("practices")) {
                assertThat(practice.path("criteria").asString())
                    .as(
                        "criteria for '%s' must use real newlines, not a literal backslash-n",
                        practice.path("slug").asString()
                    )
                    .doesNotContain("\\n");
            }
        }
    }

    private static Workspace workspace(long id) {
        Workspace workspace = new Workspace();
        workspace.setId(id);
        return workspace;
    }
}
