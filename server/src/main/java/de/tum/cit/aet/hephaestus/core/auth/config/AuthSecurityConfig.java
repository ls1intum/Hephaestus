package de.tum.cit.aet.hephaestus.core.auth.config;

import de.tum.cit.aet.hephaestus.core.auth.AuthProperties;
import de.tum.cit.aet.hephaestus.core.auth.jwt.HephaestusJwtIssuer;
import de.tum.cit.aet.hephaestus.core.auth.oauth.AccountProvisioningService;
import de.tum.cit.aet.hephaestus.core.auth.oauth.AuthIntentCookie;
import de.tum.cit.aet.hephaestus.core.auth.oauth.CookieOAuth2AuthorizationRequestRepository;
import de.tum.cit.aet.hephaestus.core.auth.oauth.GitHubEmailOAuth2UserService;
import de.tum.cit.aet.hephaestus.core.auth.oauth.HephaestusAuthSuccessHandler;
import de.tum.cit.aet.hephaestus.core.auth.ratelimit.AuthRateLimitFilter;
import de.tum.cit.aet.hephaestus.core.auth.spi.OAuthLoginDefaultsProvider;
import de.tum.cit.aet.hephaestus.core.security.SecurityHeaders;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

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
            // Subject must be the IdP-stable NUMERIC GitLab user id (GitLab's /api/v4/user returns
            // both "id" and "username"; "username" is mutable). Keying IdentityLink.subject on the
            // numeric id matches GitHub + the workspace-scoped gl-ws-* registrations, satisfies the
            // "IdP-stable user id" contract on IdentityLink.subject (nOAuth defence), and lets the
            // SCM actor-mirror provisioner derive the provider native_id from the subject.
            .userNameAttributeName("id")
            .clientName("gitlab.lrz.de")
            .build();
    }

    @Bean
    public CookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository(
        AuthProperties properties,
        Environment environment
    ) {
        return new CookieOAuth2AuthorizationRequestRepository(resolveStateCookieKey(properties, isProd(environment)));
    }

    @Bean
    public AuthIntentCookie authIntentCookie(AuthProperties properties, Environment environment) {
        return new AuthIntentCookie(resolveStateCookieKey(properties, isProd(environment)));
    }

    private static boolean isProd(Environment environment) {
        return environment.acceptsProfiles(Profiles.of("prod"));
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

    /**
     * Resolver that adds PKCE (RFC 7636 / RFC 9700) to every login authorization request. Spring
     * only auto-enables PKCE for <em>public</em> clients; our login registrations are confidential
     * ({@code CLIENT_SECRET_BASIC}), so we opt in explicitly. RFC 9700 (Jan 2025) requires PKCE for
     * all OAuth clients — for GitHub (plain OAuth2, no {@code id_token}/nonce) it is the primary
     * defence against authorization-code injection alongside {@code state}. The {@code code_verifier}
     * is stored on the {@code OAuth2AuthorizationRequest} and round-trips inside the sealed
     * auth-request cookie ({@link CookieOAuth2AuthorizationRequestRepository}).
     */
    private static OAuth2AuthorizationRequestResolver pkceResolver(ClientRegistrationRepository repo) {
        DefaultOAuth2AuthorizationRequestResolver resolver = new DefaultOAuth2AuthorizationRequestResolver(
            repo,
            "/oauth2/authorization"
        );
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        return resolver;
    }

    /**
     * OAuth2 (non-OIDC) user service. Routes the default {@code github} registration through
     * {@link GitHubEmailOAuth2UserService} (fetches the primary+verified email from
     * {@code api.github.com/user/emails}); every other registration uses the framework default. Scoped
     * to {@code github} ONLY — workspace {@code gh-ws-*} providers may be GitHub Enterprise with a
     * different API host, which this enricher does not know. OIDC providers (gitlab-lrz, gl-ws-*) never
     * reach this service; they go through the OidcUserService and expose {@code email_verified} natively.
     */
    @Bean
    public OAuth2UserService<OAuth2UserRequest, OAuth2User> oauthUserService() {
        var github = new GitHubEmailOAuth2UserService();
        var fallback = new DefaultOAuth2UserService();
        return request ->
            (GITHUB_REGISTRATION_ID.equals(request.getClientRegistration().getRegistrationId())
                ? github
                : fallback
            ).loadUser(request);
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 10)
    public SecurityFilterChain oauthLoginSecurityFilterChain(
        HttpSecurity http,
        CookieOAuth2AuthorizationRequestRepository cookieRepo,
        HephaestusAuthSuccessHandler successHandler,
        AuthRateLimitFilter authRateLimitFilter,
        ClientRegistrationRepository clientRegistrationRepository,
        OAuth2UserService<OAuth2UserRequest, OAuth2User> oauthUserService
    ) throws Exception {
        http
            .securityMatcher("/oauth2/authorization/**", "/login/oauth2/code/**", "/auth/login", "/auth/error")
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(req -> {
                req.requestMatchers("/auth/error").permitAll();
                req.anyRequest().permitAll(); // oauth2Login handles the actual auth
            })
            .oauth2Login(oauth -> {
                // Explicit loginPage: the SPA owns the login UI, and without this Spring Security's
                // OAuth2LoginConfigurer ENUMERATES the ClientRegistrationRepository at config time to build
                // default login links. That repo is DB-backed (LoginClientRegistrationRepository overlays
                // workspace OIDC Connections), so the enumeration opens a JDBC connection during context
                // refresh — which has no DB during the Paketo CDS-training build run and fails the image
                // build. Setting a loginPage skips the default-page generation and that startup DB hit.
                oauth.loginPage("/login");
                oauth.authorizationEndpoint(endpoint ->
                    endpoint
                        .authorizationRequestRepository(cookieRepo)
                        .authorizationRequestResolver(pkceResolver(clientRegistrationRepository))
                );
                // GitHub: enrich attributes with the primary+verified email from /user/emails so
                // VerifiedEmailResolver can stamp primaryEmailVerifiedAt. OIDC providers are untouched.
                oauth.userInfoEndpoint(userInfo -> userInfo.userService(oauthUserService));
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
     *
     * <p>In the {@code prod} profile a blank key is fatal (fail-closed) — mirroring
     * {@code JwtSigningKeySealer}'s prod fail-fast — because an ephemeral key silently
     * invalidates every in-flight login on each pod restart and differs per replica, which
     * {@link AuthProperties} documents as a misconfiguration rather than a degraded mode.
     */
    static byte[] resolveStateCookieKey(AuthProperties properties, boolean prodProfile) {
        if (!properties.stateCookieKey().isBlank()) {
            byte[] decoded = Base64.getDecoder().decode(properties.stateCookieKey());
            if (decoded.length != 32) {
                throw new IllegalStateException(
                    "hephaestus.auth.state-cookie-key must decode to 32 bytes (256-bit AES); got " + decoded.length
                );
            }
            return decoded;
        }
        if (prodProfile) {
            throw new IllegalStateException(
                "hephaestus.auth.state-cookie-key is required in production (fail-closed). " +
                    "Set it to a base64-encoded 32-byte (256-bit AES) value."
            );
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
