package de.tum.cit.aet.hephaestus.core.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/**
 * CSRF request handler for a stateless cookie double-submit SPA (ADR 0017).
 *
 * <p>The webapp reads the <em>raw</em> {@code __Host-XSRF-TOKEN} cookie value and echoes it back in the
 * {@code X-XSRF-TOKEN} header on every state-changing request (see {@code webapp/src/main.tsx} and
 * {@code webapp/src/integrations/auth/authClient.ts}). It does NOT understand Spring's
 * BREACH-mitigation XOR masking. This handler therefore:
 *
 * <ul>
 *   <li><b>Renders</b> the token via {@link XorCsrfTokenRequestAttributeHandler} so the value
 *       exposed to the page (and any server-side template) is per-response masked — BREACH
 *       protection is preserved for anything that reads the request attribute.</li>
 *   <li><b>Resolves</b> a token presented in the {@code X-XSRF-TOKEN} <i>header</i> as a plain,
 *       unmasked value ({@link CsrfTokenRequestAttributeHandler}) — matching what the SPA sends
 *       straight from the cookie. A token presented as a request <i>parameter</i> (classic form
 *       post) is still treated as masked. This is the documented Spring Security SPA idiom.</li>
 * </ul>
 *
 * <p>Pairing this with {@code CookieCsrfTokenRepository.withHttpOnlyFalse()} stores the raw token in
 * the JS-readable {@code __Host-XSRF-TOKEN} cookie, closing the SameSite=Lax-only gap: a cross-site forged
 * POST cannot read the cookie and so cannot supply the matching header.
 */
public final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        // Render via the XOR handler: the request attribute carries a per-response masked value, and
        // the repository persists the raw token to the cookie when the token is first resolved.
        this.xor.handle(request, response, csrfToken);
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        // The SPA sends the raw cookie value in the header → resolve it plainly. Form parameters
        // (legacy) remain XOR-masked.
        if (StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))) {
            return this.plain.resolveCsrfTokenValue(request, csrfToken);
        }
        return this.xor.resolveCsrfTokenValue(request, csrfToken);
    }
}
