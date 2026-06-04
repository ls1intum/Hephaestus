package de.tum.cit.aet.hephaestus.core.security;

import java.util.List;
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
     * This is the standard OIDC {@code given_name} claim, carried on our own issued JWT.
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
     * Check if the current user is an instance super-admin (Account.AppRole APP_ADMIN).
     * Such users can be elevated to workspace-admin level by the authorization layer, but only for
     * workspaces where they are members. This method itself only checks for the presence of the
     * authority. The instance-admin authority is the namespaced {@code app_admin} the issuer mints
     * for APP_ADMIN accounts (see {@code JwtPrincipalFactory}) — deliberately distinct from the
     * per-workspace {@code admin} role, which is membership-derived and never appears in the JWT.
     *
     * @return true if the current user has the {@code app_admin} authority
     */
    public static boolean isSuperAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return false;
        }

        // Flat `roles` claim on the Hephaestus-issued JWT (ADR 0017).
        var rolesObj = jwt.getClaims().get("roles");
        return rolesObj instanceof List<?> roles && roles.contains("app_admin");
    }
}
