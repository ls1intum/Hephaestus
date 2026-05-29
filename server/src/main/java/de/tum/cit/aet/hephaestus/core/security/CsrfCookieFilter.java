package de.tum.cit.aet.hephaestus.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Forces the deferred {@link CsrfToken} to be loaded on every request so the
 * {@code CookieCsrfTokenRepository} actually writes the {@code XSRF-TOKEN} cookie.
 *
 * <p>With the SS6+ deferred-token model the repository only persists the cookie when something reads
 * {@code csrfToken.getToken()}. A stateless SPA that authenticates purely via the access-token cookie
 * never triggers that read on a plain {@code GET /user}, so without this filter the SPA would have no
 * {@code XSRF-TOKEN} cookie to echo back and its first POST would 403. Calling {@link CsrfToken#getToken()}
 * here materializes the token (and cookie) on every response. Registered after the
 * {@code BasicAuthenticationFilter} / before the {@code AuthorizationFilter} on the resource-server chain.
 */
public final class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            // Triggers CookieCsrfTokenRepository to render the XSRF-TOKEN cookie.
            csrfToken.getToken();
        }
        chain.doFilter(request, response);
    }
}
