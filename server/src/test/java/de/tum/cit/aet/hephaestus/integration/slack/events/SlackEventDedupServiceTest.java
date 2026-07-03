package de.tum.cit.aet.hephaestus.integration.slack.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackEventDedupRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

/**
 * Dedup-service unit tests: {@link SlackEventDedupService#claim} maps the repository's first-writer-wins insert
 * (rows-affected {@code 1} vs {@code 0}) onto process-this-event vs drop-duplicate.
 */
class SlackEventDedupServiceTest extends BaseUnitTest {

    @Mock
    private SlackEventDedupRepository repository;

    @ParameterizedTest
    @CsvSource({ "1, true", "0, false" })
    void claim_mapsRowsAffectedToProcessDecision(int rowsAffected, boolean expected) {
        when(repository.claim(eq("Ev1"), any(Instant.class), any(Instant.class))).thenReturn(rowsAffected);
        SlackEventDedupService service = new SlackEventDedupService(repository, Duration.ofHours(48));

        assertThat(service.claim("Ev1")).isEqualTo(expected);
    }

    @Test
    void nonPositiveTtl_fallsBackToDefault() {
        // A misconfigured zero/negative TTL must not produce expires_at <= received_at; the guard swaps in 48h.
        when(repository.claim(eq("Ev1"), any(Instant.class), any(Instant.class))).thenReturn(1);
        SlackEventDedupService service = new SlackEventDedupService(repository, Duration.ZERO);

        service.claim("Ev1");

        ArgumentCaptor<Instant> received = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> expires = ArgumentCaptor.forClass(Instant.class);
        verify(repository).claim(eq("Ev1"), received.capture(), expires.capture());
        assertThat(Duration.between(received.getValue(), expires.getValue())).isEqualTo(Duration.ofHours(48));
    }
}
