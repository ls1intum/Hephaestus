package de.tum.in.www1.hephaestus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Utility class for Spring Security.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    /**
     * Get the login of the current user.
     *
     * @return the login of the current user.
     */
    public static Optional<String> getCurrentUserLogin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return Optional.ofNullable(jwt.getClaimAsString("preferred_username"));
        }
        return Optional.empty();
    }

    /**
     * Get the login of the current user or throw an exception if not authenticated.
     *
     * @return the login of the current user.
     * @throws IllegalStateException if no authenticated user is found.
     */
    public static String getCurrentUserLoginOrThrow() {
        return getCurrentUserLogin().orElseThrow(() -> new IllegalStateException("No authenticated user found"));
    }

    /**
     * Get the first name (given_name) from a JWT token.
     * This is the standard OIDC claim for first name used by Keycloak.
     *
     * @param jwt The JWT token
     * @return The given_name claim value, or empty if not present
     */
    public static Optional<String> getGivenName(Jwt jwt) {
        if (jwt == null) {
            return Optional.empty();
        }
        String givenName = jwt.getClaimAsString("given_name");
        if (givenName != null && !givenName.isBlank()) {
            return Optional.of(givenName);
        }
        return Optional.empty();
    }

    /**
     * Check if the current user has the global admin realm role.
     * Users with the admin realm role (configured via KEYCLOAK_GITHUB_ADMIN_USERNAME)
     * are automatically granted workspace admin privileges across all workspaces.
     *
     * @return true if the current user has the admin realm role
     */
    @SuppressWarnings("unchecked")
    public static boolean isGlobalAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return false;
        }

        // Extract realm_access.roles from JWT claims (following SecurityConfig pattern)
        Object realmAccessObj = jwt.getClaims().get("realm_access");
        if (!(realmAccessObj instanceof Map)) {
            return false;
        }

        Map<String, Object> realmAccess = (Map<String, Object>) realmAccessObj;
        Object rolesObj = realmAccess.get("roles");
        if (!(rolesObj instanceof List)) {
            return false;
        }

        List<String> roles = (List<String>) rolesObj;
        return roles.contains("admin");
    }
}
