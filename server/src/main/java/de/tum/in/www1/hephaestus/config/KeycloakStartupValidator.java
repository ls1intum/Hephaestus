package de.tum.in.www1.hephaestus.config;

import jakarta.annotation.PostConstruct;
import org.keycloak.admin.client.Keycloak;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Validates Keycloak connectivity at application startup.
 *
 * <p>When enabled via {@code hephaestus.keycloak.validate-on-startup=true}, this validator
 * attempts to connect to Keycloak during application initialization. It retries the connection
 * for up to 2 minutes with 5-second intervals between attempts.
 *
 * <p>This is useful in containerized environments where Keycloak might not be immediately
 * available when the application starts. If validation fails after all retries, the application
 * continues to start (allowing the circuit breaker to handle degradation gracefully) but logs
 * an error for visibility.
 *
 * <h2>Configuration</h2>
 * <pre>{@code
 * hephaestus:
 *   keycloak:
 *     validate-on-startup: true  # disabled by default
 * }</pre>
 *
 * @see KeycloakProperties
 * @see KeycloakConfig
 */
@Component
@ConditionalOnProperty(name = "hephaestus.keycloak.validate-on-startup", havingValue = "true", matchIfMissing = false)
public class KeycloakStartupValidator {

    private static final Logger logger = LoggerFactory.getLogger(KeycloakStartupValidator.class);

    private static final long RETRY_INTERVAL_MS = 5_000L;
    private static final long MAX_WAIT_TIME_MS = 120_000L;

    private final Keycloak keycloak;
    private final KeycloakProperties keycloakProperties;

    public KeycloakStartupValidator(Keycloak keycloak, KeycloakProperties keycloakProperties) {
        this.keycloak = keycloak;
        this.keycloakProperties = keycloakProperties;
    }

    /**
     * Validates Keycloak connectivity at startup with retry logic.
     *
     * <p>Attempts to connect to Keycloak by retrieving realm information. Retries every
     * 5 seconds for up to 2 minutes. If all attempts fail, logs an error but allows
     * the application to continue starting.
     */
    @PostConstruct
    public void validateKeycloakConnectivity() {
        logger.info("Validating Keycloak connectivity at {}", keycloakProperties.effectiveInternalUrl());

        long startTime = System.currentTimeMillis();
        int attempt = 0;

        while (System.currentTimeMillis() - startTime < MAX_WAIT_TIME_MS) {
            attempt++;
            try {
                // Attempt to fetch realm info as a connectivity check
                keycloak.realm(keycloakProperties.realm()).toRepresentation();
                logger.info("Keycloak connectivity verified");
                return;
            } catch (Exception e) {
                long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
                long remainingSeconds = (MAX_WAIT_TIME_MS - (System.currentTimeMillis() - startTime)) / 1000;

                logger.warn(
                    "Keycloak not ready (attempt {}), elapsed: {}s, remaining: {}s - {}",
                    attempt,
                    elapsedSeconds,
                    remainingSeconds,
                    e.getMessage()
                );

                if (System.currentTimeMillis() - startTime + RETRY_INTERVAL_MS >= MAX_WAIT_TIME_MS) {
                    break;
                }

                try {
                    Thread.sleep(RETRY_INTERVAL_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.warn("Keycloak validation interrupted");
                    return;
                }
            }
        }

        logger.error(
            "Failed to verify Keycloak connectivity after {} attempts over {} seconds. " +
                "Application will continue, but authentication may be degraded until Keycloak becomes available.",
            attempt,
            MAX_WAIT_TIME_MS / 1000
        );
    }
}
