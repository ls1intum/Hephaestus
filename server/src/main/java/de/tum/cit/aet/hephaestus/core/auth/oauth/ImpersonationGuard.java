package de.tum.cit.aet.hephaestus.core.auth.oauth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Read-only-by-default enforcement for impersonation sessions.
 *
 * <p>When the authenticated JWT carries an {@code act} claim (set by
 * {@link de.tum.cit.aet.hephaestus.core.auth.impersonation.ImpersonationService}), any
 * non-safe HTTP method is rejected with 403 UNLESS the operator opted into writes by
 * sending {@code X-Impersonation-Allow-Writes: true}. The SPA sets that header only after
 * a second click-through confirmation, and every such request is still audited at the
 * controller layer.
 *
 * <p>Safe methods (GET / HEAD / OPTIONS) always pass — impersonation is primarily a
 * "see what they see" tool.
 *
 * <p>Runs on the resource-server chain after the bearer/cookie token is resolved, so the
 * {@link SecurityContextHolder} already holds the {@link JwtAuthenticationToken}.
 */
public class ImpersonationGuard extends OncePerRequestFilter {

    public static final String ALLOW_WRITES_HEADER = "X-Impersonation-Allow-Writes";
    private static final Logger log = LoggerFactory.getLogger(ImpersonationGuard.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        if (isImpersonating() && !isSafe(request) && !writesAllowed(request)) {
            log.warn(
                "auth.impersonation: blocked {} {} — impersonation session is read-only (no allow-writes header)",
                request.getMethod(),
                request.getRequestURI()
            );
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/problem+json");
            response
                .getWriter()
                .write(
                    "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403," +
                    "\"detail\":\"Impersonation sessions are read-only. Enable writes explicitly to proceed.\"," +
                    "\"code\":\"impersonation_read_only\"}"
                );
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
            HttpMethod.GET.matches(method) ||
            HttpMethod.HEAD.matches(method) ||
            HttpMethod.OPTIONS.matches(method)
        );
    }

    private static boolean writesAllowed(HttpServletRequest request) {
        return "true".equalsIgnoreCase(request.getHeader(ALLOW_WRITES_HEADER));
    }
}
