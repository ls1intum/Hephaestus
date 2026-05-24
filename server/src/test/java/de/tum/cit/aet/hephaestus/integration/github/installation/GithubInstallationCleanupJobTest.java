package de.tum.cit.aet.hephaestus.integration.github.installation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Unit tests for {@link GithubInstallationCleanupJob}.
 *
 * <p>The cleanup job is intentionally thin: pull expired rows, delete them, increment a
 * counter, never abort the batch on a single failure. The tests exercise those three
 * properties without spinning up Spring.
 */
@DisplayName("GithubInstallationCleanupJob — unit")
class GithubInstallationCleanupJobTest extends BaseUnitTest {

    @Mock
    private GithubInstallationUnboundRepository repository;

    @Spy
    private SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks
    private GithubInstallationCleanupJob job;

    @BeforeEach
    void wire() {
        // Mockito's @InjectMocks won't see the registry-built counter, so build the job manually.
        job = new GithubInstallationCleanupJob(repository, meterRegistry);
    }

    @Test
    @DisplayName("processes all expired rows, increments counter per delete")
    void processesAllExpiredRows() {
        List<GithubInstallationUnbound> expired = List.of(
            row(1L), row(2L), row(3L)
        );
        when(repository.findByExpiresAtBefore(any(Instant.class))).thenReturn(expired);
        doNothing().when(repository).delete(any(GithubInstallationUnbound.class));

        job.cleanupExpired();

        verify(repository, times(3)).delete(any(GithubInstallationUnbound.class));
        Counter counter = meterRegistry.find("github.installation.unbound.cleaned").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("one failing delete does not block the rest of the batch")
    void oneFailureDoesNotBlockBatch() {
        GithubInstallationUnbound poison = row(2L);
        List<GithubInstallationUnbound> expired = List.of(row(1L), poison, row(3L));
        when(repository.findByExpiresAtBefore(any(Instant.class))).thenReturn(expired);

        doNothing().when(repository).delete(any(GithubInstallationUnbound.class));
        doThrow(new RuntimeException("simulated DB error")).when(repository).delete(poison);

        job.cleanupExpired();

        verify(repository, times(3)).delete(any(GithubInstallationUnbound.class));
        Counter counter = meterRegistry.find("github.installation.unbound.cleaned").counter();
        assertThat(counter).isNotNull();
        // Two successes, one failure → counter at 2.
        assertThat(counter.count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("empty expired list short-circuits with no deletes and no counter increments")
    void emptyListShortCircuits() {
        when(repository.findByExpiresAtBefore(any(Instant.class))).thenReturn(List.of());

        job.cleanupExpired();

        verify(repository, times(0)).delete(any(GithubInstallationUnbound.class));
        Counter counter = meterRegistry.find("github.installation.unbound.cleaned").counter();
        // Counter is created in the constructor; assert it stayed at zero.
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("disabled-by-default: @ConditionalOnProperty gates bean activation")
    void conditionalOnPropertyPresent() {
        // Verify via reflection that the @ConditionalOnProperty annotation gates the job,
        // so it is not loaded into the Spring context unless the operator opts in.
        ConditionalOnProperty conditional = GithubInstallationCleanupJob.class.getAnnotation(ConditionalOnProperty.class);
        assertThat(conditional).as("@ConditionalOnProperty must be present").isNotNull();
        assertThat(conditional.name())
            .as("Property name must match the documented opt-in flag")
            .containsExactly("hephaestus.integration.github.unbound-cleanup.enabled");
        assertThat(conditional.havingValue()).isEqualTo("true");
    }

    private static GithubInstallationUnbound row(long id) {
        return new GithubInstallationUnbound(id);
    }
}
