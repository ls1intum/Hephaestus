package de.tum.cit.aet.hephaestus.core.auth.export;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.PrivacyJobMetrics;
import de.tum.cit.aet.hephaestus.core.PrivacyJobMetrics.Job;
import de.tum.cit.aet.hephaestus.core.PrivacyJobMetrics.Outcome;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

class ExportRetentionSweeperTest extends BaseUnitTest {

    @Mock
    private AccountExportService accountExportService;

    @Mock
    private PrivacyJobMetrics metrics;

    @InjectMocks
    private ExportRetentionSweeper sweeper;

    @Test
    void shouldRecordSuccessAndTheExpiredCountWhenTheSweepRuns() {
        when(accountExportService.expireRetention()).thenReturn(4);

        sweeper.sweep();

        verify(metrics).record(Job.EXPORT_RETENTION, Outcome.SUCCESS);
        verify(metrics).recordAffected(Job.EXPORT_RETENTION, 4);
    }

    @Test
    void shouldRecordSuccessWhenNothingWasEligible() {
        when(accountExportService.expireRetention()).thenReturn(0);

        sweeper.sweep();

        verify(metrics).record(Job.EXPORT_RETENTION, Outcome.SUCCESS);
        verify(metrics).recordAffected(Job.EXPORT_RETENTION, 0);
    }

    @Test
    void shouldRecordFailureAndPropagateWhenTheSweepFails() {
        when(accountExportService.expireRetention()).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(sweeper::sweep).isInstanceOf(IllegalStateException.class);

        verify(metrics).record(Job.EXPORT_RETENTION, Outcome.FAILURE);
    }
}
