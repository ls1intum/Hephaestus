package de.tum.cit.aet.hephaestus.core.auth.config;

import de.tum.cit.aet.hephaestus.core.auth.AuthProperties;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLinkRepository;
import de.tum.cit.aet.hephaestus.core.auth.jwt.HephaestusJwtIssuer;
import de.tum.cit.aet.hephaestus.core.auth.oauth.AuthIntentCookie;
import de.tum.cit.aet.hephaestus.core.auth.oauth.CookieOAuth2AuthorizationRequestRepository;
import de.tum.cit.aet.hephaestus.core.auth.oauth.HephaestusAuthSuccessHandler;
import de.tum.cit.aet.hephaestus.core.auth.oauth.RegistrationToGitProviderResolver;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountRepository;
import java.time.Clock;
import java.security.SecureRandom;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
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
 * <p>Until the cutover commit, this chain coexists with the Keycloak-backed
 * resource-server chain — everything outside the URLs above continues to validate
 * Keycloak JWTs. After cutover, the resource-server chain switches to our own
 * {@code RevocationAwareJwtDecoder} and Keycloak is deleted.
 */
@Configuration
public class AuthSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(AuthSecurityConfig.class);

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
        AccountRepository accountRepository,
        IdentityLinkRepository identityLinkRepository,
        RegistrationToGitProviderResolver providerResolver,
        HephaestusJwtIssuer jwtIssuer,
        AuthIntentCookie authIntentCookie,
        AuthProperties properties,
        Clock authClock
    ) {
        return new HephaestusAuthSuccessHandler(
            accountRepository,
            identityLinkRepository,
            providerResolver,
            jwtIssuer,
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
        HephaestusAuthSuccessHandler successHandler
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
