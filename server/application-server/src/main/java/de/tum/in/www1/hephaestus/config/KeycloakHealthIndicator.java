package de.tum.in.www1.hephaestus.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.keycloak.admin.client.Keycloak;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for Keycloak connectivity.
 * <p>
 * Reports the health status of the Keycloak integration by checking:
 * <ol>
 *   <li>Circuit breaker state - if OPEN, reports DOWN immediately to avoid unnecessary calls</li>
 *   <li>Keycloak realm accessibility - performs a lightweight realm info fetch</li>
 * </ol>
 * <p>
 * Health details include:
 * <ul>
 *   <li>{@code url}: The configured Keycloak server URL</li>
 *   <li>{@code realm}: The configured realm name</li>
 *   <li>{@code circuitBreaker}: Current circuit breaker state</li>
 * </ul>
 *
 * @see org.springframework.boot.actuate.health.HealthIndicator
 */
@Component
public class KeycloakHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(KeycloakHealthIndicator.class);

    private final Keycloak keycloak;
    private final KeycloakProperties keycloakProperties;
    private final CircuitBreaker circuitBreaker;

    public KeycloakHealthIndicator(
        Keycloak keycloak,
        KeycloakProperties keycloakProperties,
        @Qualifier("keycloakCircuitBreaker") CircuitBreaker circuitBreaker
    ) {
        this.keycloak = keycloak;
        this.keycloakProperties = keycloakProperties;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public Health health() {
        String internalUrl = keycloakProperties.effectiveInternalUrl();
        String realm = keycloakProperties.realm();
        CircuitBreaker.State circuitState = circuitBreaker.getState();

        // If circuit breaker is OPEN, fail fast without making a request
        if (circuitState == CircuitBreaker.State.OPEN) {
            log.debug("Keycloak health check skipped: circuit breaker is OPEN");
            return Health.down()
                .withDetail("url", internalUrl)
                .withDetail("realm", realm)
                .withDetail("circuitBreaker", circuitState.name())
                .withDetail("error", "Circuit breaker is OPEN - Keycloak unavailable")
                .build();
        }

        try {
            // Lightweight operation: fetch realm representation
            keycloak.realm(realm).toRepresentation();

            log.debug("Keycloak health check succeeded: realm={}", realm);
            return Health.up()
                .withDetail("url", internalUrl)
                .withDetail("realm", realm)
                .withDetail("circuitBreaker", circuitState.name())
                .build();
        } catch (Exception e) {
            log.warn("Keycloak health check failed: realm={}, error={}", realm, e.getMessage());
            return Health.down()
                .withDetail("url", internalUrl)
                .withDetail("realm", realm)
                .withDetail("circuitBreaker", circuitState.name())
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
