package de.tum.cit.aet.hephaestus.practices.spi;

import org.springframework.lang.NonNull;

/**
 * Service Provider Interface for checking user roles.
 * <p>
 * This abstraction decouples the activity module from the role-storage
 * implementation (the {@code account_feature} table), allowing role checks
 * without direct dependency on security infrastructure.
 * <p>
 * Role name constants should be defined at call sites, not in this interface,
 * to keep the SPI generic and prevent method proliferation per role.
 *
 * <h2>Contract</h2>
 * <strong>Fail-closed:</strong> implementations MUST return {@code false} when the check cannot be
 * completed (provider unreachable, null/blank inputs, any error) and MUST never throw from
 * {@link #hasRole} — callers rely on the result without a try-catch.
 */
public interface UserRoleChecker {
    /**
     * Fail-closed: returns {@code false} on any error (and never throws), so callers need no try-catch.
     */
    boolean hasRole(@NonNull String username, @NonNull String roleName);

    /**
     * When unhealthy, callers should skip the operation entirely rather than call {@link #hasRole}.
     */
    boolean isHealthy();
}
