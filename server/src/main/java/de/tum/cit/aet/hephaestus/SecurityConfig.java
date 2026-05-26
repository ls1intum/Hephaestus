package de.tum.cit.aet.hephaestus;

import de.tum.cit.aet.hephaestus.config.CorsProperties;
import de.tum.cit.aet.hephaestus.feature.FeatureFlag;
import de.tum.cit.aet.hephaestus.observability.ReplicaIdentityFilter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
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
    AuthoritiesConverter realmRolesAuthoritiesConverter() {
        return claims -> {
            final var realmAccess = Optional.ofNullable((Map<String, Object>) claims.get("realm_access"));
            final var roles = realmAccess.flatMap(map -> Optional.ofNullable((List<String>) map.get("roles")));
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
     * on the worker JWT triggers a Keycloak round-trip that either fails (issuer unreachable) or
     * rejects (different signer), masking the real auth.
     *
     * <p>Always present (even when Keycloak isn't configured), so a dev / worker-only pod can
     * boot without any Keycloak realm.
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
     * Fallback chain when Spring's OAuth2 resource-server autoconfig produces no
     * {@code JwtDecoder} (worker-only pod, smoke tests, fresh dev box). Mutually exclusive with
     * {@link #resourceServerSecurityFilterChain(HttpSecurity, Converter)} via the same gate so
     * only one "any request" chain ever loads at runtime.
     *
     * <p>Honors {@code hephaestus.dev.trigger-enabled=true} the same way the resource-server
     * chain does — without this, a fresh dev box has no way to fire the trigger after enabling
     * the flag, because the lockdown chain owns the {@code anyRequest()} slot.
     */
    @Bean
    @ConditionalOnMissingBean(org.springframework.security.oauth2.jwt.JwtDecoder.class)
    SecurityFilterChain lockdownSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(requests -> {
                requests.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                // OpenAPI / Swagger endpoints are public on the resource-server chain; they must
                // also be public on the lockdown chain so spec generation works on no-Keycloak boots
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

    @Bean
    @ConditionalOnBean(org.springframework.security.oauth2.jwt.JwtDecoder.class)
    SecurityFilterChain resourceServerSecurityFilterChain(
        HttpSecurity http,
        Converter<Jwt, AbstractAuthenticationToken> authenticationConverter
    ) throws Exception {
        http.oauth2ResourceServer(resourceServer -> {
            resourceServer.jwt(jwtConfigurer -> {
                jwtConfigurer.jwtAuthenticationConverter(authenticationConverter);
            });
        });

        http
            .sessionManagement(sessions -> {
                sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS);
            })
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()));

        http.headers(headers ->
            headers
                .frameOptions(frameOptions -> frameOptions.deny())
                .contentTypeOptions(contentType -> {})
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
        );

        http.authorizeHttpRequests(requests -> {
            // CORS preflight requests must be permitted for cross-origin requests to work.
            // Without this, OPTIONS requests are rejected with 403 before CORS headers can be added.
            requests.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
            // Webhook endpoints — authenticated by HMAC (GitHub) or shared-token (GitLab) at the
            // pipeline layer. Spring Security must not block these or external providers can
            // never reach the receiver. See integration.webhook.* and ADR 0008. The unified
            // {@code /webhooks/{kind}} entry point serves GitHub, GitLab, Slack and Outline.
            requests.requestMatchers(HttpMethod.POST, "/webhooks/**").permitAll();
            // OAuth vendor callbacks — authenticated by HMAC-signed state parameter at the
            // controller layer (see OAuthCallbackController). The vendor redirect arrives
            // unauthenticated; Spring Security MUST NOT block it or no OAuth flow ever completes.
            requests.requestMatchers("/oauth/callback/**").permitAll();
            // NOTE: /api/workers/** and /actuator/** are handled by workerHubSecurityFilterChain
            // (highest precedence) which skips the OAuth2 resource server entirely — the worker
            // hub validates its own JWTs via WorkerJwtHandshakeInterceptor.
            // Dev-only: permit the dev trigger endpoint when explicitly enabled (defaults to false)
            if (devTriggerEnabled) {
                requests.requestMatchers("/api/dev/**").permitAll();
            }
            // OpenAPI documentation endpoints (public for spec generation and dev access)
            requests
                .requestMatchers("/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui/**", "/swagger-ui.html")
                .permitAll();
            // Public auth discovery (identity providers for login UI)
            requests.requestMatchers(HttpMethod.GET, "/auth/identity-providers").permitAll();
            // Public workspace provider discovery (workspace creation UI)
            requests.requestMatchers(HttpMethod.GET, "/workspaces/providers").permitAll();
            // Mentor endpoints gated by the MENTOR_ACCESS realm role. MUST be matched BEFORE the
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

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"));
        configuration.setAllowedHeaders(
            List.of("Authorization", "Content-Type", "Accept", "X-Requested-With", "Origin")
        );
        configuration.setExposedHeaders(List.of(ReplicaIdentityFilter.HEADER_NAME));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
