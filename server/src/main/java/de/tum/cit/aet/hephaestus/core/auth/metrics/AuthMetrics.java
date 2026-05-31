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

    private static final String LOGIN_METRIC = "auth.login";
    private static final String RATELIMIT_BLOCKED_METRIC = "auth.ratelimit.blocked";

    private final MeterRegistry registry;
    private final Counter loginSuccess;
    private final Counter loginFailure;
    private final Timer tokenRefresh;

    public AuthMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.loginSuccess = login(registry, LoginResult.SUCCESS);
        this.loginFailure = login(registry, LoginResult.FAILURE);
        this.tokenRefresh = Timer.builder("auth.token.refresh")
            .description("Wall-clock latency of the access-token rotation in AuthSessionService.refresh.")
            .publishPercentileHistogram()
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
}
