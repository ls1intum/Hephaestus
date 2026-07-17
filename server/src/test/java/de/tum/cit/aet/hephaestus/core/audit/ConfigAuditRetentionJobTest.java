package de.tum.cit.aet.hephaestus.core.audit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ConfigAuditRetentionJobTest {

    @Test
    void sweepsAtTheDeclaredRetentionWindow() {
        // The window reaches SQL only through this argument; the changelog parity test pins the constant
        // to the trigger's interval, and this pins that the sweep actually uses it.
        ConfigAuditEventRepository repository = mock(ConfigAuditEventRepository.class);
        new ConfigAuditRetentionJob(repository).sweep();
        verify(repository).deleteOlderThan(ConfigAuditRetentionJob.RETENTION_DAYS);
    }
}
