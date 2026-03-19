package de.tum.in.www1.hephaestus.practices.spi;

import org.springframework.lang.NonNull;

/**
 * Service Provider Interface for checking user roles.
 * <p>
 * This abstraction decouples the activity module from identity provider
 * implementations (Keycloak, etc.), allowing role checks without direct
 * dependency on security infrastructure.
 * <p>
 * Role name constants should be defined at call sites, not in this interface,
 * to keep the SPI generic and prevent method proliferation per role.
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li><strong>Fail-closed:</strong> implementations MUST return {@code false}
 *       when the identity provider is unreachable or the check cannot be completed.
 *       They must never throw exceptions from {@link #hasRole}.</li>
 *   <li><strong>Preconditions:</strong> {@code username} and {@code roleName} must
 *       be non-null and {@code roleName} must not be blank. Implementations should
 *       fail fast on violations before any network I/O.</li>
 * </ul>
 */
public interface UserRoleChecker {
    /**
     * Checks if a user has a specific realm role.
     * <p>
     * Returns {@code false} on any error (network failure, auth misconfiguration,
     * circuit breaker open) — never throws. This fail-closed behavior ensures that
     * callers can safely use the result without wrapping in try-catch.
     *
     * @param username the username (login) to check — must not be null
     * @param roleName the realm role name to check for (e.g., "run_automatic_detection") — must not be null or blank
     * @return true if the user has the specified role, false otherwise (including on error)
     */
    boolean hasRole(@NonNull String username, @NonNull String roleName);

    /**
     * Returns whether the role checking service is currently healthy.
     * <p>
     * When unhealthy (e.g., identity provider unreachable), callers should
     * gracefully degrade by skipping the entire operation rather than calling
     * {@link #hasRole}, which would return {@code false} anyway.
     *
     * @return true if the service is healthy, false if it should be bypassed
     */
    boolean isHealthy();
}
