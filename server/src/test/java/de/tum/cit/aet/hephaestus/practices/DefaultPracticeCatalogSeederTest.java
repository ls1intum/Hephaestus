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
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
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
    private WorkspaceRepository workspaceRepository;

    private DefaultPracticeCatalogSeeder seeder(boolean enabled) {
        return new DefaultPracticeCatalogSeeder(
            enabled,
            JsonMapper.builder().build(),
            areaService,
            practiceService,
            areaRepository,
            practiceRepository,
            workspaceRepository
        );
    }

    @Test
    void disabled_doesNothing() {
        seeder(false).seed();
        verifyNoInteractions(workspaceRepository, areaRepository, areaService, practiceService);
    }

    @Test
    void noWorkspace_skips() {
        when(workspaceRepository.findAll()).thenReturn(List.of());
        seeder(true).seed();
        verify(areaService, never()).createArea(any(), any(), any());
    }

    @Test
    void happyPath_seedsTheGroundedCatalog() {
        when(workspaceRepository.findAll()).thenReturn(List.of(new Workspace()));
        when(areaRepository.existsByWorkspaceIdAndSlug(any(), any())).thenReturn(false);

        seeder(true).seed();

        // The shipped catalog is eleven areas with thirty-two practices total, each bound to its area.
        verify(areaService).createArea(any(), eq("review-ready-work"), any());
        verify(areaService).createArea(any(), eq("acting-on-review-feedback"), any());
        verify(areaService).createArea(any(), eq("actionable-issue-authoring"), any());
        verify(areaService).createArea(any(), eq("constructive-code-review"), any());
        verify(areaService).createArea(any(), eq("testing-discipline"), any());
        verify(areaService, times(11)).createArea(any(), any(), any());

        var practiceCaptor = ArgumentCaptor.forClass(CreatePracticeRequestDTO.class);
        verify(practiceService, times(32)).createPractice(any(), practiceCaptor.capture());
        verify(areaService, times(32)).bindPractice(any(), any(), any());

        // 6 of the 32 practices are issue-focused and must seed with WorkArtifact.ISSUE.
        var foci = practiceCaptor.getAllValues().stream().map(CreatePracticeRequestDTO::artifactType).toList();
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
                request.artifactType() == WorkArtifact.ISSUE ? "issue" : "pull request"
            );
        }
    }

    @Test
    void idempotent_skipsAreasAndPracticesThatAlreadyExistAndAreBound() {
        // An already-present practice that is already bound to an area must be left untouched (respect admin edits).
        Practice bound = new Practice();
        bound.setArea(new PracticeArea());
        when(workspaceRepository.findAll()).thenReturn(List.of(new Workspace()));
        when(areaRepository.existsByWorkspaceIdAndSlug(any(), any())).thenReturn(true);
        when(practiceRepository.findByWorkspaceIdAndSlug(any(), any())).thenReturn(Optional.of(bound));

        seeder(true).seed();

        verify(areaService, never()).createArea(any(), any(), any());
        verify(practiceService, never()).createPractice(any(), any());
        verify(areaService, never()).bindPractice(any(), any(), any());
    }

    @Test
    void perRowIdempotent_resumesMissingPracticesWhenAreaAlreadyExists() {
        // Regression: a mid-area failure can leave the area created but only some practices seeded. A per-AREA
        // skip would permanently strand the rest; per-ROW idempotency must still seed the practices that are
        // absent even though their area already exists.
        when(workspaceRepository.findAll()).thenReturn(List.of(new Workspace()));
        when(areaRepository.existsByWorkspaceIdAndSlug(any(), any())).thenReturn(true);
        when(practiceRepository.findByWorkspaceIdAndSlug(any(), any())).thenReturn(Optional.empty());

        seeder(true).seed();

        // No area is re-created (all present), but every absent practice is still seeded and bound.
        verify(areaService, never()).createArea(any(), any(), any());
        verify(practiceService, times(32)).createPractice(any(), any());
        verify(areaService, times(32)).bindPractice(any(), any(), any());
    }

    @Test
    void resumable_bindsAStrandedPracticeThatExistsButIsUnbound() {
        // C4 root cause: createPractice and bindPractice run in SEPARATE transactions, so a mid-seed failure
        // can leave a practice committed with area=NULL. A plain exists-then-skip guard would strand it forever.
        // On the next boot the seeder must re-bind the unbound practice WITHOUT re-creating it.
        Practice unbound = new Practice(); // area defaults to null
        when(workspaceRepository.findAll()).thenReturn(List.of(new Workspace()));
        when(areaRepository.existsByWorkspaceIdAndSlug(any(), any())).thenReturn(true);
        when(practiceRepository.findByWorkspaceIdAndSlug(any(), any())).thenReturn(Optional.of(unbound));

        seeder(true).seed();

        // Never re-create an existing practice, but bind each unbound one to its catalog area.
        verify(practiceService, never()).createPractice(any(), any());
        verify(areaService, times(32)).bindPractice(any(), any(), any());
    }

    @Test
    void perRowFailure_skipsTheBadRowButSeedsTheRest() {
        // Per-ROW resilience: one practice that fails (e.g. a malformed artifactType making createPractice
        // throw) must skip ONLY that row, not abort the remaining catalog. With 32 practices, a single
        // throwing createPractice still leaves 31 created and bound.
        when(workspaceRepository.findAll()).thenReturn(List.of(new Workspace()));
        when(areaRepository.existsByWorkspaceIdAndSlug(any(), any())).thenReturn(false);
        when(practiceRepository.findByWorkspaceIdAndSlug(any(), any())).thenReturn(Optional.empty());
        // First createPractice throws; all subsequent calls succeed.
        when(practiceService.createPractice(any(), any()))
            .thenThrow(new RuntimeException("malformed artifactType"))
            .thenReturn(new Practice());

        assertThatCode(() -> seeder(true).seed()).doesNotThrowAnyException();

        // All 32 are attempted; the 31 that did not throw are bound (the failed row never reaches bindPractice).
        verify(practiceService, times(32)).createPractice(any(), any());
        verify(areaService, times(31)).bindPractice(any(), any(), any());
    }

    @Test
    void seedingFailure_isIsolatedAndDoesNotThrow() {
        when(workspaceRepository.findAll()).thenReturn(List.of(new Workspace()));
        when(areaRepository.existsByWorkspaceIdAndSlug(any(), any())).thenReturn(false);
        when(areaService.createArea(any(), any(), any())).thenThrow(new RuntimeException("boom"));

        assertThatCode(() -> seeder(true).seed()).doesNotThrowAnyException();
    }

    @Test
    void shippedCatalogCriteria_useRealNewlinesNotLiteralEscapes() {
        // The injector writes criteria verbatim into the judge's context, so a literal two-char "\n" (instead
        // of a real newline) would collapse every "## Section" of the judge prompt onto one line. Catch it at
        // the source.
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
}
