package de.tum.cit.aet.hephaestus.agent.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

/**
 * The one thing this token decides for itself: it never hands the bearer secret back out.
 *
 * <p>Everything else about it — the principal it carries, that it is authenticated, that it holds no
 * authorities — is Spring's own contract, and {@code JobTokenAuthenticationFilterTest} asserts the
 * assembled token where it actually lands, on the {@code SecurityContext}.
 */
class JobTokenAuthenticationTest extends BaseUnitTest {

    private static final ProxyRouting ROUTING = new ProxyRouting(
        "job:test",
        "anthropic-messages",
        "https://api.anthropic.com",
        null,
        null,
        null,
        null,
        null
    );

    @Test
    void shouldRedactCredentials() {
        var auth = new JobTokenAuthentication(ROUTING);

        // The default AbstractAuthenticationToken credential is whatever was passed in; this override
        // is what keeps a proxy bearer token out of every log line that prints the Authentication.
        assertThat(auth.getCredentials()).isEqualTo("[REDACTED]");
    }
}
