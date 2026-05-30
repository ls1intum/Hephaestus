package de.tum.cit.aet.hephaestus.core.auth.oauth;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Entry point for the OAuth login dance — stamps the {@link AuthIntentCookie} with the
 * caller's intent (workspace + returnTo + mode) and 302s to Spring's standard
 * {@code /oauth2/authorization/{registrationId}} initiation endpoint.
 *
 * <p>This is the SPA's "Sign in with X" target. The intent cookie survives the round-trip
 * to the IdP and is read by the success handler (later commit) to decide where to land
 * the user post-login.
 */
@RestController
@RequestMapping("/auth")
public class AuthBeginController {

    private static final Logger log = LoggerFactory.getLogger(AuthBeginController.class);
    private static final String OAUTH_INIT_PATH = "/oauth2/authorization/";

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final AuthIntentCookie authIntentCookie;

    public AuthBeginController(
        ClientRegistrationRepository clientRegistrationRepository,
        AuthIntentCookie authIntentCookie
    ) {
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.authIntentCookie = authIntentCookie;
    }

    @GetMapping("/login")
    @PreAuthorize("permitAll()")
    @Hidden // Behavior endpoint; OpenAPI catalogs the public-facing surface in commit 11.
    @Operation(summary = "Begin OAuth login flow against the given registrationId")
    public RedirectView begin(
        @RequestParam("provider") String registrationId,
        @RequestParam(value = "workspace", required = false) @Nullable String workspaceSlug,
        @RequestParam(value = "returnTo", required = false) @Nullable String returnTo,
        @RequestParam(value = "mode", required = false, defaultValue = "login") String mode,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        ClientRegistration registration = clientRegistrationRepository.findByRegistrationId(registrationId);
        if (registration == null) {
            log.warn("auth.begin: unknown provider={}", registrationId);
            return new RedirectView("/auth/error?code=unknown_provider", false);
        }
        String safeReturnTo = ReturnToValidator.safeOrFallback(returnTo);
        AuthIntentCookie.Intent intent = "link".equalsIgnoreCase(mode)
            ? AuthIntentCookie.Intent.link(/* linkingAccountId resolved by handler */ null, safeReturnTo)
            : AuthIntentCookie.Intent.login(workspaceSlug, safeReturnTo);
        authIntentCookie.write(response, intent);
        // 302 to Spring's standard initiation endpoint; the OAuth2AuthorizationRequestRedirectFilter
        // takes over from here, building the upstream redirect with state + PKCE.
        String urlEncodedRegistration = URLEncoder.encode(registrationId, StandardCharsets.UTF_8);
        return new RedirectView(OAUTH_INIT_PATH + urlEncodedRegistration, false);
    }
}
