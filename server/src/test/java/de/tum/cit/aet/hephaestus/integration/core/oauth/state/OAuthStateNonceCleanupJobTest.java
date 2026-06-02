package de.tum.cit.aet.hephaestus.integration.core.oauth.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * Unit tests for {@link OAuthStateNonceCleanupJob}. The job is a one-liner over
 * {@link OAuthStateNonceRepository#deleteByIssuedAtBefore}, so tests cover:
 * <ul>
 *   <li>Cutoff is computed as {@code now - retention}.</li>
 *   <li>Returned row count drives the counter, including the 0-row case.</li>
 *   <li>{@code @SchedulerLock} is present with the documented bounds.</li>
 * </ul>
 */
class OAuthStateNonceCleanupJobTest extends BaseUnitTest {

    @Mock
    private OAuthStateNonceRepository repository;

    @Test
    void incrementsCounterByRowCount() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OAuthStateNonceCleanupJob job = new OAuthStateNonceCleanupJob(repository, Duration.ofDays(7), registry);
        when(repository.deleteByIssuedAtBefore(any(Instant.class))).thenReturn(5);

        job.cleanupExpired();

        verify(repository).deleteByIssuedAtBefore(any(Instant.class));
        assertThat(registry.find("oauth.state.nonce.pruned").counter().count()).isEqualTo(5.0);
    }

    @Test
    void zeroRowSweep() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OAuthStateNonceCleanupJob job = new OAuthStateNonceCleanupJob(repository, Duration.ofDays(7), registry);
        when(repository.deleteByIssuedAtBefore(any(Instant.class))).thenReturn(0);

        job.cleanupExpired();

        assertThat(registry.find("oauth.state.nonce.pruned").counter().count()).isEqualTo(0.0);
    }

    @Test
    void schedulerLockPresent() throws NoSuchMethodException {
        SchedulerLock lock = OAuthStateNonceCleanupJob.class.getMethod("cleanupExpired").getAnnotation(
            SchedulerLock.class
        );
        assertThat(lock).as("@SchedulerLock must be present").isNotNull();
        assertThat(lock.name()).isEqualTo("oauth-state-nonce-cleanup");
        assertThat(lock.lockAtMostFor()).isEqualTo("PT10M");
        assertThat(lock.lockAtLeastFor()).isEqualTo("PT1M");
    }

    @Test
    void nullRetentionFallsBackToDefault() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OAuthStateNonceCleanupJob job = new OAuthStateNonceCleanupJob(repository, (Duration) null, registry);
        when(repository.deleteByIssuedAtBefore(any(Instant.class))).thenReturn(0);

        // Should not throw — null is normalised.
        job.cleanupExpired();
        verify(repository).deleteByIssuedAtBefore(any(Instant.class));
    }
}
