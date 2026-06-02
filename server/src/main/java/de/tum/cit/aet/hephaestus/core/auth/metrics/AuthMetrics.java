package de.tum.cit.aet.hephaestus.core.auth.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Micrometer instruments for the auth module (ADR 0017 hardening — the A→A+ observability gap).
 *
 * <p>Naming follows the rest of the codebase ({@code mentor.turn.*}, {@code agent.job.*}): dot-case
 * Java identifiers that Micrometer's Prometheus renderer maps to {@code auth_login_total{result=…}}
 * etc. Tag values are a fixed enum so cardinality stays bounded.
 *
 * <h2>Where each instrument fires (and why <em>not</em> a global event listener)</h2>
 * Spring Security's resource-server {@code BearerTokenAuthenticationFilter} publishes an
 * {@code AuthenticationSuccessEvent} on <strong>every</strong> cookie-JWT-bearing API request — so a
 * listener on that event would count API traffic, not logins. The interactive oauth2Login filter
 * (an {@code AbstractAuthenticationProcessingFilter}) instead publishes
 * {@code InteractiveAuthenticationSuccessEvent} on success and an
 * {@code AbstractAuthenticationFailureEvent} on a failed IdP exchange, and the bearer filter does
 * <em>not</em>. {@code AuthLoginEventMetrics} listens for exactly those two — the precise, login-only
 * signal — and increments:
 * <ul>
 *   <li>{@code auth.login{result=success}} — interactive OAuth login completed.</li>
 *   <li>{@code auth.login{result=failure}} — the oauth2Login IdP exchange failed.</li>
 * </ul>
 * {@code auth.ratelimit.blocked{bucket=…}} is incremented on the 429 path of
 * {@code AuthRateLimitFilter}; the bucket namespace ({@code oauth-authz}, {@code refresh}, …) is a
 * bounded tag. {@code auth.token.refresh} times the token-rotation critical section in
 * {@code AuthSessionService.refresh}.
 */
@Component
public class AuthMetrics {

    /** Outcome tagged on {@code auth.login}. Fixed set → bounded cardinality. */
    public enum LoginResult {
        SUCCESS("success"),
        FAILURE("failure");

        private final String tag;

        LoginResult(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }
    }

    /**
     * Terminal outcome tagged on {@code auth.token.refresh.result}. The {@code auth.token.refresh}
     * Timer alone wraps the whole method, so a {@code revoked==0} rotation race and a SUSPENDED /
     * DELETING / DELETED early-return are indistinguishable from a real re-mint. This split lets an
     * operator alert on an abnormal {@code noop} (token-reuse / replay) or {@code suspended} rate
     * without inferring it from latency. Fixed set → bounded cardinality.
     */
    public enum RefreshResult {
        /** A fresh access token was minted and set on the cookie. */
        SUCCESS("success"),
        /** Conditional revoke affected 0 rows (a concurrent refresh/logout already rotated the jti). */
        NOOP("noop"),
        /** Account was not ACTIVE (SUSPENDED / DELETING / DELETED / missing) — session ended, no re-mint. */
        SUSPENDED("suspended"),
        /** The rotation threw after the presenting token was revoked (re-mint / cookie failure). */
        ERROR("error");

        private final String tag;

        RefreshResult(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }
    }

    private static final String LOGIN_METRIC = "auth.login";
    private static final String RATELIMIT_BLOCKED_METRIC = "auth.ratelimit.blocked";
    private static final String REFRESH_RESULT_METRIC = "auth.token.refresh.result";
    private static final String REVOCATION_CHECK_FAILED_METRIC = "auth.revocation.check_failed";

    private final MeterRegistry registry;
    private final Counter loginSuccess;
    private final Counter loginFailure;
    private final Timer tokenRefresh;
    private final Counter revocationCheckFailed;

    public AuthMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.loginSuccess = login(registry, LoginResult.SUCCESS);
        this.loginFailure = login(registry, LoginResult.FAILURE);
        this.tokenRefresh = Timer.builder("auth.token.refresh")
            .description("Wall-clock latency of the access-token rotation in AuthSessionService.refresh.")
            .publishPercentileHistogram()
            .register(registry);
        this.revocationCheckFailed = Counter.builder(REVOCATION_CHECK_FAILED_METRIC)
            .description(
                "Fail-closed revocation lookups in RevocationAwareJwtDecoder (DB unreachable → 401). " +
                    "A spike means a DB outage is mass-rejecting otherwise-valid cookie-JWTs."
            )
            .register(registry);
    }

    private static Counter login(MeterRegistry registry, LoginResult result) {
        return Counter.builder(LOGIN_METRIC)
            .description("Interactive OAuth logins, tagged by terminal result.")
            .tag("result", result.tag())
            .register(registry);
    }

    public void recordLogin(LoginResult result) {
        switch (result) {
            case SUCCESS -> loginSuccess.increment();
            case FAILURE -> loginFailure.increment();
        }
    }

    /**
     * Count one rate-limit 429. {@code bucket} is the endpoint namespace
     * ({@code oauth-authz}/{@code refresh}/{@code impersonate}/{@code delete-user}) — bounded, no PII.
     * The per-(bucket) Counter is registered lazily; the namespace set is tiny so this is effectively
     * a fixed handful of meters.
     */
    public void recordRateLimitBlocked(String bucket) {
        Counter.builder(RATELIMIT_BLOCKED_METRIC)
            .description("Auth requests rejected with HTTP 429 by AuthRateLimitFilter, tagged by bucket namespace.")
            .tag("bucket", bucket)
            .register(registry)
            .increment();
    }

    public Timer.Sample startRefreshTimer() {
        return Timer.start(registry);
    }

    public void stopRefreshTimer(Timer.Sample sample) {
        sample.stop(tokenRefresh);
    }

    /**
     * Count one token-refresh by its terminal {@code result} ({@code success}/{@code noop}/
     * {@code suspended}). Lazily-registered per-result Counter; the result set is a fixed enum so
     * this is a fixed handful of meters.
     */
    public void recordRefreshResult(RefreshResult result) {
        Counter.builder(REFRESH_RESULT_METRIC)
            .description("Token-refresh attempts in AuthSessionService.refresh, tagged by terminal result.")
            .tag("result", result.tag())
            .register(registry)
            .increment();
    }

    /**
     * Count one fail-closed revocation lookup ({@code RevocationAwareJwtDecoder} could not reach the
     * DB and surfaced a 401). Makes a DB-outage mass-401 observable instead of only logged.
     */
    public void recordRevocationCheckFailed() {
        revocationCheckFailed.increment();
    }
}
