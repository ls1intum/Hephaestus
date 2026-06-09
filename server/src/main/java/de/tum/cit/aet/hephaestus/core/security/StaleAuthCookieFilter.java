package de.tum.cit.aet.hephaestus.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Evicts a present-but-invalid access cookie BEFORE {@code BearerTokenAuthenticationFilter} tries to
 * authenticate it, so a stale cookie cannot 401 a public endpoint.
 *
 * <h2>Why</h2>
 * The SPA's {@code __Host-HEPHAESTUS_AT} cookie is sent automatically by the browser on every request.
 * Spring's bearer filter resolves it and, if the token is invalid/expired, the entry point commits a
 * 401 BEFORE the authorization rules (e.g. {@code permitAll} on {@code GET /identity-providers}) are
 * ever consulted. The login page then can't load its sign-in options while a stale cookie lingers —
 * the common case being a cookie left after the session expired, or (in dev) one signed by a key that
 * changed across a restart.
 *
 * <h2>What</h2>
 * This filter does a LOCAL-only validation (signature + exp + iss/aud via
 * {@code RevocationAwareJwtDecoder#localSignatureDecoder} — deliberately NO {@code issued_jwt}
 * revocation lookup, so the authenticated hot path keeps its single DB read). On failure it:
 * <ol>
 *   <li>flags the request so {@code CookieBearerTokenResolver} ignores the cookie — the request then
 *       proceeds anonymously, so a {@code permitAll} endpoint serves instead of 401ing, while a
 *       protected endpoint still 401s as unauthenticated (the correct "logged out" signal, not a
 *       500-ish bad-token error); and</li>
 *   <li>clears the cookie ({@code Max-Age=0}) so the browser stops resending the dead token — the
 *       state self-heals after one request.</li>
 * </ol>
 * A cookie that passes local validation is left untouched: the bearer filter authenticates it as
 * before (including the revocation check). A revoked-but-still-locally-valid token therefore still
 * 401s at the bearer filter — an acceptable, rare residual, since a logout already clears the cookie.
 */
public class StaleAuthCookieFilter extends OncePerRequestFilter {

    /** Request attribute set when the access cookie failed local validation; read by the resolver. */
    public static final String COOKIE_INVALID_ATTRIBUTE = StaleAuthCookieFilter.class.getName() + ".COOKIE_INVALID";

    private final String cookieName;
    private final JwtDecoder localDecoder;

    public StaleAuthCookieFilter(String cookieName, JwtDecoder localDecoder) {
        this.cookieName = cookieName;
        this.localDecoder = localDecoder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String token = readAuthCookie(request);
        if (token != null) {
            try {
                localDecoder.decode(token);
            } catch (JwtException invalid) {
                // Stale cookie (bad signature / expired / wrong iss-aud): hide it from the bearer filter
                // for this request and clear it so the browser drops it.
                request.setAttribute(COOKIE_INVALID_ATTRIBUTE, Boolean.TRUE);
                clearCookie(response);
            }
        }
        chain.doFilter(request, response);
    }

    private String readAuthCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                String value = cookie.getValue();
                return (value != null && !value.isBlank()) ? value : null;
            }
        }
        return null;
    }

    /** Mirror {@code AuthSessionService.clearCookie}: same attributes so the browser actually drops it. */
    private void clearCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(cookieName, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }
}
