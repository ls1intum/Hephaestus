package de.tum.cit.aet.hephaestus.agent.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

class JobTokenAuthenticationTest extends BaseUnitTest {

    private static final ProxyRouting ROUTING = new ProxyRouting(
        "job:test",
        "anthropic-messages",
        "https://api.anthropic.com",
        null,
        null,
        1L
    );

    @Test
    void shouldReturnRoutingAsPrincipal() {
        var auth = new JobTokenAuthentication(ROUTING);

        assertThat(auth.getPrincipal()).isSameAs(ROUTING);
    }

    @Test
    void shouldRedactCredentials() {
        var auth = new JobTokenAuthentication(ROUTING);

        assertThat(auth.getCredentials()).isEqualTo("[REDACTED]");
    }

    @Test
    void shouldBeAuthenticated() {
        var auth = new JobTokenAuthentication(ROUTING);

        assertThat(auth.isAuthenticated()).isTrue();
    }

    @Test
    void shouldHaveEmptyAuthorities() {
        var auth = new JobTokenAuthentication(ROUTING);

        assertThat(auth.getAuthorities()).isEmpty();
    }
}
