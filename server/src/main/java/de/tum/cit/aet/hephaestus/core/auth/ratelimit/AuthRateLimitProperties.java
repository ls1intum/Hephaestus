package de.tum.cit.aet.hephaestus.core.auth.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Rate-limit knobs for the hot auth endpoints, bound to {@code hephaestus.auth.rate-limit.*}.
 *
 * <p>Each limit is a token bucket of {@code capacity} tokens that refills the full capacity once
 * per {@code period} (interval refill — a fresh budget every window, not a trickle). Defaults match
 * the security-hardening spec (ADR 0017 follow-up):
 *
 * <ul>
 *   <li>{@code GET /oauth2/authorization/*} — 20/min, keyed by client IP</li>
 *   <li>{@code POST /auth/refresh} — 60/min, keyed by account (JWT sub; IP fallback)</li>
 *   <li>{@code POST /auth/impersonate} — 10/min, keyed by admin account</li>
 *   <li>{@code DELETE /user} — 3/hour, keyed by account</li>
 * </ul>
 *
 * <p>Set {@code enabled=false} to bypass the filter entirely (e.g. load tests). The limits are
 * shared across replicas when a Postgres-backed {@code ProxyManager} is wired (production); see
 * {@link AuthRateLimitConfig} for the per-replica fallback trade-off.
 *
 * <p>{@code trustedProxyCount} bounds how many rightmost {@code X-Forwarded-For} hops the deployment
 * owns (default 1 — always behind Coolify). The IP-keyed buckets resolve the client as the entry
 * just left of those trusted hops, so a spoofed leftmost XFF value cannot mint fresh buckets. Set to
 * the real number of reverse proxies in front of the app; {@code 0} disables XFF trust entirely
 * (key purely off {@code getRemoteAddr()}).
 */
@ConfigurationProperties(prefix = "hephaestus.auth.rate-limit")
public record AuthRateLimitProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("1") int trustedProxyCount,
    @DefaultValue Limit oauthAuthorization,
    @DefaultValue Limit refresh,
    @DefaultValue Limit impersonate,
    @DefaultValue Limit deleteUser
) {
    public AuthRateLimitProperties {
        // Bind nulls (a partially-specified YAML block) to the spec defaults so a misconfigured
        // sub-key never silently disables a limit.
        oauthAuthorization = oauthAuthorization != null ? oauthAuthorization : Limit.of(20, Duration.ofMinutes(1));
        refresh = refresh != null ? refresh : Limit.of(60, Duration.ofMinutes(1));
        impersonate = impersonate != null ? impersonate : Limit.of(10, Duration.ofMinutes(1));
        deleteUser = deleteUser != null ? deleteUser : Limit.of(3, Duration.ofHours(1));
        if (trustedProxyCount < 0) {
            throw new IllegalArgumentException("trustedProxyCount must be >= 0, got: " + trustedProxyCount);
        }
    }

    /**
     * A single token-bucket limit: {@code capacity} requests per {@code period}.
     *
     * @param capacity max requests permitted within one {@code period}; must be {@code >= 1}.
     * @param period   refill window; must be positive.
     */
    public record Limit(@DefaultValue("20") long capacity, @DefaultValue("1m") Duration period) {
        public Limit {
            if (capacity < 1) {
                throw new IllegalArgumentException("rate-limit capacity must be >= 1, got: " + capacity);
            }
            if (period == null || period.isZero() || period.isNegative()) {
                throw new IllegalArgumentException("rate-limit period must be positive, got: " + period);
            }
        }

        static Limit of(long capacity, Duration period) {
            return new Limit(capacity, period);
        }
    }
}
