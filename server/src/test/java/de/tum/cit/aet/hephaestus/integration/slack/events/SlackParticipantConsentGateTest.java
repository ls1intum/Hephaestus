package de.tum.cit.aet.hephaestus.integration.slack.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackParticipantConsentRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * Single person firewall. Deterministic: locks the deny-if-opted-out / allow-if-absent rule that
 * {@link SlackIngestService} composes with the capability flag and the channel gate. The one authority both the
 * write-path and any later projector share, so an opted-out individual can never silently leak.
 */
class SlackParticipantConsentGateTest extends BaseUnitTest {

    @Mock
    private SlackParticipantConsentRepository participantConsentRepository;

    private SlackParticipantConsentGate gate;

    @BeforeEach
    void setUp() {
        gate = new SlackParticipantConsentGate(participantConsentRepository);
    }

    @Test
    void optedOutPerson_isDenied() {
        when(
            participantConsentRepository.existsByWorkspaceIdAndSlackUserIdAndIngestionOptedOutTrue(7L, "U1")
        ).thenReturn(true);

        assertThat(gate.ingestionAllowed(7L, "U1")).isFalse();
    }

    @Test
    void personWithNoOptOut_isAllowed() {
        when(
            participantConsentRepository.existsByWorkspaceIdAndSlackUserIdAndIngestionOptedOutTrue(7L, "U1")
        ).thenReturn(false);

        assertThat(gate.ingestionAllowed(7L, "U1")).isTrue();
    }
    // NOTE: an "absent row fails open" case was intentionally removed — under the mock it is byte-identical to
    // personWithNoOptOut_isAllowed (both stub existsBy…OptedOutTrue→false against the pure `return !exists(...)`), so
    // it kills no unique mutant. The real absent-row-vs-opted_out=false SQL distinction is exercised on Postgres in
    // SlackParticipantConsentFirewallIntegrationTest.
}
