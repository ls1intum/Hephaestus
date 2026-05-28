package de.tum.cit.aet.hephaestus.core.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

/**
 * Placeholder success handler for the parallel-chain commit. Just reads the
 * {@link AuthIntentCookie}, logs the resolved provider subject, and 302s to the validated
 * {@code returnTo} (or {@code /}). The real handler — JIT account provisioning, IdentityLink
 * upsert, JWT issuance via {@link de.tum.cit.aet.hephaestus.core.auth.jwt.HephaestusJwtIssuer},
 * link-mode dispatch — lands in a later commit; the wiring it bolts into stays the same.
 */
public class StubAuthSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(StubAuthSuccessHandler.class);

    private final AuthIntentCookie authIntentCookie;

    public StubAuthSuccessHandler(AuthIntentCookie authIntentCookie) {
        this.authIntentCookie = authIntentCookie;
        setAlwaysUseDefaultTargetUrl(false);
        setDefaultTargetUrl("/");
    }

    @Override
    public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) throws IOException {
        AuthIntentCookie.Intent intent = authIntentCookie.read(request);
        if (authentication.getPrincipal() instanceof OAuth2User principal) {
            log.info(
                "auth.oauth: federated login OK — provider={} subject={} attributes={}",
                authentication instanceof org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken token
                    ? token.getAuthorizedClientRegistrationId()
                    : "?",
                principal.getName(),
                principal.getAttributes().keySet()
            );
        }
        authIntentCookie.clear(response);
        String target = (intent != null) ? ReturnToValidator.safeOrFallback(intent.returnTo()) : "/";
        getRedirectStrategy().sendRedirect(request, response, target);
    }
}
