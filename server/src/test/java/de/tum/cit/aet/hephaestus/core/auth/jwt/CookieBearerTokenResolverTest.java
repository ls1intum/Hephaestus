package de.tum.cit.aet.hephaestus.core.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.auth.AuthProperties;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Security contract for {@link CookieBearerTokenResolver}: the cookie token is authoritative and a
 * hostile {@code Authorization} header can never override it (cookie-first invariant, ADR 0017). The
 * HttpOnly access cookie is trusted; an attacker-supplied header is not, and the SPA never sends a
 * header at all. A regression that flips precedence is a token-confusion vulnerability — this test
 * fails loudly if that ever happens.
 */
class CookieBearerTokenResolverTest extends BaseUnitTest {

    private static final String COOKIE_NAME = "__Host-HEPHAESTUS_AT";
    private static final String COOKIE_TOKEN = "cookie-token-trusted";
    private static final String HEADER_TOKEN = "attacker-token-hostile";

    private CookieBearerTokenResolver resolver;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties(
            URI.create("http://localhost:8080"),
            "hephaestus-spa",
            Duration.ofMinutes(15),
            COOKIE_NAME,
            "",
            Duration.ofHours(48),
            new AuthProperties.GithubLogin("", ""),
            new AuthProperties.GitlabLogin("", "", URI.create("https://gitlab.com"), "GitLab"),
            java.util.List.of(),
            "",
            Duration.ofHours(1)
        );
        resolver = new CookieBearerTokenResolver(properties);
    }

    @Test
    void resolve_cookieAndHostileHeader_returnsCookieToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(COOKIE_NAME, COOKIE_TOKEN));
        request.addHeader("Authorization", "Bearer " + HEADER_TOKEN);

        // Cookie-first: the trusted HttpOnly cookie wins; the hostile header is never consulted.
        assertThat(resolver.resolve(request)).isEqualTo(COOKIE_TOKEN);
    }

    @Test
    void resolve_cookieOnly_returnsCookieToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(COOKIE_NAME, COOKIE_TOKEN));

        assertThat(resolver.resolve(request)).isEqualTo(COOKIE_TOKEN);
    }

    @Test
    void resolve_headerOnly_returnsHeaderToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + HEADER_TOKEN);

        // No cookie → standard header fallback (worker/API/test clients).
        assertThat(resolver.resolve(request)).isEqualTo(HEADER_TOKEN);
    }

    @Test
    void resolve_neither_returnsNull() {
        assertThat(resolver.resolve(new MockHttpServletRequest())).isNull();
    }

    @Test
    void resolve_blankCookieValue_fallsBackToHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(COOKIE_NAME, "")); // blank → not a usable token
        request.addHeader("Authorization", "Bearer " + HEADER_TOKEN);

        assertThat(resolver.resolve(request)).isEqualTo(HEADER_TOKEN);
    }
}
