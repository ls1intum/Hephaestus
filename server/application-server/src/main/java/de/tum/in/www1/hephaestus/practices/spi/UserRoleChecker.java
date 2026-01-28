package de.tum.in.www1.hephaestus.practices.spi;

import org.springframework.lang.NonNull;

/**
 * Service Provider Interface for checking user roles.
 * <p>
 * This abstraction decouples the activity module from identity provider
 * implementations (Keycloak, etc.), allowing role checks without direct
 * dependency on security infrastructure.
 */
public interface UserRoleChecker {
    /**
     * Checks if a user has the permission to trigger automatic bad practice detection.
     *
     * @param username the username (login) to check
     * @return true if the user has the required role, false otherwise
     */
    boolean hasAutomaticDetectionRole(@NonNull String username);

    /**
     * Returns whether the role checking service is currently healthy.
     * <p>
     * When unhealthy (e.g., identity provider unreachable), callers should
     * gracefully degrade by skipping role checks.
     *
     * @return true if the service is healthy, false if it should be bypassed
     */
    boolean isHealthy();
}
