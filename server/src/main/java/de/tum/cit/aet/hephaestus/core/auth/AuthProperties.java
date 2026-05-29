package de.tum.cit.aet.hephaestus.core.auth;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Runtime configuration for the core.auth module.
 *
 * <p>All knobs that vary by environment live here. Defaults are tuned for production;
 * integration tests override via {@code @TestPropertySource}.
 *
 * @param issuer          Canonical issuer URI; populates the {@code iss} claim and
 *                        {@code /.well-known/openid-configuration}.
 * @param audience        Default {@code aud} claim for SPA cookies.
 * @param accessTtl       Cookie-JWT lifetime.
 * @param cookieName      Access-token cookie name (the {@code __Host-} prefix is
 *                        browser-enforced — must be {@code Secure}, no {@code Domain},
 *                        {@code Path=/}).
 * @param stateCookieKey  Base64-encoded 32-byte AES key used to seal the OAuth state
 *                        cookie and the auth-intent cookie. Required in non-dev profiles.
 *                        Bean wiring will generate an ephemeral key (and log a warning)
 *                        if this is left blank — fine for local dev, fatal in prod.
 * @param github          Default GitHub OAuth login app. The login registration is built
 *                        only when a client id is configured (blank = provider omitted, no
 *                        crash) — unlike {@code spring.security.oauth2.client.*}, which fails
 *                        Boot's non-empty-client-id validation on a no-credentials boot.
 * @param gitlabLrz       Default gitlab.lrz.de OAuth login app (same blank-tolerant rule).
 */
@ConfigurationProperties(prefix = "hephaestus.auth")
public record AuthProperties(
    @DefaultValue("http://localhost:38080") URI issuer,
    @DefaultValue("hephaestus-spa") String audience,
    @DefaultValue("15m") Duration accessTtl,
    @DefaultValue("__Host-HEPHAESTUS_AT") String cookieName,
    @DefaultValue("") String stateCookieKey,
    @DefaultValue GithubLogin github,
    @DefaultValue GitlabLrzLogin gitlabLrz
) {
    /**
     * Default GitHub OAuth login provider credentials. The registration is wired only when
     * {@link #configured()} so CI / specs / worker-only pods boot without OAuth credentials.
     */
    public record GithubLogin(@DefaultValue("") String clientId, @DefaultValue("") String clientSecret) {
        public boolean configured() {
            return !clientId.isBlank();
        }
    }

    /**
     * Default gitlab.lrz.de OAuth login provider credentials plus the instance base URL.
     */
    public record GitlabLrzLogin(
        @DefaultValue("") String clientId,
        @DefaultValue("") String clientSecret,
        @DefaultValue("https://gitlab.lrz.de") URI baseUrl
    ) {
        public boolean configured() {
            return !clientId.isBlank();
        }
    }
}
