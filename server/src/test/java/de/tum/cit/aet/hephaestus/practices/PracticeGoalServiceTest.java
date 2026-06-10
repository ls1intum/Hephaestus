package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeGoal;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

class PracticeGoalServiceTest extends BaseUnitTest {

    @Mock
    private PracticeGoalRepository practiceGoalRepository;

    @Mock
    private PracticeRepository practiceRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @InjectMocks
    private PracticeGoalService service;

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
    void createGoal_persistsWithFields() {
        when(practiceGoalRepository.existsByWorkspaceIdAndSlug(1L, "review-comms")).thenReturn(false);
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(new Workspace()));
        when(practiceGoalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PracticeGoal created = service.createGoal(CTX, "review-comms", "Effective review communication", "blurb");

        assertThat(created.getSlug()).isEqualTo("review-comms");
        assertThat(created.getName()).isEqualTo("Effective review communication");
        assertThat(created.getDescription()).isEqualTo("blurb");
        assertThat(created.isActive()).isTrue();
        verify(practiceGoalRepository).save(any(PracticeGoal.class));
    }

    @Test
    void createGoal_duplicateSlug_throwsConflict() {
        when(practiceGoalRepository.existsByWorkspaceIdAndSlug(1L, "dup")).thenReturn(true);

        assertThatExceptionOfType(PracticeGoalSlugConflictException.class).isThrownBy(() ->
            service.createGoal(CTX, "dup", "Dup", null)
        );
        verify(practiceGoalRepository, never()).save(any());
    }

    @Test
    void updateGoal_appliesPartialChanges() {
        PracticeGoal goal = new PracticeGoal();
        goal.setSlug("g");
        goal.setName("Old");
        goal.setDisplayOrder(0);
        when(practiceGoalRepository.findByWorkspaceIdAndSlug(1L, "g")).thenReturn(Optional.of(goal));
        when(practiceGoalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PracticeGoal updated = service.updateGoal(CTX, "g", "New", null, 5);

        assertThat(updated.getName()).isEqualTo("New");
        assertThat(updated.getDisplayOrder()).isEqualTo(5);
    }

    @Test
    void getGoal_missing_throwsNotFound() {
        when(practiceGoalRepository.findByWorkspaceIdAndSlug(1L, "nope")).thenReturn(Optional.empty());
        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> service.getGoal(CTX, "nope"));
    }

    @Test
    void bindPractice_setsGoalWhenBothResolveInWorkspace() {
        Practice practice = new Practice();
        PracticeGoal goal = new PracticeGoal();
        when(practiceRepository.findByWorkspaceIdAndSlug(1L, "p")).thenReturn(Optional.of(practice));
        when(practiceGoalRepository.findByWorkspaceIdAndSlug(1L, "g")).thenReturn(Optional.of(goal));
        when(practiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Practice bound = service.bindPractice(CTX, "p", "g");

        assertThat(bound.getGoal()).isSameAs(goal);
    }

    @Test
    void bindPractice_nullGoalUnbinds() {
        Practice practice = new Practice();
        practice.setGoal(new PracticeGoal());
        when(practiceRepository.findByWorkspaceIdAndSlug(1L, "p")).thenReturn(Optional.of(practice));
        when(practiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Practice unbound = service.bindPractice(CTX, "p", null);

        assertThat(unbound.getGoal()).isNull();
    }

    @Test
    void bindPractice_goalInAnotherWorkspace_throwsNotFound() {
        // The goal is resolved scoped to CTX's workspace; a foreign goal slug returns empty → not found.
        when(practiceRepository.findByWorkspaceIdAndSlug(1L, "p")).thenReturn(Optional.of(new Practice()));
        when(practiceGoalRepository.findByWorkspaceIdAndSlug(1L, "foreign")).thenReturn(Optional.empty());

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() ->
            service.bindPractice(CTX, "p", "foreign")
        );
        verify(practiceRepository, never()).save(any());
    }
}
