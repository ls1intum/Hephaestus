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

        // The shipped catalog is eleven goals with twenty-eight practices total, each bound to its goal.
        verify(goalService).createGoal(any(), eq("review-ready-work"), any(), any());
        verify(goalService).createGoal(any(), eq("acting-on-review-feedback"), any(), any());
        verify(goalService).createGoal(any(), eq("actionable-issue-authoring"), any(), any());
        verify(goalService).createGoal(any(), eq("constructive-code-review"), any(), any());
        verify(goalService).createGoal(any(), eq("testing-discipline"), any(), any());
        verify(goalService, times(11)).createGoal(any(), any(), any(), any());

        var practiceCaptor = ArgumentCaptor.forClass(CreatePracticeRequestDTO.class);
        verify(practiceService, times(30)).createPractice(any(), practiceCaptor.capture());
        verify(goalService, times(30)).bindPractice(any(), any(), any());

        // 5 of the 30 practices are issue-focused and must seed with WorkArtifact.ISSUE.
        var foci = practiceCaptor.getAllValues().stream().map(CreatePracticeRequestDTO::focusArtifact).toList();
        assertThat(foci).contains(WorkArtifact.ISSUE, WorkArtifact.PULL_REQUEST);
        assertThat(
            foci
                .stream()
                .filter(f -> f == WorkArtifact.ISSUE)
                .count()
        ).isEqualTo(5);

        // Every seeded practice's criteria is the per-focus evidence-contract preamble composed onto the
        // practice-specific criteria with a "\n\n---\n\n" fence: a non-empty preamble before the fence,
        // naming the practice's focus. Structural (not exact-prose) so rewording a preamble can't break it.
        for (var request : practiceCaptor.getAllValues()) {
            int fence = request.criteria().indexOf("\n\n---\n\n");
            assertThat(fence).as("preamble fenced ahead of the practice criteria").isGreaterThan(40);
            String preamble = request.criteria().substring(0, fence);
            assertThat(preamble).containsIgnoringCase(
                request.focusArtifact() == WorkArtifact.ISSUE ? "issue" : "pull request"
            );
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
