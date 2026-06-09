package de.tum.cit.aet.hephaestus.core.auth.ratelimit;

import de.tum.cit.aet.hephaestus.core.auth.metrics.AuthMetrics;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Token-bucket rate limiter for the hot auth endpoints (ADR 0017 hardening). Sits on the
 * resource-server chain (covers {@code /auth/refresh}, {@code /auth/impersonate}, {@code DELETE
 * /user}) and the oauth2Login chain (covers {@code GET /oauth2/authorization/*}); registered after
 * authentication so the account principal is resolvable from the {@link SecurityContextHolder}.
 *
 * <p>On breach the response is HTTP 429 with an RFC 9457 {@code application/problem+json} body and a
 * {@code Retry-After} header (seconds until the bucket next has a token). Requests that match no
 * configured limit pass through untouched — in particular the worker-hub / webhook paths, which live
 * on a different chain and are additionally guarded here defensively.
 *
 * <p>Which endpoints are limited and how each is keyed is encoded by the {@link Endpoint} enum
 * (namespace + account-vs-IP scope) and applied in {@link #resolveBucketKey}. The namespace prefix
 * guarantees two endpoints never share a bucket even for the same principal; account-scoped limits
 * fall back to the client IP when the request reaches the filter unauthenticated.
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);

    private final AuthRateLimitProperties properties;
    private final BucketResolver bucketResolver;
    private final ObjectMapper objectMapper;
    private final AuthMetrics metrics;

    public AuthRateLimitFilter(
        AuthRateLimitProperties properties,
        BucketResolver bucketResolver,
        ObjectMapper objectMapper,
        AuthMetrics metrics
    ) {
        this.properties = properties;
        this.bucketResolver = bucketResolver;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    /** Identifies which configured limit (if any) applies to a request, and how to key it. */
    private enum Endpoint {
        OAUTH_AUTHORIZATION("oauth-authz", false),
        REFRESH("refresh", true),
        IMPERSONATE("impersonate", true),
        DELETE_USER("delete-user", true),
        // GDPR Art. 20 export: cap POST /user/exports (the async assembly). Account-scoped (JWT sub)
        // with IP fallback — the route requires isAuthenticated(), so sub is normally present.
        EXPORT("export", true);

        private final String namespace;
        /** Whether the limit keys by account (with IP fallback) vs. always by IP. */
        private final boolean accountScoped;

        Endpoint(String namespace, boolean accountScoped) {
            this.namespace = namespace;
            this.accountScoped = accountScoped;
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        if (!properties.enabled()) {
            chain.doFilter(request, response);
            return;
        }

        Endpoint endpoint = match(request);
        if (endpoint == null) {
            chain.doFilter(request, response);
            return;
        }

        AuthRateLimitProperties.Limit limit = limitFor(endpoint);
        String key = resolveBucketKey(endpoint, request);
        ConsumptionProbe probe;
        try {
            Bucket bucket = bucketResolver.resolve(key, configFor(limit));
            probe = bucket.tryConsumeAndReturnRemaining(1);
        } catch (RuntimeException e) {
            // Fail OPEN, deliberately. The bucket store (Postgres-backed in prod) can blip — a DB
            // hiccup or lock-timeout must not turn the auth endpoints into a hard outage, and worse,
            // failing closed here would let an attacker DoS auth by driving the limiter into contention.
            // We surface the degradation as a metric + WARN so it is not silent.
            metrics.recordRateLimitBackendError();
            log.warn("Auth rate limit backend error (failing open): endpoint={} key={}", endpoint, key, e);
            chain.doFilter(request, response);
            return;
        }

        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
        // key is namespaced (no raw account-id PII beyond what already exists in auth logs); safe at WARN.
        log.warn("Auth rate limit exceeded: endpoint={} key={} retryAfterSeconds={}", endpoint, key, retryAfterSeconds);
        // Tag by bucket namespace (oauth-authz/refresh/impersonate/delete-user) — bounded, no PII.
        metrics.recordRateLimitBlocked(endpoint.namespace);
        writeTooManyRequests(request, response, retryAfterSeconds);
    }

    /**
     * Maps {@code (method, path)} to the configured limit, or {@code null} if none applies. Worker-hub
     * and webhook paths are explicitly excluded (defence in depth — they live on another chain).
     */
    @Nullable
    private static Endpoint match(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            path = request.getRequestURI();
        }
        if (path.startsWith("/api/workers/") || path.startsWith("/webhooks/") || path.startsWith("/actuator/")) {
            return null;
        }
        String method = request.getMethod();
        if ("GET".equals(method) && path.startsWith("/oauth2/authorization/")) {
            return Endpoint.OAUTH_AUTHORIZATION;
        }
        if ("POST".equals(method) && path.equals("/auth/refresh")) {
            return Endpoint.REFRESH;
        }
        // Begin-impersonation only. The exit verb (/auth/impersonate:exit) is a separate, non-rate-
        // limited path; equals() (not startsWith) keeps it out of this bucket.
        if ("POST".equals(method) && path.equals("/auth/impersonate")) {
            return Endpoint.IMPERSONATE;
        }
        if ("DELETE".equals(method) && path.equals("/user")) {
            return Endpoint.DELETE_USER;
        }
        // GDPR export: cap ONLY the POST that starts an async full-bundle assembly (the expensive,
        // storage-amplifying op). The download is a cheap ownership+READY-gated blob read — rate-limiting
        // it with the same budget would penalise legitimate polling.
        if ("POST".equals(method) && path.equals("/user/exports")) {
            return Endpoint.EXPORT;
        }
        return null;
    }

    private AuthRateLimitProperties.Limit limitFor(Endpoint endpoint) {
        return switch (endpoint) {
            case OAUTH_AUTHORIZATION -> properties.oauthAuthorization();
            case REFRESH -> properties.refresh();
            case IMPERSONATE -> properties.impersonate();
            case DELETE_USER -> properties.deleteUser();
            case EXPORT -> properties.export();
        };
    }

    /**
     * Derives the bucket key. Account-scoped endpoints prefer the JWT {@code sub}; when the principal
     * is absent (request reached the filter before/without authentication) they fall back to the
     * client IP so an unauthenticated flood is still capped. The namespace prefix guarantees two
     * endpoints never share a bucket even for the same principal.
     */
    String resolveBucketKey(Endpoint endpoint, HttpServletRequest request) {
        if (endpoint.accountScoped) {
            Optional<String> sub = currentSubject();
            if (sub.isPresent()) {
                return endpoint.namespace + ":acct:" + sub.get();
            }
        }
        return endpoint.namespace + ":ip:" + clientIp(request);
    }

    private static Optional<String> currentSubject() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            Jwt jwt = token.getToken();
            return Optional.ofNullable(jwt.getSubject());
        }
        return Optional.empty();
    }

    /**
     * Client IP for IP-keyed buckets, from {@code getRemoteAddr()} only — never a bespoke X-Forwarded-For
     * parse. In prod Tomcat's {@code RemoteIpValve} ({@code forward-headers-strategy: native}, trust pinned
     * by {@code ProxyTrustGuard}) has already validated XFF and rewritten {@code getRemoteAddr()} to the
     * unforgeable client address; a second parse would double-count hops or key off a spoofable entry. In
     * dev it is the direct socket peer. The container is the single source of truth for proxy trust.
     */
    String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        return remote != null ? remote : "unknown";
    }

    /** Builds a Bucket4j configuration: one bandwidth, interval refill (fresh budget per window). */
    private static BucketConfiguration configFor(AuthRateLimitProperties.Limit limit) {
        Bandwidth bandwidth = Bandwidth.classic(limit.capacity(), Refill.intervally(limit.capacity(), limit.period()));
        return BucketConfiguration.builder().addLimit(bandwidth).build();
    }

    private void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response, long retryAfterSeconds)
        throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.TOO_MANY_REQUESTS,
            "Rate limit exceeded. Retry after " + retryAfterSeconds + " seconds."
        );
        problem.setTitle("Too Many Requests");
        problem.setProperty("retryAfterSeconds", retryAfterSeconds);
        problem.setInstance(java.net.URI.create(request.getRequestURI()));

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
