package de.tum.cit.aet.hephaestus.core.auth;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Verifies the stateless double-submit CSRF protection on the resource-server chain (ADR 0017).
 * The SPA authenticates by cookie, so SameSite=Lax alone is insufficient — a cookie-style
 * (non-bearer) state-changing request must carry the {@code X-XSRF-TOKEN} header matching the
 * {@code XSRF-TOKEN} cookie. This test FAILS if CSRF is disabled (the no-token POST would no longer
 * be rejected with 403).
 *
 * <p>Distinguishing the two failure modes: a missing/invalid CSRF token is rejected by the CSRF
 * filter with <b>403</b> before authentication; once CSRF passes, the (still unauthenticated)
 * request is rejected by the entry point with <b>401</b>. The 403→401 transition proves CSRF — not
 * auth — was the gate.
 */
class CsrfProtectionIntegrationTest extends BaseIntegrationTest {

    private static final String XSRF_COOKIE = "XSRF-TOKEN";
    private static final String XSRF_HEADER = "X-XSRF-TOKEN";

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void csrfCookieIsIssuedOnSafeRequest() {
        String token = fetchCsrfToken();
        assertThat(token).isNotBlank();
    }

    @Test
    void cookieStylePostWithoutCsrfTokenIsRejected() {
        // No Authorization: Bearer header → treated as a cookie-style browser POST, so CSRF applies.
        // No token → 403 from the CSRF filter (before authentication; the SecurityContext has no
        // authentication yet, so AccessDenied resolves to 403, not the entry-point 401).
        webTestClient.post().uri("/auth/logout").exchange().expectStatus().isForbidden();
    }

    @Test
    void cookieStylePostWithMatchingCsrfTokenPassesCsrf() {
        // Obtain a token + the cookie it was minted into, then replay both on the POST. CSRF now
        // passes; the request is unauthenticated so the entry point returns 401 (NOT the CSRF 403).
        FluxExchangeResultHolder safe = exchangeForCsrf();
        String token = safe.token();
        String cookieHeader = XSRF_COOKIE + "=" + token;

        webTestClient
            .post()
            .uri("/auth/logout")
            .header(HttpHeaders.COOKIE, cookieHeader)
            .header(XSRF_HEADER, token)
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }

    @Test
    void cookieStylePostWithMismatchedCsrfTokenIsRejected() {
        FluxExchangeResultHolder safe = exchangeForCsrf();
        String token = safe.token();

        webTestClient
            .post()
            .uri("/auth/logout")
            .header(HttpHeaders.COOKIE, XSRF_COOKIE + "=" + token)
            .header(XSRF_HEADER, "totally-different-value")
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    private record FluxExchangeResultHolder(String token) {}

    private FluxExchangeResultHolder exchangeForCsrf() {
        return new FluxExchangeResultHolder(fetchCsrfToken());
    }

    /**
     * Performs a safe GET on a public resource-server-chain endpoint and extracts the XSRF-TOKEN
     * cookie value the CsrfCookieFilter wrote. {@code /identity-providers} is permitAll and lives on
     * the resource-server chain (where CSRF + the cookie filter are configured), unlike {@code
     * /auth/error} which is owned by the oauth2Login chain.
     */
    private String fetchCsrfToken() {
        var result = webTestClient
            .get()
            .uri("/identity-providers")
            .exchange()
            .expectStatus()
            .isOk()
            .returnResult(Void.class);
        List<ResponseCookie> cookies = result.getResponseCookies().get(XSRF_COOKIE);
        assertThat(cookies).as("XSRF-TOKEN cookie must be issued").isNotEmpty();
        return cookies.get(0).getValue();
    }
}
