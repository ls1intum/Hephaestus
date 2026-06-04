package de.tum.cit.aet.hephaestus.core.auth;

import java.net.URI;
import java.time.Duration;
import java.util.List;
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
 * @param deleteCooldown  GDPR Art. 17 soft-delete cooldown. A DELETING account is invisible but
 *                        recoverable for this window; once {@code deleted_at} is older than this,
 *                        {@code AccountHardDeleteSweeper} hard-deletes the account (cascading its
 *                        auth rows) and flips status to DELETED. Default 48h.
 * @param github          Default GitHub OAuth login app. The login registration is built
 *                        only when a client id is configured (blank = provider omitted, no
 *                        crash) — unlike {@code spring.security.oauth2.client.*}, which fails
 *                        Boot's non-empty-client-id validation on a no-credentials boot.
 * @param gitlabLrz       Default gitlab.lrz.de OAuth login app (same blank-tolerant rule).
 * @param bootstrapAdmins Instance super-admin (APP_ADMIN) allowlist of {@code <registrationId>:<who>}.
 *                        {@code who} is either {@code @username} (recommended, readable — e.g.
 *                        {@code gitlab-lrz:@m.mustermann}, matched against the git login) or the
 *                        stable numeric {@code subject} (e.g. {@code github:1234567}, reclaim-proof,
 *                        best on public github.com). A listed identity is promoted to APP_ADMIN on
 *                        login (idempotent, promote-only — never demotes). Matched on the authenticated
 *                        identity, NEVER email (so it is not nOAuth-vulnerable); the {@code @username}
 *                        form's only residual risk is username reclaim (negligible on institutional
 *                        gitlab.lrz.de, hence the default there; prefer {@code subject} on github.com).
 *                        This is the operator-scoped source of truth for "who runs this instance",
 *                        deliberately separate from per-workspace roles (those derive from SCM team /
 *                        WorkspaceMembership). Empty by default.
 * @param bootstrapToken  Optional one-time break-glass token. When non-blank it enables
 *                        {@code POST /auth/bootstrap-admin}, which promotes the authenticated
 *                        caller to APP_ADMIN exactly while NO active admin exists (self-disables
 *                        afterwards). Proof-of-control is deployment access; deliver out-of-band,
 *                        never log it. Blank = endpoint disabled (404). Prefer {@code bootstrapAdmins};
 *                        keep this as the lockout safety net.
 * @param impersonationMaxLifetime Absolute ceiling on an impersonation session ({@code imp_exp}),
 *                        enforced on {@code POST /auth/refresh}. With no silent-refresh caller today
 *                        the de-facto bound is already {@code accessTtl}; this only binds once refresh
 *                        is wired. See {@code docs/contributor/instance-admin.md}.
 */
@ConfigurationProperties(prefix = "hephaestus.auth")
public record AuthProperties(
    @DefaultValue("http://localhost:8080") URI issuer,
    @DefaultValue("hephaestus-spa") String audience,
    @DefaultValue("15m") Duration accessTtl,
    @DefaultValue(DEFAULT_COOKIE_NAME) String cookieName,
    @DefaultValue("") String stateCookieKey,
    @DefaultValue("48h") Duration deleteCooldown,
    @DefaultValue GithubLogin github,
    @DefaultValue GitlabLrzLogin gitlabLrz,
    @DefaultValue List<String> bootstrapAdmins,
    @DefaultValue("") String bootstrapToken,
    @DefaultValue("1h") Duration impersonationMaxLifetime
) {
    /**
     * Access-token cookie name. The {@code __Host-} prefix forces Secure + host-only (no Domain),
     * so the browser drops it if a proxy injects a Domain attribute. Single source of truth — also
     * referenced by {@code SecurityConfig}'s CSRF cookie check so the two cannot drift.
     */
    public static final String DEFAULT_COOKIE_NAME = "__Host-HEPHAESTUS_AT";

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
