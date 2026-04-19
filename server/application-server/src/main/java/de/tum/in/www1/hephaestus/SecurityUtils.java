package de.tum.in.www1.hephaestus;

import de.tum.in.www1.hephaestus.core.LoggingUtils;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Utility class for Spring Security.
 */
public final class SecurityUtils {

    private static final Logger log = LoggerFactory.getLogger(SecurityUtils.class);

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
     * Get the current authenticated JWT, if any.
     */
    public static Optional<Jwt> getCurrentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        return Optional.of(jwt);
    }

    /**
     * Get the GitHub numeric user id claim ({@code github_id}) from the current JWT.
     * <p>
     * This claim is populated by a Keycloak protocol mapper attached to the GitHub IdP and
     * identifies the authenticated user's GitHub account independently of {@code preferred_username}
     * (which reflects the Keycloak username, not the GitHub login).
     */
    public static Optional<Long> getCurrentGitHubId() {
        return getCurrentJwt().flatMap(jwt -> readLongClaim(jwt, "github_id"));
    }

    /**
     * Get the GitLab numeric user id claim ({@code gitlab_id}) from the current JWT.
     * <p>
     * Populated by a Keycloak protocol mapper attached to the GitLab IdP.
     */
    public static Optional<Long> getCurrentGitLabId() {
        return getCurrentJwt().flatMap(jwt -> readLongClaim(jwt, "gitlab_id"));
    }

    private static Optional<Long> readLongClaim(Jwt jwt, String claimName) {
        // Some Keycloak protocol-mapper configurations emit user-attribute claims as a
        // single-element array instead of a scalar. Unwrap once before parsing; reject
        // everything else (multi-valued arrays, unparseable strings) with a WARN so a
        // misconfigured mapper is visible in logs instead of silently resolving to empty.
        Object raw = jwt.getClaim(claimName);
        if (raw instanceof Collection<?> c) {
            if (c.size() != 1) {
                log.warn("Ignoring multi-valued or empty JWT claim: claim={}, size={}", claimName, c.size());
                return Optional.empty();
            }
            raw = c.iterator().next();
        }
        if (raw == null) {
            return Optional.empty();
        }
        if (raw instanceof Number n) {
            return Optional.of(n.longValue());
        }
        try {
            return Optional.of(Long.parseLong(raw.toString().trim()));
        } catch (NumberFormatException ex) {
            log.warn(
                "Ignoring unparseable JWT claim: claim={}, value={}",
                claimName,
                LoggingUtils.sanitizeForLog(String.valueOf(raw))
            );
            return Optional.empty();
        }
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
     * Check if the current user has the super admin realm role.
     * Users with the admin realm role (configured via KEYCLOAK_GITHUB_ADMIN_USERNAME)
     * can be elevated to workspace admin level by the authorization layer, but only for workspaces
     * where they are members. This method itself only checks for the presence of the realm role.
     *
     * @return true if the current user has the admin realm role
     */
    public static boolean isSuperAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return false;
        }

        // Extract realm_access.roles from JWT claims (following SecurityConfig pattern)
        var realmAccessObj = jwt.getClaims().get("realm_access");
        if (!(realmAccessObj instanceof Map<?, ?> realmAccess)) {
            return false;
        }

        var rolesObj = realmAccess.get("roles");
        return rolesObj instanceof List<?> roles && roles.contains("admin");
    }
}
