package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntry;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

class PracticeAreaServiceTest extends BaseUnitTest {

    @Mock
    private PracticeAreaRepository practiceAreaRepository;

    @Mock
    private PracticeRepository practiceRepository;

    @Mock
    private PracticeRevisionService practiceRevisionService;

    @Mock
    private ConfigAuditPort configAudit;

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

    @BeforeEach
    void mockWorkspaceLock() {
        lenient().when(workspaceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(new Workspace()));
        PracticeRevision revision = mock(PracticeRevision.class);
        lenient().when(revision.getRevisionNumber()).thenReturn(1);
        lenient().when(practiceRevisionService.append(any())).thenReturn(revision);
    }

    @Test
    void createArea_persistsWithFields() {
        when(practiceAreaRepository.existsByWorkspaceIdAndSlug(1L, "review-comms")).thenReturn(false);
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
        assertThat(created.isVisibleInPracticeDashboards()).isTrue();
        verify(practiceAreaRepository).save(any(PracticeArea.class));
    }

    @Test
    void createArea_recordsItsConfiguration() {
        when(practiceAreaRepository.save(any())).thenAnswer(invocation -> {
            PracticeArea area = invocation.getArgument(0);
            area.setId(9L);
            return area;
        });

        service.createArea(CTX, "review-comms", new AreaAttributes("Review communication", null, 0, null, null));

        ConfigAuditEntry entry = capturedAuditEntry();
        assertThat(entry.entityType()).isEqualTo(ConfigAuditEntityType.PRACTICE_AREA);
        assertThat(entry.entityId()).isEqualTo("9");
        assertThat(entry.workspaceId()).isEqualTo(1L);
        assertThat(entry.before()).isNull();
        assertThat(entry.after()).isEqualTo(
            new PracticeAreaSnapshot("review-comms", "Review communication", null, true, null, null, null)
        );
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
    void createArea_appendsWhenOrderIsOmitted() {
        when(practiceAreaRepository.findMaxDisplayOrder(1L)).thenReturn(3);
        when(practiceAreaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PracticeArea created = service.createArea(
            CTX,
            "new-area",
            new AreaAttributes("New area", null, null, null, null)
        );

        assertThat(created.getDisplayOrder()).isEqualTo(4);
    }

    @Test
    void updateArea_appliesPartialChanges() {
        PracticeArea area = new PracticeArea();
        area.setId(7L);
        area.setSlug("g");
        area.setName("Old");
        area.setDescription("original blurb");
        area.setColor("slate");
        area.setDisplayOrder(0);
        when(practiceAreaRepository.findByWorkspaceIdAndSlug(1L, "g")).thenReturn(Optional.of(area));
        when(practiceAreaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PracticeArea updated = service.updateArea(CTX, "g", new AreaAttributes("New", null, 5, "Eye", null));

        assertThat(updated.getName()).isEqualTo("New");
        assertThat(updated.getDisplayOrder()).isEqualTo(5);
        assertThat(updated.getIcon()).isEqualTo("Eye");
        assertThat(updated.getDescription()).isEqualTo("original blurb");
        assertThat(updated.getColor()).isEqualTo("slate");
    }

    @Test
    void updateArea_recordsBeforeAndAfter() {
        PracticeArea area = area("guidance");
        area.setId(7L);
        area.setName("Old");
        area.setVisibleInPracticeDashboards(true);
        when(practiceAreaRepository.findByWorkspaceIdAndSlug(1L, "guidance")).thenReturn(Optional.of(area));
        when(practiceAreaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateArea(CTX, "guidance", new AreaAttributes("New", null, null, null, null), false);

        ConfigAuditEntry entry = capturedAuditEntry();
        assertThat(entry.entityType()).isEqualTo(ConfigAuditEntityType.PRACTICE_AREA);
        assertThat(entry.before()).isEqualTo(new PracticeAreaSnapshot("guidance", "Old", null, true, null, null, null));
        assertThat(entry.after()).isEqualTo(new PracticeAreaSnapshot("guidance", "New", null, false, null, null, null));
    }

    @Test
    void updateArea_appendsRevisionsWhenSnapshotAttributesChange() {
        PracticeArea area = area("g");
        area.setId(7L);
        area.setName("Old");
        Practice first = new Practice();
        first.setId(1L);
        Practice second = new Practice();
        second.setId(2L);
        when(practiceAreaRepository.findByWorkspaceIdAndSlug(1L, "g")).thenReturn(Optional.of(area));
        when(practiceAreaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(practiceRepository.findByWorkspaceIdAndAreaIdOrderByDisplayOrderAscNameAsc(1L, 7L)).thenReturn(
            List.of(first, second)
        );

        service.updateArea(CTX, "g", new AreaAttributes("New", null, null, null, null));

        verify(practiceRevisionService).append(first);
        verify(practiceRevisionService).append(second);
    }

    @Test
    void getArea_missing_throwsNotFound() {
        when(practiceAreaRepository.findByWorkspaceIdAndSlug(1L, "nope")).thenReturn(Optional.empty());
        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> service.getArea(CTX, "nope"));
    }

    @Test
    void bindPractice_appendsToTheDestinationArea() {
        PracticeArea destination = area("destination");
        destination.setId(20L);
        Practice moved = practice("moved");
        when(practiceRepository.findByWorkspaceIdAndSlug(1L, "moved")).thenReturn(Optional.of(moved));
        when(practiceAreaRepository.findByWorkspaceIdAndSlug(1L, "destination")).thenReturn(Optional.of(destination));
        when(practiceRepository.findMaxDisplayOrder(1L, 20L)).thenReturn(4);
        when(practiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Practice bound = service.bindPractice(CTX, "moved", "destination");

        assertThat(bound).extracting(Practice::getArea, Practice::getDisplayOrder).containsExactly(destination, 5);
        verify(practiceRevisionService).append(bound);
    }

    @Test
    void bindPractice_nullAreaUnbinds() {
        Practice practice = practice("p");
        PracticeArea currentArea = new PracticeArea();
        currentArea.setId(10L);
        practice.setArea(currentArea);
        when(practiceRepository.findByWorkspaceIdAndSlug(1L, "p")).thenReturn(Optional.of(practice));
        when(practiceRepository.findMaxDisplayOrder(1L, null)).thenReturn(6);
        when(practiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Practice unbound = service.bindPractice(CTX, "p", null);

        assertThat(unbound).extracting(Practice::getArea, Practice::getDisplayOrder).containsExactly(null, 7);
        verify(practiceRevisionService).append(unbound);
    }

    @Test
    void bindPractice_sameAreaDoesNotWrite() {
        PracticeArea currentArea = area("current");
        currentArea.setId(10L);
        PracticeArea resolvedArea = area("current");
        resolvedArea.setId(10L);
        Practice practice = practice("p");
        practice.setArea(currentArea);
        when(practiceRepository.findByWorkspaceIdAndSlug(1L, "p")).thenReturn(Optional.of(practice));
        when(practiceAreaRepository.findByWorkspaceIdAndSlug(1L, "current")).thenReturn(Optional.of(resolvedArea));

        assertThat(service.bindPractice(CTX, "p", "current")).isSameAs(practice);

        verify(practiceRepository, never()).findMaxDisplayOrder(any(), any());
        verify(practiceRepository, never()).save(any());
        verify(practiceRevisionService, never()).append(any());
    }

    @Test
    void bindPractice_unresolvedArea_throwsNotFound() {
        when(practiceRepository.findByWorkspaceIdAndSlug(1L, "p")).thenReturn(Optional.of(practice("p")));
        when(practiceAreaRepository.findByWorkspaceIdAndSlug(1L, "foreign")).thenReturn(Optional.empty());

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() ->
            service.bindPractice(CTX, "p", "foreign")
        );
        verify(practiceRepository, never()).save(any());
    }

    @Test
    void deleteArea_appendsPracticesToUnassigned() {
        PracticeArea area = area("deleted");
        area.setId(20L);
        Practice first = practice("first");
        first.setId(1L);
        first.setArea(area);
        Practice second = practice("second");
        second.setId(2L);
        second.setArea(area);
        when(practiceAreaRepository.findByWorkspaceIdAndSlug(1L, "deleted")).thenReturn(Optional.of(area));
        when(practiceRepository.findMaxDisplayOrder(1L, null)).thenReturn(3);
        when(practiceRepository.findByWorkspaceIdAndAreaIdOrderByDisplayOrderAscNameAsc(1L, 20L)).thenReturn(
            List.of(first, second)
        );

        service.deleteArea(CTX, "deleted");

        assertThat(List.of(first, second))
            .extracting(Practice::getArea, Practice::getDisplayOrder)
            .containsExactly(tuple(null, 4), tuple(null, 5));
        verify(practiceRevisionService).append(first);
        verify(practiceRevisionService).append(second);
        verify(practiceAreaRepository).delete(area);
    }

    @Test
    void deleteArea_recordsTheDeletedConfiguration() {
        PracticeArea area = area("deleted");
        area.setId(20L);
        area.setName("Deleted");
        when(practiceAreaRepository.findByWorkspaceIdAndSlug(1L, "deleted")).thenReturn(Optional.of(area));

        service.deleteArea(CTX, "deleted");

        ConfigAuditEntry entry = capturedAuditEntry();
        assertThat(entry.entityType()).isEqualTo(ConfigAuditEntityType.PRACTICE_AREA);
        assertThat(entry.before()).isEqualTo(
            new PracticeAreaSnapshot("deleted", "Deleted", null, true, null, null, null)
        );
        assertThat(entry.after()).isNull();
    }

    private ConfigAuditEntry capturedAuditEntry() {
        ArgumentCaptor<ConfigAuditEntry> captor = ArgumentCaptor.forClass(ConfigAuditEntry.class);
        verify(configAudit).record(captor.capture());
        return captor.getValue();
    }

    private static PracticeArea area(String slug) {
        PracticeArea g = new PracticeArea();
        g.setSlug(slug);
        return g;
    }

    private static Practice practice(String slug) {
        Practice practice = new Practice();
        practice.setSlug(slug);
        practice.setName(slug);
        practice.setBindings(PracticeTestEvidence.bindings(ArtifactKinds.PULL_REQUEST));
        practice.setBindings(PracticeTestEvidence.bindings(ScmSignals.PULL_REQUEST_OPENED));
        practice.setCriteria("criteria");
        practice.setAutomatedReviewPolicy(PracticeTestEvidence.forArtifact(ArtifactKinds.PULL_REQUEST));
        return practice;
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
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
            service.reorder(CTX, List.of("a", "a"))
        );
        verify(practiceAreaRepository, never()).save(any());
    }

    @Test
    void reorder_partialList_rejectsAndWritesNothing() {
        when(practiceAreaRepository.findByWorkspaceIdOrderByDisplayOrderAscNameAsc(1L)).thenReturn(
            List.of(area("a"), area("b"), area("c"))
        );

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
            service.reorder(CTX, List.of("a", "b"))
        );
        verify(practiceAreaRepository, never()).save(any());
    }
}
