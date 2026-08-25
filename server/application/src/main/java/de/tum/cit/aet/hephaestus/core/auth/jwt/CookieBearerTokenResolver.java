package de.tum.cit.aet.hephaestus.core.auth.jwt;

import de.tum.cit.aet.hephaestus.core.auth.AuthProperties;
import de.tum.cit.aet.hephaestus.core.security.StaleAuthCookieFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/**
 * Resolves the resource-server bearer token from the SPA's {@code __Host-HEPHAESTUS_AT} cookie
 * (name from {@link AuthProperties#cookieName()}), falling back to the standard
 * {@code Authorization: Bearer} header (ADR 0017).
 *
 * <p>The SPA authenticates by an HttpOnly access-token cookie set on the OAuth success path
 * ({@code HephaestusAuthSuccessHandler}) and sent automatically with {@code credentials:"include"};
 * it never sends an {@code Authorization} header. The framework default
 * ({@link DefaultBearerTokenResolver}) only reads the header, so without this resolver every
 * browser request is 401. Worker / API / bearer-token integration clients still authenticate via
 * the header fallback.
 *
 * <p>Order is cookie-first: a browser request carries only the cookie, while a non-browser client
 * carries only the header, so they never collide in practice. CSRF (double-submit) covers the
 * cookie-authenticated, state-changing browser path — see {@code SecurityConfig#requiresCsrf}.
 */
public class CookieBearerTokenResolver implements BearerTokenResolver {

    private final String cookieName;
    private final DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();

    public CookieBearerTokenResolver(AuthProperties properties) {
        this.cookieName = properties.cookieName();
    }

    @Override
    public String resolve(HttpServletRequest request) {
        // A stale cookie already rejected by StaleAuthCookieFilter: ignore it so this request stays
        // anonymous (a permitAll endpoint serves instead of 401ing on the dead token). The header
        // fallback below still applies for worker/API/bearer clients.
        if (Boolean.TRUE.equals(request.getAttribute(StaleAuthCookieFilter.COOKIE_INVALID_ATTRIBUTE))) {
            return headerResolver.resolve(request);
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookieName.equals(cookie.getName())) {
                    String value = cookie.getValue();
                    if (value != null && !value.isBlank()) {
                        return value;
                    }
                }
            }
        }
        // No cookie token → fall back to the standard Authorization: Bearer header (worker/API/tests).
        return headerResolver.resolve(request);
    }
}
