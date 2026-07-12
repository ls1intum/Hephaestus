package de.tum.cit.aet.hephaestus.core.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Pins the two load-bearing contracts of the disclosure writer:
 * <ul>
 *   <li>a report-view / roster-view persists the right actor, subject, resource_type, and timestamp;</li>
 *   <li><b>fail-closed</b>: unlike {@code AuthEventWriter}, a persistence failure is NOT swallowed — it
 *       propagates, so an un-audited disclosure can never be served.</li>
 * </ul>
 */
class DataAccessAuditWriterTest extends BaseUnitTest {

    private static final Instant NOW = Instant.parse("2026-07-02T10:15:30Z");
    private static final Clock FIXED = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("recordReportView persists actor -> subject with resource_type PRACTICE_REPORT and the clock time")
    void recordReportViewPersistsRow() {
        DataAccessEventRepository repository = mock(DataAccessEventRepository.class);
        DataAccessAuditWriter writer = new DataAccessAuditWriter(repository, FIXED);

        writer.recordReportView(7L, 11L, 22L);

        ArgumentCaptor<DataAccessEvent> captor = ArgumentCaptor.forClass(DataAccessEvent.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        DataAccessEvent saved = captor.getValue();
        assertThat(saved.getWorkspaceId()).isEqualTo(7L);
        assertThat(saved.getActorUserId()).isEqualTo(11L);
        assertThat(saved.getSubjectUserId()).isEqualTo(22L);
        assertThat(saved.getResourceType()).isEqualTo(DataAccessResourceType.PRACTICE_REPORT);
        assertThat(saved.getOccurredAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("recordRosterView persists a bulk view: subject is NULL, resource_type PRACTICE_ROSTER")
    void recordRosterViewPersistsNullSubject() {
        DataAccessEventRepository repository = mock(DataAccessEventRepository.class);
        DataAccessAuditWriter writer = new DataAccessAuditWriter(repository, FIXED);

        writer.recordRosterView(7L, 11L);

        ArgumentCaptor<DataAccessEvent> captor = ArgumentCaptor.forClass(DataAccessEvent.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        DataAccessEvent saved = captor.getValue();
        assertThat(saved.getWorkspaceId()).isEqualTo(7L);
        assertThat(saved.getActorUserId()).isEqualTo(11L);
        assertThat(saved.getSubjectUserId()).isNull();
        assertThat(saved.getResourceType()).isEqualTo(DataAccessResourceType.PRACTICE_ROSTER);
    }

    @Test
    @DisplayName("fail-closed: a persistence failure propagates (NOT swallowed like AuthEventWriter)")
    void persistenceFailurePropagates() {
        DataAccessEventRepository repository = mock(DataAccessEventRepository.class);
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("boom"));
        DataAccessAuditWriter writer = new DataAccessAuditWriter(repository, FIXED);

        assertThatThrownBy(() -> writer.recordReportView(7L, 11L, 22L)).isInstanceOf(
            DataIntegrityViolationException.class
        );
        assertThatThrownBy(() -> writer.recordRosterView(7L, 11L)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
