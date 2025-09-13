package de.tum.in.www1.hephaestus.testconfig;

import de.tum.in.www1.hephaestus.SecurityConfig;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

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
     *
     * It dynamically determines the user based on the token value pattern:
     * - "mock-jwt-token-for-mentor-user" -> mentor user
     * - "mock-jwt-token-for-admin-user" -> admin user
     * - "mock-jwt-token-for-test-user" -> test user
     * - any other token -> defaults to testuser
     */
    @Bean
    @Primary
    public JwtDecoder mockJwtDecoder() {
        return token -> {
            // Determine user based on token pattern
            String username;
            String userId;
            String[] roles;

            if ("mock-jwt-token-for-mentor-user".equals(token)) {
                username = "mentor";
                userId = "mentor-user-id";
                roles = new String[] { "mentor_access" };
            } else if ("mock-jwt-token-for-admin-user".equals(token)) {
                username = "admin";
                userId = "admin-user-id";
                roles = new String[] { "admin" };
            } else if ("mock-jwt-token-for-test-user".equals(token)) {
                username = "testuser";
                userId = "test-user-id";
                roles = new String[] {};
            } else {
                // Default fallback
                username = "testuser";
                userId = "test-user-id";
                roles = new String[] {};
            }

            // Create a mock JWT that matches the structure expected by the main SecurityConfig
            Map<String, Object> claims = new HashMap<>();
            claims.put("sub", userId);
            claims.put("preferred_username", username);
            claims.put("iss", "https://test-issuer");
            claims.put("aud", "test-audience");

            // Add realm_access with roles (same structure as Keycloak)
            if (roles.length > 0) {
                Map<String, Object> realmAccess = new HashMap<>();
                realmAccess.put("roles", Arrays.asList(roles));
                claims.put("realm_access", realmAccess);
            }

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
