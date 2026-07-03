package de.tum.cit.aet.hephaestus.integration.slack.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel.ConsentState;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannelRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * Single consent gate. Deterministic: locks that a channel flows content <strong>only</strong> when its
 * consent is {@code ACTIVE}, and fails closed on an absent row — the one authority the ingest write-path and
 * any later projector share, so a non-{@code ACTIVE} channel can never silently leak.
 */
class SlackChannelConsentGateTest extends BaseUnitTest {

    @Mock
    private SlackMonitoredChannelRepository monitoredChannelRepository;

    private SlackChannelConsentGate gate;

    @BeforeEach
    void setUp() {
        gate = new SlackChannelConsentGate(monitoredChannelRepository);
    }

    @Test
    void activeChannel_allowsIngest() {
        when(monitoredChannelRepository.findConsentState(7L, "C1")).thenReturn(Optional.of(ConsentState.ACTIVE));

        assertThat(gate.ingestAllowed(7L, "C1")).isTrue();
    }

    @Test
    void pendingChannel_blocksIngest() {
        when(monitoredChannelRepository.findConsentState(7L, "C1")).thenReturn(Optional.of(ConsentState.PENDING));

        assertThat(gate.ingestAllowed(7L, "C1")).isFalse();
    }

    @Test
    void revokedChannel_blocksIngest() {
        when(monitoredChannelRepository.findConsentState(7L, "C1")).thenReturn(Optional.of(ConsentState.REVOKED));

        assertThat(gate.ingestAllowed(7L, "C1")).isFalse();
    }

    @Test
    void absentRow_failsClosed() {
        when(monitoredChannelRepository.findConsentState(7L, "C1")).thenReturn(Optional.empty());

        assertThat(gate.ingestAllowed(7L, "C1")).isFalse();
    }
}
