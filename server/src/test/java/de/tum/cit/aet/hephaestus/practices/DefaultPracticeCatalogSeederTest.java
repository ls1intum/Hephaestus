package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import tools.jackson.databind.JsonNode;
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
        verify(goalService, never()).createGoal(any(), any(), any(), any(), anyInt());
    }

    @Test
    void happyPath_seedsTheGroundedCatalog() {
        when(workspaceRepository.findAll()).thenReturn(List.of(new Workspace()));
        when(goalRepository.existsByWorkspaceIdAndSlug(any(), any())).thenReturn(false);

        seeder(true).seed();

        // The shipped catalog is eleven goals with thirty-two practices total, each bound to its goal.
        verify(goalService).createGoal(any(), eq("review-ready-work"), any(), any(), anyInt());
        verify(goalService).createGoal(any(), eq("acting-on-review-feedback"), any(), any(), anyInt());
        verify(goalService).createGoal(any(), eq("actionable-issue-authoring"), any(), any(), anyInt());
        verify(goalService).createGoal(any(), eq("constructive-code-review"), any(), any(), anyInt());
        verify(goalService).createGoal(any(), eq("testing-discipline"), any(), any(), anyInt());
        verify(goalService, times(11)).createGoal(any(), any(), any(), any(), anyInt());

        var practiceCaptor = ArgumentCaptor.forClass(CreatePracticeRequestDTO.class);
        verify(practiceService, times(32)).createPractice(any(), practiceCaptor.capture());
        verify(goalService, times(32)).bindPractice(any(), any(), any());

        // 6 of the 32 practices are issue-focused and must seed with WorkArtifact.ISSUE.
        var foci = practiceCaptor.getAllValues().stream().map(CreatePracticeRequestDTO::focusArtifact).toList();
        assertThat(foci).contains(WorkArtifact.ISSUE, WorkArtifact.PULL_REQUEST);
        assertThat(
            foci
                .stream()
                .filter(f -> f == WorkArtifact.ISSUE)
                .count()
        ).isEqualTo(6);

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

        verify(goalService, never()).createGoal(any(), any(), any(), any(), anyInt());
        verify(practiceService, never()).createPractice(any(), any());
    }

    @Test
    void seedingFailure_isIsolatedAndDoesNotThrow() {
        when(workspaceRepository.findAll()).thenReturn(List.of(new Workspace()));
        when(goalRepository.existsByWorkspaceIdAndSlug(any(), any())).thenReturn(false);
        when(goalService.createGoal(any(), any(), any(), any(), anyInt())).thenThrow(new RuntimeException("boom"));

        assertThatCode(() -> seeder(true).seed()).doesNotThrowAnyException();
    }

    @Test
    void shippedCatalogCriteria_useRealNewlinesNotLiteralEscapes() {
        // Regression guard (2026-06-13): 21/32 criteria once shipped with a literal two-char "\n" instead of
        // a real newline, collapsing every "## Section" of the judge prompt onto one line. The injector
        // writes criteria verbatim into the judge's context, so this must be caught at the source.
        JsonNode catalog = JsonMapper.builder()
            .build()
            .readTree(getClass().getClassLoader().getResourceAsStream("practices/default-catalog.json"));
        for (JsonNode goal : catalog.path("goals")) {
            for (JsonNode practice : goal.path("practices")) {
                assertThat(practice.path("criteria").asString())
                    .as(
                        "criteria for '%s' must use real newlines, not a literal backslash-n",
                        practice.path("slug").asString()
                    )
                    .doesNotContain("\\n");
            }
        }
    }
}
