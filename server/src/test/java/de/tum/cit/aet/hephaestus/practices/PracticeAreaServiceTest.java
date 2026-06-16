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

        PracticeArea created = service.createArea(CTX, "review-comms", "Effective review communication", "blurb", 0);

        assertThat(created.getSlug()).isEqualTo("review-comms");
        assertThat(created.getName()).isEqualTo("Effective review communication");
        assertThat(created.getDescription()).isEqualTo("blurb");
        assertThat(created.isActive()).isTrue();
        verify(practiceAreaRepository).save(any(PracticeArea.class));
    }

    @Test
    void createArea_duplicateSlug_throwsConflict() {
        when(practiceAreaRepository.existsByWorkspaceIdAndSlug(1L, "dup")).thenReturn(true);

        assertThatExceptionOfType(PracticeAreaSlugConflictException.class).isThrownBy(() ->
            service.createArea(CTX, "dup", "Dup", null, 0)
        );
        verify(practiceAreaRepository, never()).save(any());
    }

    @Test
    void updateArea_appliesPartialChanges() {
        PracticeArea area = new PracticeArea();
        area.setSlug("g");
        area.setName("Old");
        area.setDisplayOrder(0);
        when(practiceAreaRepository.findByWorkspaceIdAndSlug(1L, "g")).thenReturn(Optional.of(area));
        when(practiceAreaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PracticeArea updated = service.updateArea(CTX, "g", "New", null, 5);

        assertThat(updated.getName()).isEqualTo("New");
        assertThat(updated.getDisplayOrder()).isEqualTo(5);
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
        // A area slug the workspace-scoped finder does not resolve → EntityNotFoundException. This unit
        // covers the not-found mapping only; it stubs the scoped finder and does not prove tenant isolation.
        when(practiceRepository.findByWorkspaceIdAndSlug(1L, "p")).thenReturn(Optional.of(new Practice()));
        when(practiceAreaRepository.findByWorkspaceIdAndSlug(1L, "foreign")).thenReturn(Optional.empty());

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() ->
            service.bindPractice(CTX, "p", "foreign")
        );
        verify(practiceRepository, never()).save(any());
    }

    @Test
    void reorder_setsDisplayOrderByListIndex() {
        PracticeArea a = new PracticeArea();
        a.setSlug("a");
        PracticeArea b = new PracticeArea();
        b.setSlug("b");
        PracticeArea c = new PracticeArea();
        c.setSlug("c");
        when(practiceAreaRepository.findByWorkspaceIdAndSlug(1L, "c")).thenReturn(Optional.of(c));
        when(practiceAreaRepository.findByWorkspaceIdAndSlug(1L, "a")).thenReturn(Optional.of(a));
        when(practiceAreaRepository.findByWorkspaceIdAndSlug(1L, "b")).thenReturn(Optional.of(b));

        service.reorder(CTX, List.of("c", "a", "b"));

        assertThat(c.getDisplayOrder()).isEqualTo(0);
        assertThat(a.getDisplayOrder()).isEqualTo(1);
        assertThat(b.getDisplayOrder()).isEqualTo(2);
        verify(practiceAreaRepository, times(3)).save(any());
    }

    @Test
    void reorder_unknownSlug_throws() {
        when(practiceAreaRepository.findByWorkspaceIdAndSlug(1L, "ghost")).thenReturn(Optional.empty());

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() ->
            service.reorder(CTX, List.of("ghost"))
        );
    }
}
