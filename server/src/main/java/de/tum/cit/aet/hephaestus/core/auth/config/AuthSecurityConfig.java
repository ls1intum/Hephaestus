package de.tum.cit.aet.hephaestus.core.auth.config;

import de.tum.cit.aet.hephaestus.core.auth.AuthProperties;
import de.tum.cit.aet.hephaestus.core.auth.jwt.HephaestusJwtIssuer;
import de.tum.cit.aet.hephaestus.core.auth.oauth.AuthIntentCookie;
import de.tum.cit.aet.hephaestus.core.auth.oauth.CookieOAuth2AuthorizationRequestRepository;
import de.tum.cit.aet.hephaestus.core.auth.oauth.HephaestusAuthSuccessHandler;
import de.tum.cit.aet.hephaestus.core.auth.oauth.AccountProvisioningService;
import de.tum.cit.aet.hephaestus.core.auth.ratelimit.AuthRateLimitFilter;
import de.tum.cit.aet.hephaestus.core.auth.spi.OAuthLoginDefaultsProvider;
import de.tum.cit.aet.hephaestus.core.security.SecurityHeaders;
import java.time.Clock;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Wires the OAuth login flow on a dedicated, medium-precedence
 * {@link SecurityFilterChain}.
 *
 * <p>Order: {@code HIGHEST_PRECEDENCE + 10} — runs after the worker-hub chain
 * ({@code HIGHEST_PRECEDENCE}) but before the resource-server chain (default order).
 * Scope is strictly the URLs Spring's {@code oauth2Login} cares about plus the
 * {@code /auth/login} kickoff endpoint:
 *
 * <ul>
 *   <li>{@code GET /oauth2/authorization/{registrationId}} — Spring's initiation filter.</li>
 *   <li>{@code GET /login/oauth2/code/{registrationId}} — Spring's callback filter.</li>
 *   <li>{@code GET /auth/login} — our intent-stamping wrapper that 302s to the initiation.</li>
 *   <li>{@code GET /auth/error} — public error page rendered by the SPA.</li>
 * </ul>
 *
 * <p>Stateless. {@link CookieOAuth2AuthorizationRequestRepository} carries the in-flight
 * {@code OAuth2AuthorizationRequest} across the IdP round-trip via an AES-GCM-sealed
 * cookie — the standard {@code HttpSessionOAuth2AuthorizationRequestRepository} would
 * silently create a session that the callback request on a different pod could not see.
 *
 * <p>Everything outside the URLs above is handled by the resource-server chain, which
 * validates our own ES256 JWTs via {@code RevocationAwareJwtDecoder} (replaces the former
 * Keycloak setup; ADR 0017).
 */
@Configuration
public class AuthSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(AuthSecurityConfig.class);
    private static final String GITHUB_REGISTRATION_ID = "github";
    private static final String GITLAB_LRZ_REGISTRATION_ID = "gitlab-lrz";
    private static final String REDIRECT_URI_TEMPLATE = "{baseUrl}/login/oauth2/code/{registrationId}";

    /**
     * Env-configured default OAuth login providers (github, gitlab-lrz), exposed as the
     * {@link OAuthLoginDefaultsProvider} port. The {@code integration} module's composite
     * {@code ClientRegistrationRepository} consumes this and overlays the workspace-scoped
     * OIDC-login Connections (family IDENTITY) — keeping the secret-bearing Connection access
     * out of {@code core.auth} and the bounded-context dependency direction correct
     * ({@code integration → core}, never the reverse).
     *
     * <p>Default providers are built from {@link AuthProperties} rather than
     * {@code spring.security.oauth2.client.*} so that a provider with no configured client id
     * is simply omitted — Boot's {@code OAuth2ClientProperties} validation would otherwise
     * reject the empty client id and abort the context (breaking CI, specs and worker pods).
     */
    @Bean
    public OAuthLoginDefaultsProvider oauthLoginDefaultsProvider(AuthProperties authProperties) {
        List<ClientRegistration> registrations = new ArrayList<>();
        if (authProperties.github().configured()) {
            registrations.add(buildGithubRegistration(authProperties.github()));
        }
        if (authProperties.gitlabLrz().configured()) {
            registrations.add(buildGitlabLrzRegistration(authProperties.gitlabLrz()));
        }
        if (registrations.isEmpty()) {
            log.warn(
                "auth: no default OAuth login providers configured — set hephaestus.auth.github.client-id " +
                "and/or hephaestus.auth.gitlab-lrz.client-id. Workspace-scoped OIDC Connections may still apply."
            );
        }
        return () -> List.copyOf(registrations);
    }

    private static ClientRegistration buildGithubRegistration(AuthProperties.GithubLogin github) {
        return ClientRegistration.withRegistrationId(GITHUB_REGISTRATION_ID)
            .clientId(github.clientId())
            .clientSecret(github.clientSecret())
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(REDIRECT_URI_TEMPLATE)
            .scope("read:user", "user:email")
            .authorizationUri("https://github.com/login/oauth/authorize")
            .tokenUri("https://github.com/login/oauth/access_token")
            .userInfoUri("https://api.github.com/user")
            .userNameAttributeName("id")
            .clientName("GitHub")
            .build();
    }

    private static ClientRegistration buildGitlabLrzRegistration(AuthProperties.GitlabLrzLogin gitlab) {
        String base = gitlab.baseUrl().toString().replaceAll("/+$", "");
        return ClientRegistration.withRegistrationId(GITLAB_LRZ_REGISTRATION_ID)
            .clientId(gitlab.clientId())
            .clientSecret(gitlab.clientSecret())
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(REDIRECT_URI_TEMPLATE)
            .scope("openid", "profile", "email", "read_user")
            .authorizationUri(base + "/oauth/authorize")
            .tokenUri(base + "/oauth/token")
            .userInfoUri(base + "/api/v4/user")
            .userNameAttributeName("username")
            .clientName("gitlab.lrz.de")
            .build();
    }

    @Bean
    public CookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository(AuthProperties properties) {
        return new CookieOAuth2AuthorizationRequestRepository(resolveStateCookieKey(properties));
    }

    @Bean
    public AuthIntentCookie authIntentCookie(AuthProperties properties) {
        return new AuthIntentCookie(resolveStateCookieKey(properties));
    }

    @Bean
    public HephaestusAuthSuccessHandler hephaestusAuthSuccessHandler(
        AccountProvisioningService provisioningService,
        HephaestusJwtIssuer jwtIssuer,
        de.tum.cit.aet.hephaestus.core.auth.jwt.JwtPrincipalFactory principalFactory,
        AuthIntentCookie authIntentCookie,
        AuthProperties properties,
        Clock authClock
    ) {
        return new HephaestusAuthSuccessHandler(
            provisioningService,
            jwtIssuer,
            principalFactory,
            authIntentCookie,
            properties,
            authClock
        );
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 10)
    public SecurityFilterChain oauthLoginSecurityFilterChain(
        HttpSecurity http,
        CookieOAuth2AuthorizationRequestRepository cookieRepo,
        HephaestusAuthSuccessHandler successHandler,
        AuthRateLimitFilter authRateLimitFilter
    ) throws Exception {
        http
            .securityMatcher(
                "/oauth2/authorization/**",
                "/login/oauth2/code/**",
                "/auth/login",
                "/auth/error"
            )
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(req -> {
                req.requestMatchers("/auth/error").permitAll();
                req.anyRequest().permitAll(); // oauth2Login handles the actual auth
            })
            .oauth2Login(oauth -> {
                oauth.authorizationEndpoint(endpoint -> endpoint.authorizationRequestRepository(cookieRepo));
                oauth.successHandler(successHandler);
                oauth.failureUrl("/auth/error?code=oauth_failure");
            });

        // Same header set as the resource-server chain — this chain serves /auth/error to the SPA.
        SecurityHeaders.apply(http);
        // Rate-limit GET /oauth2/authorization/* (keyed by client IP). The filter no-ops on the
        // other paths this chain matches.
        http.addFilterBefore(authRateLimitFilter, AuthorizationFilter.class);
        return http.build();
    }

    /**
     * Resolve the AES-256-GCM key for the two short-lived state cookies. Production must set
     * {@code hephaestus.auth.state-cookie-key} to a base64-encoded 32-byte value; absence is
     * tolerated only for dev / CI where we generate a per-boot ephemeral key and log a
     * warning (in-flight logins are abandoned across restarts).
     */
    private static byte[] resolveStateCookieKey(AuthProperties properties) {
        if (!properties.stateCookieKey().isBlank()) {
            byte[] decoded = Base64.getDecoder().decode(properties.stateCookieKey());
            if (decoded.length != 32) {
                throw new IllegalStateException(
                    "hephaestus.auth.state-cookie-key must decode to 32 bytes (256-bit AES); got " + decoded.length
                );
            }
            return decoded;
        }
        byte[] ephemeral = new byte[32];
        new SecureRandom().nextBytes(ephemeral);
        log.warn(
            "auth: hephaestus.auth.state-cookie-key is unset — generated ephemeral 256-bit key for this boot. " +
            "In-flight logins will not survive a restart. Set the env var for stable behaviour."
        );
        return ephemeral;
    }
}
