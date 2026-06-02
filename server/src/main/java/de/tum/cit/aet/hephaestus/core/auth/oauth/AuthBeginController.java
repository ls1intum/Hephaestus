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
    private final de.tum.cit.aet.hephaestus.core.auth.jwt.CookieBearerTokenResolver bearerTokenResolver;
    private final org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

    public AuthBeginController(
        ClientRegistrationRepository clientRegistrationRepository,
        AuthIntentCookie authIntentCookie,
        de.tum.cit.aet.hephaestus.core.auth.jwt.CookieBearerTokenResolver bearerTokenResolver,
        de.tum.cit.aet.hephaestus.core.auth.jwt.RevocationAwareJwtDecoder jwtDecoder
    ) {
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.authIntentCookie = authIntentCookie;
        this.bearerTokenResolver = bearerTokenResolver;
        this.jwtDecoder = jwtDecoder;
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
        AuthIntentCookie.Intent intent;
        if ("link".equalsIgnoreCase(mode)) {
            // Link mode MUST be initiated by an already-authenticated user (secure account linking:
            // never auto-link to an unauthenticated context — that is the pre-account-takeover bug).
            // The login chain is stateless and permitAll, so no SecurityContext exists here; we validate
            // the access cookie ourselves with the SAME primitives the resource-server chain uses
            // (CookieBearerTokenResolver -> RevocationAwareJwtDecoder), then bind sub = accountId so
            // AccountProvisioningService attaches the new identity to THIS account.
            Long currentAccountId = resolveAuthenticatedAccountId(request);
            if (currentAccountId == null) {
                log.warn("auth.begin: link mode rejected — no valid session");
                return new RedirectView("/auth/error?code=link_requires_auth", false);
            }
            intent = AuthIntentCookie.Intent.link(currentAccountId, safeReturnTo);
        } else {
            intent = AuthIntentCookie.Intent.login(workspaceSlug, safeReturnTo);
        }
        authIntentCookie.write(response, intent);
        // 302 to Spring's standard initiation endpoint; the OAuth2AuthorizationRequestRedirectFilter
        // takes over from here, building the upstream redirect with state + PKCE (see AuthSecurityConfig).
        String urlEncodedRegistration = URLEncoder.encode(registrationId, StandardCharsets.UTF_8);
        return new RedirectView(OAUTH_INIT_PATH + urlEncodedRegistration, false);
    }

    /**
     * The current account id from the access cookie, validated through the same decoder the
     * resource-server chain uses (signature + exp/iss/aud + revocation). Returns {@code null} when
     * there is no cookie/header token or the token is invalid/revoked — link mode then fails closed.
     */
    @Nullable
    private Long resolveAuthenticatedAccountId(HttpServletRequest request) {
        String token = bearerTokenResolver.resolve(request);
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            org.springframework.security.oauth2.jwt.Jwt jwt = jwtDecoder.decode(token);
            return Long.parseLong(jwt.getSubject());
        } catch (org.springframework.security.oauth2.jwt.JwtException | NumberFormatException ex) {
            log.warn("auth.begin: link-mode token rejected: {}", ex.getMessage());
            return null;
        }
    }
}
