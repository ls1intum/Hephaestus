package de.tum.in.www1.hephaestus.testconfig;

import de.tum.in.www1.hephaestus.SecurityConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Test security configuration that imports the main SecurityConfig and only overrides 
 * the JWT decoder with a mock implementation. This ensures we use the same security 
 * configuration as production but with a test-friendly JWT decoder.
 */
@TestConfiguration
@Import(SecurityConfig.class)
@Profile("test")
public class TestSecurityConfig {

    /**
     * Mock JWT decoder that creates a valid JWT for testing.
     * This decoder will be used by the main SecurityConfig's OAuth2 resource server configuration.
     * The JWT contains the same realm_access structure as a real Keycloak token.
     */
    @Bean
    @Primary
    public JwtDecoder mockJwtDecoder() {
        return token -> {
            // Create a mock JWT that matches the structure expected by the main SecurityConfig
            Map<String, Object> claims = new HashMap<>();
            claims.put("sub", "test-user");
            claims.put("preferred_username", "testuser");
            claims.put("iss", "https://test-issuer");
            claims.put("aud", "test-audience");
            
            // Add realm_access with mentor_access role (same structure as Keycloak)
            Map<String, Object> realmAccess = new HashMap<>();
            realmAccess.put("roles", Arrays.asList("mentor_access"));
            claims.put("realm_access", realmAccess);
            
            return Jwt.withTokenValue(token)
                .header("alg", "HS256")
                .header("typ", "JWT")
                .claims(claimsMap -> claimsMap.putAll(claims))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        };
    }
}
