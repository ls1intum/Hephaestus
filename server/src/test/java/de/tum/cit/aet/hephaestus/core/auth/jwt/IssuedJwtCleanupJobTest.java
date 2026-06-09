package de.tum.cit.aet.hephaestus.core.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link IssuedJwtCleanupJob}: it must delete rows expired as of the (injected) clock instant
 * and count exactly what it removed. Fails if the cutoff drifts off the clock or the metric is dropped.
 */
class IssuedJwtCleanupJobTest extends BaseUnitTest {

    private static final Instant NOW = Instant.parse("2026-06-02T03:30:00Z");

    @Test
    void cleanupExpired_deletesAtClockInstantAndCountsPrunedRows() {
        IssuedJwtRepository repository = mock(IssuedJwtRepository.class);
        when(repository.deleteExpiredBefore(eq(NOW))).thenReturn(7);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        IssuedJwtCleanupJob job = new IssuedJwtCleanupJob(repository, Clock.fixed(NOW, ZoneOffset.UTC), registry);

        job.cleanupExpired();

        // Name the real contract directly: the cutoff is the injected clock instant, not "some Instant".
        verify(repository).deleteExpiredBefore(NOW);
        assertThat(registry.get("auth.issued_jwt.pruned").counter().count()).isEqualTo(7.0);
    }
}
