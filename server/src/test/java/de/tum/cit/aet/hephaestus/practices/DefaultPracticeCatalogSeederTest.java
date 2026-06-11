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
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
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

        // Issue-focused practices are seeded with WorkArtifact.ISSUE — only possible because the
        // create DTO now carries focusArtifact (the configurability gap this PR closes).
        var foci = practiceCaptor.getAllValues().stream().map(CreatePracticeRequestDTO::focusArtifact).toList();
        assertThat(foci).contains(WorkArtifact.ISSUE, WorkArtifact.PULL_REQUEST);
        assertThat(
            foci
                .stream()
                .filter(f -> f == WorkArtifact.ISSUE)
                .count()
        ).isEqualTo(3);

        // Every seeded practice's criteria is the per-focus evidence-contract preamble composed onto the
        // practice-specific criteria with a "\n\n---\n\n" fence — so the shared contract is authored once
        // and inherited, while the validated practice text is preserved verbatim after the fence.
        for (var request : practiceCaptor.getAllValues()) {
            assertThat(request.criteria()).contains("\n\n---\n\n");
            if (request.focusArtifact() == WorkArtifact.ISSUE) {
                assertThat(request.criteria()).startsWith(
                    "You are a formative-feedback reviewer assessing a single software-practice habit from an issue."
                );
            } else {
                assertThat(request.criteria()).startsWith(
                    "You are a formative-feedback reviewer assessing a single software-practice habit from a pull request."
                );
            }
        }
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
