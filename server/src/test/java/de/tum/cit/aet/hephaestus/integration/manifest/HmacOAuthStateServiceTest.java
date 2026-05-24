package de.tum.cit.aet.hephaestus.integration.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.OAuthStateService.StateBinding;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HmacOAuthStateService HMAC + TTL semantics")
class HmacOAuthStateServiceTest extends BaseUnitTest {

    private static final String SECRET = "unit-test-secret-with-enough-entropy-32b";

    @Test
    void issuedStateRoundTrips() {
        HmacOAuthStateService svc = new HmacOAuthStateService(SECRET, Duration.ofMinutes(10));
        String state = svc.issue(42L, IntegrationKind.GITHUB);

        StateBinding binding = svc.consume(state);
        assertThat(binding.workspaceId()).isEqualTo(42L);
        assertThat(binding.kind()).isEqualTo(IntegrationKind.GITHUB);
    }

    @Test
    void differentInvocationsProduceDifferentStates() {
        HmacOAuthStateService svc = new HmacOAuthStateService(SECRET, Duration.ofMinutes(10));
        String a = svc.issue(1L, IntegrationKind.SLACK);
        String b = svc.issue(1L, IntegrationKind.SLACK);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void tamperedStateRejected() {
        HmacOAuthStateService svc = new HmacOAuthStateService(SECRET, Duration.ofMinutes(10));
        String state = svc.issue(42L, IntegrationKind.GITHUB);
        String tampered = state.substring(0, state.length() - 2) + "AA";
        assertThatThrownBy(() -> svc.consume(tampered))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void issuedWithDifferentSecretRejected() {
        HmacOAuthStateService a = new HmacOAuthStateService(SECRET, Duration.ofMinutes(10));
        HmacOAuthStateService b = new HmacOAuthStateService("another-secret-of-equivalent-length-xx", Duration.ofMinutes(10));
        String state = a.issue(42L, IntegrationKind.GITHUB);
        assertThatThrownBy(() -> b.consume(state))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("signature mismatch");
    }

    @Test
    void blankSecretRejectedAtConstruction() {
        assertThatThrownBy(() -> new HmacOAuthStateService("", Duration.ofMinutes(10)))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new HmacOAuthStateService(null, Duration.ofMinutes(10)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void blankStateRejected() {
        HmacOAuthStateService svc = new HmacOAuthStateService(SECRET, Duration.ofMinutes(10));
        assertThatThrownBy(() -> svc.consume(""))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> svc.consume(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actorRefRoundTripsThroughHmacPayload() {
        HmacOAuthStateService svc = new HmacOAuthStateService(SECRET, Duration.ofMinutes(10));
        String state = svc.issue(42L, IntegrationKind.SLACK, "alice@example.com");

        StateBinding binding = svc.consume(state);
        assertThat(binding.workspaceId()).isEqualTo(42L);
        assertThat(binding.kind()).isEqualTo(IntegrationKind.SLACK);
        assertThat(binding.actorRef()).isEqualTo("alice@example.com");
    }

    @Test
    void actorRefIsNullWhenIssuedViaLegacyOverload() {
        HmacOAuthStateService svc = new HmacOAuthStateService(SECRET, Duration.ofMinutes(10));
        // The no-actor overload must keep working byte-compatibly — older callers don't
        // know about actorRef and the controller should fall back to a sentinel.
        String state = svc.issue(42L, IntegrationKind.GITHUB);

        StateBinding binding = svc.consume(state);
        assertThat(binding.actorRef()).isNull();
    }

    @Test
    void actorRefIsNullWhenExplicitNullPassedToNewOverload() {
        HmacOAuthStateService svc = new HmacOAuthStateService(SECRET, Duration.ofMinutes(10));
        String state = svc.issue(42L, IntegrationKind.GITHUB, null);
        StateBinding binding = svc.consume(state);
        assertThat(binding.actorRef()).isNull();
    }

    @Test
    void actorRefContainingPipeCharacterSurvivesTokeniser() {
        // The HMAC payload tokeniser uses '|' as a delimiter; the actor segment is
        // base64url-encoded specifically so identity sources that emit pipe-bearing
        // subjects (rare but valid in some IDP configs) don't break the framing.
        HmacOAuthStateService svc = new HmacOAuthStateService(SECRET, Duration.ofMinutes(10));
        String actor = "user|with|pipes";
        String state = svc.issue(42L, IntegrationKind.OUTLINE, actor);
        assertThat(svc.consume(state).actorRef()).isEqualTo(actor);
    }

    @Test
    void tamperedActorSegmentRejectedByHmac() {
        HmacOAuthStateService svc = new HmacOAuthStateService(SECRET, Duration.ofMinutes(10));
        String state = svc.issue(42L, IntegrationKind.GITHUB, "alice");
        // Flipping a base64 char in the payload (not the signature) MUST still fail —
        // the actor segment is part of the signed payload.
        String tampered = state.substring(0, 4) + "AAAA" + state.substring(8);
        assertThatThrownBy(() -> svc.consume(tampered))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
