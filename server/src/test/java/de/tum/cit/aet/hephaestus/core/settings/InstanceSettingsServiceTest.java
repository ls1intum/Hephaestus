package de.tum.cit.aet.hephaestus.core.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEvent;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventLogger;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventWriter;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import tools.jackson.databind.ObjectMapper;

/** Mock repository: the DB round-trip is covered by {@link InstanceSettingsAdminControllerIntegrationTest}. */
class InstanceSettingsServiceTest extends BaseUnitTest {

    @Mock
    private InstanceSettingsRepository repository;

    private InstanceSettingsService service;

    private AuthEventWriter authEventWriter;

    @BeforeEach
    void setUp() {
        authEventWriter = mock(AuthEventWriter.class);
        service = new InstanceSettingsService(
            repository,
            Optional.of(new AuthEventLogger(authEventWriter)),
            new ObjectMapper()
        );
    }

    @Test
    void toggleIsRecordedOnTheAuditTrail() {
        givenRow(new InstanceSettings());

        service.updateSilentMode(true, "incident #42", "felix");

        verify(authEventWriter).write(
            argThat(
                data ->
                    data.type() == AuthEvent.EventType.SILENT_MODE_CHANGED &&
                    data.details() != null &&
                    data.details().contains("incident #42")
            )
        );
    }

    @Test
    void isSilentModeEngaged_absentRow_isReleased() {
        when(repository.findById(InstanceSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        assertThat(service.isSilentModeEngaged()).isFalse();
    }

    @Test
    void isSilentModeEngaged_reflectsTheRow() {
        InstanceSettings row = new InstanceSettings();
        row.setSilentModeEngaged(true);
        when(repository.findById(InstanceSettings.SINGLETON_ID)).thenReturn(Optional.of(row));
        assertThat(service.isSilentModeEngaged()).isTrue();
    }

    @Test
    void engage_recordsTrimmedReasonAndActor() {
        givenRow(new InstanceSettings());

        InstanceSettings updated = service.updateSilentMode(true, "  incident #42  ", "felix");

        assertThat(updated.isSilentModeEngaged()).isTrue();
        assertThat(updated.getSilentModeReason()).isEqualTo("incident #42");
        assertThat(updated.getSilentModeChangedBy()).isEqualTo("felix");
        assertThat(updated.getSilentModeChangedAt()).isNotNull();
    }

    @Test
    void engage_blankReasonBecomesNull() {
        givenRow(new InstanceSettings());
        assertThat(service.updateSilentMode(true, "   ", "felix").getSilentModeReason()).isNull();
    }

    @Test
    void release_clearsTheReason() {
        InstanceSettings engaged = new InstanceSettings();
        engaged.setSilentModeEngaged(true);
        engaged.setSilentModeReason("incident #42");
        givenRow(engaged);

        InstanceSettings released = service.updateSilentMode(false, "ignored on release", "felix");

        assertThat(released.isSilentModeEngaged()).isFalse();
        assertThat(released.getSilentModeReason()).isNull();
    }

    private void givenRow(InstanceSettings row) {
        row.setId(InstanceSettings.SINGLETON_ID);
        when(repository.findById(InstanceSettings.SINGLETON_ID)).thenReturn(Optional.of(row));
        when(repository.save(any(InstanceSettings.class))).thenAnswer(call -> call.getArgument(0));
    }
}
