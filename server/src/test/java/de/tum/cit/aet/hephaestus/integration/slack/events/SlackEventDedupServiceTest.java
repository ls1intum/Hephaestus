package de.tum.cit.aet.hephaestus.integration.slack.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackEventDedupRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * S9 dedup-service unit tests: {@link SlackEventDedupService#claim} maps the repository's first-writer-wins insert
 * (rows-affected {@code 1} vs {@code 0}) onto process-this-event vs drop-duplicate.
 */
class SlackEventDedupServiceTest extends BaseUnitTest {

    @Mock
    private SlackEventDedupRepository repository;

    @Test
    void claim_returnsTrueWhenRowInserted() {
        when(repository.claim(eq("Ev1"), any(Instant.class), any(Instant.class))).thenReturn(1);
        SlackEventDedupService service = new SlackEventDedupService(repository, Duration.ofHours(48));

        assertThat(service.claim("Ev1")).isTrue();
    }

    @Test
    void claim_returnsFalseWhenRowAlreadyPresent() {
        when(repository.claim(eq("Ev1"), any(Instant.class), any(Instant.class))).thenReturn(0);
        SlackEventDedupService service = new SlackEventDedupService(repository, Duration.ofHours(48));

        assertThat(service.claim("Ev1")).isFalse();
    }

    @Test
    void nonPositiveTtl_fallsBackToDefault() {
        // A misconfigured zero/negative TTL must not produce expires_at <= received_at; the guard swaps in 48h.
        when(repository.claim(eq("Ev1"), any(Instant.class), any(Instant.class))).thenReturn(1);
        SlackEventDedupService service = new SlackEventDedupService(repository, Duration.ZERO);

        assertThat(service.claim("Ev1")).isTrue();
    }
}
