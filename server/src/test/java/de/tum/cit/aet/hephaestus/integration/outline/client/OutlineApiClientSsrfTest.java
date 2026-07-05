package de.tum.cit.aet.hephaestus.integration.outline.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The Outline server URL is admin-supplied, so it must be validated before any request is made.
 * These assert the SSRF guard rejects private/loopback/metadata targets up front — no network call
 * happens because validation throws first.
 */
class OutlineApiClientSsrfTest extends BaseUnitTest {

    private final OutlineApiClient client = new OutlineApiClient(
        CircuitBreaker.ofDefaults("outlineRestApi"),
        Retry.ofDefaults("outlineRestApi")
    );

    @ParameterizedTest
    @ValueSource(
        strings = {
            "http://169.254.169.254", // cloud metadata (rejected at the http scheme gate)
            "http://127.0.0.1:5432", // loopback
            "http://localhost:3000",
            "http://10.0.0.5",
            "http://192.168.1.10",
        }
    )
    void validateToken_rejectsPrivateOrMetadataHosts(String serverUrl) {
        assertThatThrownBy(() -> client.validateToken(serverUrl, "tok")).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * These pass the https scheme gate and must be rejected by {@code ServerUrlValidator}'s IP-range /
     * canonical-form logic — proving the client delegates past the scheme check, not merely gating on it.
     */
    @ParameterizedTest
    @ValueSource(
        strings = {
            "https://2130706433", // 127.0.0.1 as a decimal integer (non-canonical numeric host)
            "https://2852039166", // 169.254.169.254 metadata as a decimal integer
            "https://[fc00::1]", // IPv6 unique-local (ULA) — private range Java's isSiteLocal misses
        }
    )
    void validateToken_rejectsNonCanonicalAndIpv6PrivateHosts(String serverUrl) {
        assertThatThrownBy(() -> client.validateToken(serverUrl, "tok")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateToken_rejectsBlankServerUrl() {
        assertThatThrownBy(() -> client.validateToken("  ", "tok")).isInstanceOf(OutlineApiException.class);
    }
}
