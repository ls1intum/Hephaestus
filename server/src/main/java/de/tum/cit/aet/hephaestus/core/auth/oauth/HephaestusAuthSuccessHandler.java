package de.tum.cit.aet.hephaestus.core.auth.oauth;

import de.tum.cit.aet.hephaestus.core.auth.AuthProperties;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.jwt.HephaestusJwtIssuer;
import de.tum.cit.aet.hephaestus.core.auth.jwt.JwtPrincipalFactory;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.transaction.annotation.Transactional;

/**
 * Production success handler for {@code oauth2Login}. Delegates account resolution / JIT /
 * linking to {@link AccountProvisioningService}, then mints a Hephaestus cookie-JWT via
 * {@link HephaestusJwtIssuer} and redirects to the validated {@code returnTo}.
 *
 * <p>Account lookup is always {@code (provider, subject)} — never email (nOAuth defence).
 */
public class HephaestusAuthSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(HephaestusAuthSuccessHandler.class);

    private final AccountProvisioningService provisioningService;
    private final HephaestusJwtIssuer jwtIssuer;
    private final JwtPrincipalFactory principalFactory;
    private final AuthIntentCookie authIntentCookie;
    private final AuthProperties authProperties;
    private final Clock clock;

    public HephaestusAuthSuccessHandler(
        AccountProvisioningService provisioningService,
        HephaestusJwtIssuer jwtIssuer,
        JwtPrincipalFactory principalFactory,
        AuthIntentCookie authIntentCookie,
        AuthProperties authProperties,
        Clock clock
    ) {
        this.provisioningService = provisioningService;
        this.jwtIssuer = jwtIssuer;
        this.principalFactory = principalFactory;
        this.authIntentCookie = authIntentCookie;
        this.authProperties = authProperties;
        this.clock = clock;
        setAlwaysUseDefaultTargetUrl(false);
        setDefaultTargetUrl("/");
    }

    @Override
    @Transactional
    public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) throws IOException {
        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
            log.error("auth.success: unexpected authentication type {}", authentication.getClass());
            getRedirectStrategy().sendRedirect(request, response, "/auth/error?code=unexpected_auth_type");
            return;
        }
        OAuth2User principal = token.getPrincipal();
        String registrationId = token.getAuthorizedClientRegistrationId();
        String subject = principal.getName();
        if (subject == null || subject.isBlank()) {
            log.error("auth.success: principal has no subject (registrationId={})", registrationId);
            getRedirectStrategy().sendRedirect(request, response, "/auth/error?code=no_subject");
            return;
        }

        AuthIntentCookie.Intent intent = authIntentCookie.read(request);
        authIntentCookie.clear(response);

        Account account = provisioningService.resolveOrProvision(registrationId, subject, principal, intent);

        HephaestusJwtIssuer.Token issued = jwtIssuer.issue(
            principalFactory.forAccount(account),
            /* impersonator */ null,
            request
        );
        setAccessCookie(
            response,
            issued.value(),
            issued.expiresAt().getEpochSecond() - clock.instant().getEpochSecond()
        );

        String redirectTo = (intent != null) ? ReturnToValidator.safeOrFallback(intent.returnTo()) : "/";
        getRedirectStrategy().sendRedirect(request, response, redirectTo);
    }

    private void setAccessCookie(HttpServletResponse response, String jwt, long maxAgeSeconds) {
        Cookie cookie = new Cookie(authProperties.cookieName(), jwt);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge((int) Math.max(0, maxAgeSeconds));
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }
}
