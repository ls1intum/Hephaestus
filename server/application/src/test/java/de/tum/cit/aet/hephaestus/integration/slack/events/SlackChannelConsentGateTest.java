package de.tum.cit.aet.hephaestus.integration.slack.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel.ConsentState;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannelRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

    /**
     * Every non-ACTIVE state blocks ingestion. PAUSED is the cell that distinguishes the correct {@code == ACTIVE}
     * rule from a plausible blacklist rewrite ({@code state != PENDING && state != REVOKED}), which would silently
     * leak PAUSED content; folding all three blocked states here pins it. Flip the gate to a blacklist and the
     * PAUSED case fails.
     */
    @ParameterizedTest
    @EnumSource(value = ConsentState.class, names = { "PENDING", "PAUSED", "REVOKED" })
    void nonActiveChannel_blocksIngest(ConsentState blockedState) {
        when(monitoredChannelRepository.findConsentState(7L, "C1")).thenReturn(Optional.of(blockedState));

        assertThat(gate.ingestAllowed(7L, "C1")).isFalse();
    }

    @Test
    void absentRow_failsClosed() {
        when(monitoredChannelRepository.findConsentState(7L, "C1")).thenReturn(Optional.empty());

        assertThat(gate.ingestAllowed(7L, "C1")).isFalse();
    }
}
