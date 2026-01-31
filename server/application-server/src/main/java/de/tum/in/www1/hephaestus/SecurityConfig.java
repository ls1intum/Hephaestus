package de.tum.in.www1.hephaestus;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
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
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final List<String> allowedOrigins;

    public SecurityConfig(
        @Value("${hephaestus.cors.allowed-origins:http://localhost:4200}") List<String> allowedOrigins
    ) {
        this.allowedOrigins = allowedOrigins;
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

    @Bean
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
            // Actuator endpoints for Docker/K8s health checks and basic info
            requests.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll();
            // OpenAPI documentation endpoints (public for spec generation and dev access)
            requests
                .requestMatchers("/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui/**", "/swagger-ui.html")
                .permitAll();
            // Public read for slugged workspace paths (filter enforces membership/public visibility).
            requests.requestMatchers(HttpMethod.GET, "/workspaces/*/**").permitAll();
            // Registry/listing stays authenticated to avoid leaking tenant directory.
            requests.requestMatchers(HttpMethod.GET, "/workspaces", "/workspaces/").authenticated();
            // Non-GET workspace operations still require authentication.
            requests.requestMatchers("/workspaces/**").authenticated();
            requests.requestMatchers("/mentor/**").hasAuthority("mentor_access");
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
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"));
        configuration.setAllowedHeaders(
            List.of("Authorization", "Content-Type", "Accept", "X-Requested-With", "Origin")
        );
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
