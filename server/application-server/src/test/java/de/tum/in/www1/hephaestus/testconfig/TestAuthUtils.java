package de.tum.in.www1.hephaestus.testconfig;

import java.util.function.Consumer;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Utility class for test authentication helpers.
 */
public class TestAuthUtils {

    /**
     * Gets the appropriate JWT token value for the current security context.
     *
     * This allows tests to use @WithMentorUser, @WithAdminUser, etc. and automatically
     * get the correct token for WebTestClient requests.
     *
     * @return JWT token string, or null if no authentication should be used
     */
    public static String getCurrentUserToken() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken) {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            return jwt.getTokenValue();
        }

        // Default fallback for authenticated requests
        return "mock-jwt-token-for-test-user";
    }

    /**
     * Gets the appropriate JWT token value, returning null for unauthenticated requests.
     * Use this when you want to explicitly test unauthenticated scenarios.
     *
     * @return JWT token string, or null if no authentication is present
     */
    public static String getCurrentUserTokenOrNull() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken) {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            return jwt.getTokenValue();
        }

        // Return null for truly unauthenticated requests
        return null;
    }

    /**
     * Creates a headers consumer that adds authentication based on the current security context.
     * If no authentication is present, no Authorization header is added.
     *
     * Usage with WebTestClient:
     * <pre>
     * webTestClient.post()
     *     .uri("/some/endpoint")
     *     .headers(TestAuthUtils.withCurrentUserOrNone())
     *     .exchange()
     * </pre>
     */
    public static Consumer<HttpHeaders> withCurrentUserOrNone() {
        return headers -> {
            String token = getCurrentUserTokenOrNull();
            if (token != null) {
                headers.setBearerAuth(token);
            }
            // If token is null, no Authorization header is set (unauthenticated request)
        };
    }

    /**
     * Creates a headers consumer that adds authentication based on the current security context,
     * with a fallback to testuser if no authentication is present.
     *
     * Usage with WebTestClient:
     * <pre>
     * webTestClient.post()
     *     .uri("/some/endpoint")
     *     .headers(TestAuthUtils.withCurrentUser())
     *     .exchange()
     * </pre>
     */
    public static Consumer<HttpHeaders> withCurrentUser() {
        return headers -> {
            String token = getCurrentUserToken();
            headers.setBearerAuth(token);
        };
    }
}
