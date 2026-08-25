package de.tum.cit.aet.hephaestus.core.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Pins the fail-closed contract of {@link ProxyTrustGuard}: in prod with native forwarding, an unset
 * or wildcard-default {@code internal-proxies} must abort the boot; a pinned ingress address passes;
 * non-prod / non-native boots are never checked.
 */
class ProxyTrustGuardTest extends BaseUnitTest {

    private static Environment env(boolean prod) {
        Environment environment = mock(Environment.class);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(prod);
        return environment;
    }

    private static ProxyTrustGuard guard(boolean prod, String strategy, String internalProxies) {
        return new ProxyTrustGuard(env(prod), strategy, internalProxies);
    }

    @Test
    void prodNativeWithBlankFailsClosed() {
        assertThatThrownBy(() -> guard(true, "native", "").assertProxyTrustPinnedInProd())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("HEPHAESTUS_TRUSTED_PROXIES");
    }

    @Test
    void prodNativeWithWhitespaceOnlyFailsClosed() {
        assertThatThrownBy(() -> guard(true, "native", "   ").assertProxyTrustPinnedInProd()).isInstanceOf(
            IllegalStateException.class
        );
    }

    @Test
    void prodNativeWithPinnedAddressPasses() {
        assertThatCode(() ->
            guard(true, "native", "10\\.42\\.0\\.\\d{1,3}").assertProxyTrustPinnedInProd()
        ).doesNotThrowAnyException();
    }

    @Test
    void nonProdIsNeverChecked() {
        assertThatCode(() -> guard(false, "native", "").assertProxyTrustPinnedInProd()).doesNotThrowAnyException();
    }

    @Test
    void prodWithoutNativeForwardingIsNeverChecked() {
        assertThatCode(() -> guard(true, "none", "").assertProxyTrustPinnedInProd()).doesNotThrowAnyException();
    }
}
