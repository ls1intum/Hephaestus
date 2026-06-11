package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.practices.dto.CreatePracticeRequestDTO;
import de.tum.cit.aet.hephaestus.practices.model.FocusArtifact;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import tools.jackson.databind.json.JsonMapper;

class DefaultPracticeCatalogSeederTest extends BaseUnitTest {

    @Mock
    private PracticeGoalService goalService;

    @Mock
    private PracticeService practiceService;

    @Mock
    private PracticeGoalRepository goalRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    private DefaultPracticeCatalogSeeder seeder(boolean enabled) {
        return new DefaultPracticeCatalogSeeder(
            enabled,
            JsonMapper.builder().build(),
            goalService,
            practiceService,
            goalRepository,
            workspaceRepository
        );
    }

    @Test
    void disabled_doesNothing() {
        seeder(false).seed();
        verifyNoInteractions(workspaceRepository, goalRepository, goalService, practiceService);
    }

    @Test
    void noWorkspace_skips() {
        when(workspaceRepository.findAll()).thenReturn(List.of());
        seeder(true).seed();
        verify(goalService, never()).createGoal(any(), any(), any(), any());
    }

    @Test
    void happyPath_seedsTheGroundedCatalog() {
        when(workspaceRepository.findAll()).thenReturn(List.of(new Workspace()));
        when(goalRepository.existsByWorkspaceIdAndSlug(any(), any())).thenReturn(false);

        seeder(true).seed();

        // The shipped catalog is three goals (review-ready-work, acting-on-review-feedback,
        // actionable-issue-authoring) with seven practices total, each bound to its goal.
        verify(goalService).createGoal(any(), eq("review-ready-work"), any(), any());
        verify(goalService).createGoal(any(), eq("acting-on-review-feedback"), any(), any());
        verify(goalService).createGoal(any(), eq("actionable-issue-authoring"), any(), any());
        verify(goalService, times(3)).createGoal(any(), any(), any(), any());

        var practiceCaptor = ArgumentCaptor.forClass(CreatePracticeRequestDTO.class);
        verify(practiceService, times(7)).createPractice(any(), practiceCaptor.capture());
        verify(goalService, times(7)).bindPractice(any(), any(), any());

        // Issue-focused practices are seeded with FocusArtifact.ISSUE — only possible because the
        // create DTO now carries focusArtifact (the configurability gap this PR closes).
        var foci = practiceCaptor.getAllValues().stream().map(CreatePracticeRequestDTO::focusArtifact).toList();
        assertThat(foci).contains(FocusArtifact.ISSUE, FocusArtifact.PULL_REQUEST);
        assertThat(
            foci
                .stream()
                .filter(f -> f == FocusArtifact.ISSUE)
                .count()
        ).isEqualTo(3);
    }

    @Test
    void idempotent_skipsGoalsThatAlreadyExist() {
        when(workspaceRepository.findAll()).thenReturn(List.of(new Workspace()));
        when(goalRepository.existsByWorkspaceIdAndSlug(any(), any())).thenReturn(true);

        seeder(true).seed();

        verify(goalService, never()).createGoal(any(), any(), any(), any());
        verify(practiceService, never()).createPractice(any(), any());
    }

    @Test
    void seedingFailure_isIsolatedAndDoesNotThrow() {
        when(workspaceRepository.findAll()).thenReturn(List.of(new Workspace()));
        when(goalRepository.existsByWorkspaceIdAndSlug(any(), any())).thenReturn(false);
        when(goalService.createGoal(any(), any(), any(), any())).thenThrow(new RuntimeException("boom"));

        assertThatCode(() -> seeder(true).seed()).doesNotThrowAnyException();
    }
}
