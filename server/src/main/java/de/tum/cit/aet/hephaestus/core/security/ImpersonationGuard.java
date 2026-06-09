package de.tum.cit.aet.hephaestus.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Read-only-by-default enforcement for impersonation sessions.
 *
 * <p>When the authenticated JWT carries an {@code act} claim (set by
 * {@link de.tum.cit.aet.hephaestus.core.auth.impersonation.ImpersonationService}), any
 * non-safe HTTP method is rejected with 403 UNLESS the operator opted into writes by
 * sending {@code X-Impersonation-Allow-Writes: true}. The SPA sets that confirmation header
 * only after a second, explicit click-through, and every such request is still audited at the
 * controller layer.
 *
 * <p>Safe methods (GET / HEAD / OPTIONS) always pass — impersonation is primarily a
 * "see what they see" tool. The session-lifecycle escape endpoints ({@link #LIFECYCLE_ESCAPE} —
 * exit / logout / refresh) also always pass, so the operator can never be trapped in a read-only
 * session by the very guard meant to contain it.
 *
 * <p><strong>Threat-model honesty.</strong> This is an <em>accidental-write guardrail</em>, not a
 * hardened authorization control. An operator who holds an impersonation token already wields the
 * target's full privileges; the header merely forces a deliberate, audited second step so a stray
 * write during a "view as user" session does not happen by reflex. It does not — and cannot — defend
 * against a malicious operator, who can simply send the header. The real controls are who may mint an
 * impersonation token ({@code ImpersonationService}, admin-gated) and the audit trail.
 *
 * <p>Registered on the resource-server chain in {@code SecurityConfig} via
 * {@code addFilterAfter(impersonationGuard, AuthorizationFilter.class)} — i.e. it runs after
 * authentication so the {@link SecurityContextHolder} already holds the validated
 * {@link JwtAuthenticationToken} and its {@code act} claim is visible. On breach it returns a
 * 403 RFC 9457 {@code application/problem+json} body, matching the centralized error contract
 * (see {@code docs/contributor/api-error-handling.md}).
 *
 * <p>Deliberately NOT a {@code @Component}: a Spring-bean {@code Filter} would be auto-registered by
 * Boot on the root servlet chain and run on EVERY request (including the worker-hub / webhook chains)
 * outside the security-filter ordering. Instead {@code SecurityConfig} constructs it and registers it
 * only on the resource-server chain after the {@code AuthorizationFilter}.
 */
public class ImpersonationGuard extends OncePerRequestFilter {

    public static final String ALLOW_WRITES_HEADER = "X-Impersonation-Allow-Writes";

    /**
     * Session-lifecycle "escape" endpoints that MUST stay reachable during an impersonation session.
     * All three are POSTs (non-safe methods), so without this carve-out the read-only write-block
     * below would 403 them — trapping the operator in the impersonated session until the token TTL
     * expires, and (for exit) silently dropping the {@code IMPERSONATION_END} audit row. These verbs
     * only reduce or preserve privilege — exit drops the {@code act} claim, logout revokes the token,
     * refresh re-issues the same claims — so allowing them unconditionally cannot escalate. CSRF still
     * applies (they are cookie POSTs on the resource-server chain), and the privilege-granting
     * {@code POST /auth/impersonate} (begin) is deliberately NOT listed, so it stays guarded.
     */
    public static final RequestMatcher LIFECYCLE_ESCAPE = new OrRequestMatcher(
        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/auth/impersonate:exit"),
        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/auth/logout"),
        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/auth/refresh")
    );

    private static final Logger log = LoggerFactory.getLogger(ImpersonationGuard.class);

    private final ObjectMapper objectMapper;

    public ImpersonationGuard(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        if (isImpersonating() && !isSafe(request) && !writesAllowed(request) && !LIFECYCLE_ESCAPE.matches(request)) {
            log.warn(
                "auth.impersonation: blocked {} {} — impersonation session is read-only (no allow-writes header)",
                request.getMethod(),
                request.getRequestURI()
            );
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "Impersonation sessions are read-only. Enable writes explicitly to proceed."
            );
            problem.setTitle("Forbidden");
            problem.setProperty("code", "impersonation_read_only");
            problem.setInstance(URI.create(request.getRequestURI()));

            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(problem));
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isImpersonating() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            return jwt.hasClaim("act");
        }
        return false;
    }

    private static boolean isSafe(HttpServletRequest request) {
        String method = request.getMethod();
        return (
            HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method) || HttpMethod.OPTIONS.matches(method)
        );
    }

    private static boolean writesAllowed(HttpServletRequest request) {
        return "true".equalsIgnoreCase(request.getHeader(ALLOW_WRITES_HEADER));
    }
}
