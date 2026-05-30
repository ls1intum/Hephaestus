package de.tum.cit.aet.hephaestus.core.auth.ratelimit;

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
 * <p>Keying ({@link #resolveBucketKey}):
 * <ul>
 *   <li>{@code GET /oauth2/authorization/*} → {@code oauth-authz:ip:<client-ip>} (pre-auth, no
 *       principal yet).</li>
 *   <li>{@code POST /auth/refresh} → {@code refresh:acct:<sub>}, falling back to
 *       {@code refresh:ip:<client-ip>} when unauthenticated.</li>
 *   <li>{@code POST /auth/impersonate} (begin only — not {@code impersonate:exit}) →
 *       {@code impersonate:acct:<admin-sub>}, IP fallback.</li>
 *   <li>{@code DELETE /user} → {@code delete-user:acct:<sub>}, IP fallback.</li>
 * </ul>
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);

    private final AuthRateLimitProperties properties;
    private final BucketResolver bucketResolver;
    private final ObjectMapper objectMapper;

    public AuthRateLimitFilter(
        AuthRateLimitProperties properties,
        BucketResolver bucketResolver,
        ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.bucketResolver = bucketResolver;
        this.objectMapper = objectMapper;
    }

    /** Identifies which configured limit (if any) applies to a request, and how to key it. */
    private enum Endpoint {
        OAUTH_AUTHORIZATION("oauth-authz", false),
        REFRESH("refresh", true),
        IMPERSONATE("impersonate", true),
        DELETE_USER("delete-user", true);

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
        Bucket bucket = bucketResolver.resolve(key, configFor(limit));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
        // key is namespaced (no raw account-id PII beyond what already exists in auth logs); safe at WARN.
        log.warn("Auth rate limit exceeded: endpoint={} key={} retryAfterSeconds={}", endpoint, key, retryAfterSeconds);
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
        return null;
    }

    private AuthRateLimitProperties.Limit limitFor(Endpoint endpoint) {
        return switch (endpoint) {
            case OAUTH_AUTHORIZATION -> properties.oauthAuthorization();
            case REFRESH -> properties.refresh();
            case IMPERSONATE -> properties.impersonate();
            case DELETE_USER -> properties.deleteUser();
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
     * Resolves the client IP for IP-keyed buckets from {@code getRemoteAddr()} — and ONLY that.
     *
     * <p><strong>Why not parse X-Forwarded-For here.</strong> Production runs with
     * {@code server.forward-headers-strategy: native} (see {@code application-prod.yml}), so Tomcat's
     * {@code RemoteIpValve} runs BEFORE this filter: it consumes {@code X-Forwarded-For}, validates it
     * against the configured trusted-proxy set, and rewrites {@code getRemoteAddr()} to the real client
     * address. A second, bespoke XFF re-parse in this filter would then double-count the proxy hops and
     * key the bucket off the wrong (or a spoofable) entry — defeating the very pre-auth limit it guards.
     * Delegating to the container is the single source of truth for proxy trust; {@code getRemoteAddr()}
     * is the only value a client cannot forge once the valve has run.
     *
     * <p>In dev (no forward-headers strategy) {@code getRemoteAddr()} is the direct socket peer, which
     * is also correct: there is no trusted proxy to attribute the request through.
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
