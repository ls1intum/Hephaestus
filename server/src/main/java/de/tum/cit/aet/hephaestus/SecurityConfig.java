package de.tum.cit.aet.hephaestus;

import de.tum.cit.aet.hephaestus.config.CorsProperties;
import de.tum.cit.aet.hephaestus.core.auth.ratelimit.AuthRateLimitFilter;
import de.tum.cit.aet.hephaestus.core.security.CsrfCookieFilter;
import de.tum.cit.aet.hephaestus.core.security.ImpersonationGuard;
import de.tum.cit.aet.hephaestus.core.security.SecurityHeaders;
import de.tum.cit.aet.hephaestus.core.security.SpaCsrfTokenRequestHandler;
import de.tum.cit.aet.hephaestus.feature.FeatureFlag;
import de.tum.cit.aet.hephaestus.observability.ReplicaIdentityFilter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final CorsProperties corsProperties;
    private final boolean devTriggerEnabled;

    public SecurityConfig(
        CorsProperties corsProperties,
        @Value("${hephaestus.dev.trigger-enabled:false}") boolean devTriggerEnabled
    ) {
        this.corsProperties = corsProperties;
        this.devTriggerEnabled = devTriggerEnabled;
    }

    interface AuthoritiesConverter extends Converter<Map<String, Object>, Collection<GrantedAuthority>> {}

    @SuppressWarnings("unchecked")
    @Bean
    AuthoritiesConverter rolesAuthoritiesConverter() {
        return claims -> {
            // Flat `roles` claim on the Hephaestus-issued JWT (ADR 0017). The role strings
            // ("admin", "mentor_access", …) map 1:1 to granted authorities consumed by @PreAuthorize.
            final var roles = Optional.ofNullable((List<String>) claims.get("roles"));
            return roles
                .map(List::stream)
                .orElse(Stream.empty())
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
        };
    }

    @Bean
    JwtAuthenticationConverter authenticationConverter(
        Converter<Map<String, Object>, Collection<GrantedAuthority>> authoritiesConverter
    ) {
        var authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
            return authoritiesConverter.convert(jwt.getClaims());
        });
        return authenticationConverter;
    }

    /**
     * Dedicated filter chain for the worker control channel (POST exchange + WSS upgrade) and
     * webhook ingress + actuator probes. These paths carry their own auth — worker JWT signed
     * by {@link de.tum.cit.aet.hephaestus.core.runtime.hub.auth.WorkerKeyRing}, HMAC/token at
     * the controller layer for webhooks, or no auth at all for health/info — and must NOT be
     * processed by the user-facing OAuth2 resource server. Running BearerTokenAuthenticationFilter
     * on the worker JWT runs it through the resource-server's ES256 decoder, which either fails
     * (signing key unavailable) or rejects (different signer), masking the real auth.
     *
     * <p>Always present, so a dev / worker-only pod can
     * boot without the resource-server JWT decoder configured.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain workerHubSecurityFilterChain(HttpSecurity http) throws Exception {
        // Only paths whose auth lives at the controller / handshake layer go on this chain.
        // Other actuator endpoints (metrics, prometheus, loggers, heapdump, …) must NOT be
        // matched here — they fall through to the OAuth2 chain.
        http
            .securityMatcher(
                "/api/workers/**",
                "/actuator/health",
                "/actuator/health/**",
                "/actuator/info",
                "/webhooks/**",
                "/oauth/callback/**"
            )
            .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(requests -> requests.anyRequest().permitAll());
        return http.build();
    }

    /**
     * Application security chain (fallback after {@link #workerHubSecurityFilterChain}).
     *
     * <p>The {@link JwtDecoder} is resolved via {@link ObjectProvider} when this bean is built, so
     * it sees the decoder even when it comes from Spring Boot's OAuth2 autoconfig (registered after
     * user config). {@code @ConditionalOnBean(JwtDecoder)} can't be used here: it evaluates before
     * the autoconfig decoder is registered and would drop the server role into deny-all.
     *
     * <p>Decoder present (the native-auth {@link de.tum.cit.aet.hephaestus.core.auth.jwt.RevocationAwareJwtDecoder},
     * ADR 0017) → wire the OAuth2 resource server with the full native-auth rules (security headers,
     * auth rate limiting, the public auth-discovery permit list). Absent (worker-only pod that excludes
     * the OAuth2 autoconfig, or the {@code specs} profile that boots without a decoder) → deny everything.
     * The {@code hephaestus.dev.trigger-enabled} carve-out applies in both modes.
     */
    @Bean
    SecurityFilterChain appSecurityFilterChain(
        HttpSecurity http,
        ObjectProvider<JwtDecoder> jwtDecoderProvider,
        ObjectProvider<BearerTokenResolver> bearerTokenResolverProvider,
        Converter<Jwt, AbstractAuthenticationToken> authenticationConverter,
        AuthRateLimitFilter authRateLimitFilter,
        tools.jackson.databind.ObjectMapper objectMapper
    ) throws Exception {
        http
            .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(cors -> cors.configurationSource(corsConfigurationSource()));

        JwtDecoder jwtDecoder = jwtDecoderProvider.getIfAvailable();
        if (jwtDecoder == null) {
            // Lockdown / specs boot: no user cookie sessions exist, so CSRF is moot — disable it to
            // keep the no-decoder pod and spec generation simple.
            http.csrf(csrf -> csrf.disable());
            http.authorizeHttpRequests(requests -> {
                requests.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                // OpenAPI / Swagger endpoints are public on the resource-server chain; they must
                // also be public on the lockdown chain so spec generation works on no-JWT-decoder boots
                // (the `specs` profile boots without a JwtDecoder and would otherwise 403 on
                // `mvn verify -Dapp.profiles=specs`).
                requests
                    .requestMatchers("/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll();
                if (devTriggerEnabled) {
                    requests.requestMatchers("/api/dev/**").permitAll();
                }
                requests.anyRequest().denyAll();
            });
            return http.build();
        }

        // Resolve the JWT from the SPA's __Host-HEPHAESTUS_AT cookie first, falling back to the
        // Authorization: Bearer header. The framework default reads ONLY the header, so without this
        // the cookie-authenticated SPA (credentials:"include", no Authorization header) is 401 on
        // every request. The resolver bean is built in the auth module (AuthJwtConfig) so this root
        // config depends only on the framework BearerTokenResolver interface. See ADR 0017.
        BearerTokenResolver bearerTokenResolver = bearerTokenResolverProvider.getIfAvailable();
        http.oauth2ResourceServer(resourceServer -> {
            if (bearerTokenResolver != null) {
                resourceServer.bearerTokenResolver(bearerTokenResolver);
            }
            resourceServer.jwt(jwtConfigurer -> {
                jwtConfigurer.decoder(jwtDecoder).jwtAuthenticationConverter(authenticationConverter);
            });
        });

        // Stateless double-submit CSRF (ADR 0017). The access-token cookie is sent automatically by
        // the browser on same-site requests, so SameSite=Lax alone is the only forgery barrier — and
        // Lax still permits top-level cross-site POST navigations. We close that gap with a
        // double-submit token: CookieCsrfTokenRepository.withHttpOnlyFalse() writes the raw token to a
        // JS-readable `XSRF-TOKEN` cookie; the SPA echoes it in the `X-XSRF-TOKEN` header (see
        // webapp/src/main.tsx). A cross-site attacker cannot read the cookie, so cannot supply the
        // header. SpaCsrfTokenRequestHandler resolves the header value as the raw (unmasked) token to
        // match what the SPA sends.
        //
        // CSRF is required ONLY for cookie-authenticated, state-changing requests
        // ({@link #requiresCsrf}): safe methods (GET/HEAD/OPTIONS) are exempt, and so are requests
        // carrying an `Authorization: Bearer` header — a bearer token is never auto-attached by the
        // browser cross-site, so those requests are not CSRF-vulnerable (this also keeps API clients
        // and bearer-token integration tests working). Webhooks, OAuth callbacks and the optional
        // dev-trigger are additionally excluded — they are not browser cookie POSTs.
        http.csrf(csrf ->
            csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                .requireCsrfProtectionMatcher(this::requiresCsrf)
        );
        // Force the deferred token to render the XSRF-TOKEN cookie on every response so the SPA always
        // has a token to echo, even on a bare GET /user.
        http.addFilterAfter(new CsrfCookieFilter(), AuthorizationFilter.class);

        // Security headers (HSTS, CSP-report-only, COOP, COEP, Referrer-Policy,
        // X-Content-Type-Options) for the user-facing resource-server chain.
        SecurityHeaders.apply(http);

        // Token-bucket rate limiting on the hot auth endpoints (/auth/refresh, /auth/impersonate,
        // DELETE /user). Registered after authentication so the account principal is resolvable;
        // it no-ops on every other path. /oauth2/authorization/* is rate-limited on the oauth2Login
        // chain (AuthSecurityConfig), which owns that path.
        http.addFilterBefore(authRateLimitFilter, AuthorizationFilter.class);

        // Read-only-by-default enforcement for impersonation sessions (JWT carries an `act`
        // claim). Registered AFTER the AuthorizationFilter so the SecurityContext already holds
        // the validated JwtAuthenticationToken — only then is the `act` claim resolvable. Without
        // this registration the guard never runs and impersonation grants full write access as the
        // target (the documented read-only model was dead code). See ImpersonationGuard.
        http.addFilterAfter(new ImpersonationGuard(objectMapper), AuthorizationFilter.class);

        http.authorizeHttpRequests(requests -> {
            // CORS preflight requests must be permitted for cross-origin requests to work.
            // Without this, OPTIONS requests are rejected with 403 before CORS headers can be added.
            requests.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
            // The container's ERROR dispatch (forward to /error) is re-secured by this chain. Without
            // permitting it, an error raised on a protected endpoint (e.g. a 403 from the CsrfFilter)
            // gets overwritten by a 401 when the anonymous /error forward is denied. Permit it so the
            // ORIGINAL status is preserved. The error view carries no sensitive data.
            requests.requestMatchers("/error").permitAll();
            // NOTE: /webhooks/**, /oauth/callback/**, /api/workers/** and /actuator/health|info are
            // claimed by higher-precedence chains and NEVER reach this fallback chain:
            //   - /webhooks/** + /oauth/callback/**  → workerHubSecurityFilterChain (HIGHEST_PRECEDENCE)
            //   - /oauth2/authorization/** + /login/oauth2/code/** + /auth/login|error → AuthSecurityConfig
            // Spring dispatches to the FIRST matching SecurityFilterChain only, so permitAll rules for
            // those paths here would be dead code (they were — removed). Their controller/handshake-layer
            // auth (HMAC, shared token, signed state, worker JWT) is unaffected.
            // Dev-only: permit the dev trigger endpoint when explicitly enabled (defaults to false).
            if (devTriggerEnabled) {
                requests.requestMatchers(DEV_TRIGGER_MATCHER).permitAll();
            }
            // OpenAPI documentation endpoints (public for spec generation and dev access)
            requests
                .requestMatchers("/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui/**", "/swagger-ui.html")
                .permitAll();
            // Public auth discovery (core.auth module, ADR 0017): identity-provider list for the
            // login UI plus OIDC issuer metadata + JWKS. The OAuth login kickoff/callback paths
            // (/auth/login, /auth/error, /oauth2/authorization/**, /login/oauth2/code/**) are owned
            // by AuthSecurityConfig's higher-precedence chain and never reach this one.
            requests.requestMatchers(HttpMethod.GET, "/identity-providers").permitAll();
            requests.requestMatchers(HttpMethod.GET, "/.well-known/**").permitAll();
            // Public workspace provider discovery (workspace creation UI)
            requests.requestMatchers(HttpMethod.GET, "/workspaces/providers").permitAll();
            // Mentor endpoints gated by the MENTOR_ACCESS feature flag. MUST be matched BEFORE the
            // generic `/workspaces/*/**` permitAll below; otherwise the public-GET rule wins and
            // mentor reads become unauthenticated (feature-flag bypass — see #1071).
            requests.requestMatchers("/workspaces/*/mentor/**").hasAuthority(FeatureFlag.MENTOR_ACCESS.key());
            // Public read for slugged workspace paths (filter enforces membership/public visibility).
            requests.requestMatchers(HttpMethod.GET, "/workspaces/*/**").permitAll();
            // Registry/listing stays authenticated to avoid leaking tenant directory.
            requests.requestMatchers(HttpMethod.GET, "/workspaces", "/workspaces/").authenticated();
            // Non-GET workspace operations still require authentication.
            requests.requestMatchers("/workspaces/**").authenticated();
            // Public endpoints
            requests.requestMatchers(HttpMethod.GET, "/contributors").permitAll();
            // User account management requires authentication
            requests.requestMatchers("/user/**").authenticated();
            // Default: require authentication for all other endpoints
            requests.anyRequest().authenticated();
        });

        return http.build();
    }

    private static final java.util.Set<String> SAFE_METHODS = java.util.Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    /**
     * Single canonical matcher for the optional dev-trigger surface, shared by the authorize rules and
     * the CSRF predicate so they cannot diverge. Replaces the previous raw
     * {@code getServletPath().startsWith("/api/dev/")} string gating. The webhook / OAuth-callback /
     * {@code /login/oauth2/code/} skips that used to live in {@code requiresCsrf} are gone: those paths
     * are owned by the higher-precedence worker-hub and oauth2Login chains and never reach this chain.
     */
    static final RequestMatcher DEV_TRIGGER_MATCHER = PathPatternRequestMatcher.withDefaults().matcher("/api/dev/**");

    /**
     * CSRF applies only to cookie-authenticated, state-changing browser requests. Returns
     * {@code false} (skip CSRF) for safe methods, for any request bearing an
     * {@code Authorization: Bearer} header (bearer auth is not CSRF-vulnerable; covers API clients +
     * bearer-token tests), and for the non-browser-cookie POST paths (webhooks, OAuth callbacks, and
     * the optional dev trigger). Everything else — i.e. a non-safe request relying on the
     * {@code __Host-HEPHAESTUS_AT} cookie — must present the double-submit token.
     */
    private boolean requiresCsrf(jakarta.servlet.http.HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return false;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return false;
        }
        // No path skips for /webhooks/, /oauth/callback/, or /login/oauth2/code/ here: those are owned
        // by higher-precedence chains (worker-hub, oauth2Login) and never reach this chain, so any skip
        // here was dead. The only live carve-out is the optional dev trigger, matched by the SAME
        // PathPatternRequestMatcher the authorize rule uses (DEV_TRIGGER_MATCHER) so the two cannot drift.
        if (devTriggerEnabled && DEV_TRIGGER_MATCHER.matches(request)) {
            return false;
        }
        return true;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"));
        configuration.setAllowedHeaders(
            // X-XSRF-TOKEN: the SPA is cross-origin in dev and echoes the double-submit CSRF token on
            //   every state-changing request (see SpaCsrfTokenRequestHandler + webapp/src/main.tsx).
            //   Without it the preflight Access-Control-Allow-Headers omits the header and the browser
            //   blocks every cookie-auth write cross-origin.
            // X-Impersonation-Allow-Writes: opt-in guardrail header for impersonation write requests
            //   (see ImpersonationGuard) — must survive preflight for the same cross-origin reason.
            List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Requested-With",
                "Origin",
                "X-XSRF-TOKEN",
                "X-Impersonation-Allow-Writes"
            )
        );
        configuration.setExposedHeaders(List.of(ReplicaIdentityFilter.HEADER_NAME));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
