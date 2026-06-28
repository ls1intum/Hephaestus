package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

class PracticeAreaServiceTest extends BaseUnitTest {

    @Mock
    private PracticeAreaRepository practiceAreaRepository;

    @Mock
    private PracticeRepository practiceRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @InjectMocks
    private PracticeAreaService service;

    private static final WorkspaceContext CTX = new WorkspaceContext(
        1L,
        "acme",
        "Acme",
        AccountType.ORG,
        null,
        false,
        false,
        Set.of()
    );

    @Test
    void createArea_persistsWithFields() {
        when(practiceAreaRepository.existsByWorkspaceIdAndSlug(1L, "review-comms")).thenReturn(false);
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(new Workspace()));
        when(practiceAreaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PracticeArea created = service.createArea(
            CTX,
            "review-comms",
            new AreaAttributes("Effective review communication", "blurb", 0, "MessageSquareReply", "cyan")
        );

        assertThat(created.getSlug()).isEqualTo("review-comms");
        assertThat(created.getName()).isEqualTo("Effective review communication");
        assertThat(created.getDescription()).isEqualTo("blurb");
        assertThat(created.getIcon()).isEqualTo("MessageSquareReply");
        assertThat(created.getColor()).isEqualTo("cyan");
        assertThat(created.isActive()).isTrue();
        verify(practiceAreaRepository).save(any(PracticeArea.class));
    }

    @Test
    void createArea_duplicateSlug_throwsConflict() {
        when(practiceAreaRepository.existsByWorkspaceIdAndSlug(1L, "dup")).thenReturn(true);

        assertThatExceptionOfType(PracticeAreaSlugConflictException.class).isThrownBy(() ->
            service.createArea(CTX, "dup", new AreaAttributes("Dup", null, 0, null, null))
        );
        verify(practiceAreaRepository, never()).save(any());
    }

    @Test
    void updateArea_appliesPartialChanges() {
        PracticeArea area = new PracticeArea();
        area.setSlug("g");
        area.setName("Old");
        area.setDescription("original blurb");
        area.setColor("slate");
        area.setDisplayOrder(0);
        when(practiceAreaRepository.findByWorkspaceIdAndSlug(1L, "g")).thenReturn(Optional.of(area));
        when(practiceAreaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // null description means "keep", not "clear" (partial-PATCH semantics): supply null for description/color
        // and assert the supplied fields change while the omitted ones retain their original values.
        PracticeArea updated = service.updateArea(CTX, "g", new AreaAttributes("New", null, 5, "Eye", null));

        assertThat(updated.getName()).isEqualTo("New");
        assertThat(updated.getDisplayOrder()).isEqualTo(5);
        assertThat(updated.getIcon()).isEqualTo("Eye");
        assertThat(updated.getDescription()).isEqualTo("original blurb");
        assertThat(updated.getColor()).isEqualTo("slate");
    }

    @Test
    void getArea_missing_throwsNotFound() {
        when(practiceAreaRepository.findByWorkspaceIdAndSlug(1L, "nope")).thenReturn(Optional.empty());
        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> service.getArea(CTX, "nope"));
    }

    @Test
    void bindPractice_setsAreaWhenBothResolveInWorkspace() {
        Practice practice = new Practice();
        PracticeArea area = new PracticeArea();
        when(practiceRepository.findByWorkspaceIdAndSlug(1L, "p")).thenReturn(Optional.of(practice));
        when(practiceAreaRepository.findByWorkspaceIdAndSlug(1L, "g")).thenReturn(Optional.of(area));
        when(practiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Practice bound = service.bindPractice(CTX, "p", "g");

        assertThat(bound.getArea()).isSameAs(area);
    }

    @Test
    void bindPractice_nullAreaUnbinds() {
        Practice practice = new Practice();
        practice.setArea(new PracticeArea());
        when(practiceRepository.findByWorkspaceIdAndSlug(1L, "p")).thenReturn(Optional.of(practice));
        when(practiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Practice unbound = service.bindPractice(CTX, "p", null);

        assertThat(unbound.getArea()).isNull();
    }

    @Test
    void bindPractice_unresolvedArea_throwsNotFound() {
        // An area slug the workspace-scoped finder does not resolve → EntityNotFoundException. This unit
        // covers the not-found mapping only; it stubs the scoped finder and does not prove tenant isolation.
        when(practiceRepository.findByWorkspaceIdAndSlug(1L, "p")).thenReturn(Optional.of(new Practice()));
        when(practiceAreaRepository.findByWorkspaceIdAndSlug(1L, "foreign")).thenReturn(Optional.empty());

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() ->
            service.bindPractice(CTX, "p", "foreign")
        );
        verify(practiceRepository, never()).save(any());
    }

    private static PracticeArea area(String slug) {
        PracticeArea g = new PracticeArea();
        g.setSlug(slug);
        return g;
    }

    @Test
    void reorder_setsDisplayOrderByListIndex() {
        PracticeArea a = area("a");
        PracticeArea b = area("b");
        PracticeArea c = area("c");
        when(practiceAreaRepository.findByWorkspaceIdOrderByDisplayOrderAscNameAsc(1L)).thenReturn(List.of(a, b, c));

        service.reorder(CTX, List.of("c", "a", "b"));

        assertThat(c.getDisplayOrder()).isEqualTo(0);
        assertThat(a.getDisplayOrder()).isEqualTo(1);
        assertThat(b.getDisplayOrder()).isEqualTo(2);
        verify(practiceAreaRepository, times(3)).save(any());
    }

    @Test
    void reorder_unknownSlug_throws() {
        when(practiceAreaRepository.findByWorkspaceIdOrderByDisplayOrderAscNameAsc(1L)).thenReturn(
            List.of(area("a"), area("b"))
        );

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() ->
            service.reorder(CTX, List.of("ghost", "a", "b"))
        );
        verify(practiceAreaRepository, never()).save(any());
    }

    @Test
    void reorder_duplicateSlug_throwsAndWritesNothing() {
        // A duplicate slug would silently assign two display_order indices to one area — rejected up front.
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
            service.reorder(CTX, List.of("a", "a"))
        );
        verify(practiceAreaRepository, never()).save(any());
    }

    @Test
    void reorder_partialList_rejectsAndWritesNothing() {
        // The docstring promises a TOTAL ordering. A partial list (2 of 3 areas) would leave the omitted area
        // at a stale display_order that collides with the reassigned 0..n-1 indices — so an incomplete set is
        // rejected up front (400) and nothing is written, rather than silently producing a garbled order.
        when(practiceAreaRepository.findByWorkspaceIdOrderByDisplayOrderAscNameAsc(1L)).thenReturn(
            List.of(area("a"), area("b"), area("c"))
        );

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
            service.reorder(CTX, List.of("a", "b"))
        );
        verify(practiceAreaRepository, never()).save(any());
    }
}
