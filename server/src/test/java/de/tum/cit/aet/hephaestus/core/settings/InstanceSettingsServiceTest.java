package de.tum.cit.aet.hephaestus.core.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * The silent-mode read the delivery paths consult, and the toggle's reason/actor bookkeeping. Uses a
 * mock repository — the logic under test is the absent-row default and the engage/release field
 * mapping, not persistence (the DB round-trip is covered by the controller integration test).
 */
class InstanceSettingsServiceTest extends BaseUnitTest {

    @Mock
    private InstanceSettingsRepository repository;

    private InstanceSettingsService service;

    @BeforeEach
    void setUp() {
        service = new InstanceSettingsService(repository);
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
    }
}
