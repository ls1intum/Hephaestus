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
}
