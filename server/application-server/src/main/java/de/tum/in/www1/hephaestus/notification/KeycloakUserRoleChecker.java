package de.tum.in.www1.hephaestus.notification;

import de.tum.in.www1.hephaestus.practices.spi.UserRoleChecker;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.ProcessingException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Keycloak implementation of {@link UserRoleChecker}.
 * <p>
 * Checks user roles via the Keycloak Admin API with graceful degradation
 * when the Keycloak server is unavailable or authentication fails.
 * <p>
 * Implements a circuit breaker pattern to prevent cascade failures:
 * <ul>
 *   <li>CLOSED: Normal operation, requests go through</li>
 *   <li>OPEN: Too many failures, requests are blocked temporarily</li>
 *   <li>HALF_OPEN: After cooldown, allows one request to test recovery</li>
 * </ul>
 */
@Component
public class KeycloakUserRoleChecker implements UserRoleChecker {

    private static final Logger log = LoggerFactory.getLogger(KeycloakUserRoleChecker.class);
    private static final String AUTOMATIC_DETECTION_ROLE = "run_automatic_detection";

    /**
     * Circuit breaker states.
     */
    enum CircuitState {
        CLOSED,
        OPEN,
        HALF_OPEN,
    }

    // Circuit breaker configuration
    private static final int FAILURE_THRESHOLD = 3;
    private static final Duration COOLDOWN_PERIOD = Duration.ofMinutes(5);

    private final Keycloak keycloak;
    private final String realm;
    private final Clock clock;

    // Circuit breaker state
    private final AtomicReference<CircuitState> circuitState = new AtomicReference<>(CircuitState.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicReference<Instant> lastFailureTime = new AtomicReference<>(Instant.MIN);

    public KeycloakUserRoleChecker(Keycloak keycloak, @Value("${keycloak.realm}") String realm, Clock clock) {
        this.keycloak = keycloak;
        this.realm = realm;
        this.clock = clock;
    }

    @Override
    public boolean hasAutomaticDetectionRole(@NonNull String username) {
        if (!shouldAttemptRequest()) {
            log.debug("Skipped role check: reason=circuitOpen, userLogin={}, state={}", username, circuitState.get());
            return false;
        }

        try {
            List<UserRepresentation> users = keycloak.realm(realm).users().searchByUsername(username, true);

            if (users.isEmpty()) {
                log.debug("User not found in Keycloak: userLogin={}", username);
                // User not found is not a circuit breaker failure - Keycloak responded successfully
                onSuccess();
                return false;
            }

            UserRepresentation user = users.getFirst();
            List<RoleRepresentation> roles = keycloak
                .realm(realm)
                .users()
                .get(user.getId())
                .roles()
                .realmLevel()
                .listAll();

            boolean hasRole = roles.stream().anyMatch(role -> AUTOMATIC_DETECTION_ROLE.equals(role.getName()));

            if (hasRole) {
                log.debug("User has role: userLogin={}, role={}", username, AUTOMATIC_DETECTION_ROLE);
            } else {
                log.debug("User missing role: userLogin={}, role={}", username, AUTOMATIC_DETECTION_ROLE);
            }

            onSuccess();
            return hasRole;
        } catch (ProcessingException | NotAuthorizedException e) {
            onFailure(username, e);
            return false;
        } catch (Exception e) {
            log.error("Failed to check user role: userLogin={}", username, e);
            return false;
        }
    }

    @Override
    public boolean isHealthy() {
        return circuitState.get() == CircuitState.CLOSED;
    }

    /**
     * Determines whether a request should be attempted based on circuit state.
     */
    private boolean shouldAttemptRequest() {
        CircuitState state = circuitState.get();

        if (state == CircuitState.CLOSED) {
            return true;
        }

        if (state == CircuitState.OPEN) {
            // Check if cooldown period has elapsed
            Instant lastFailure = lastFailureTime.get();
            Instant now = clock.instant();

            if (Duration.between(lastFailure, now).compareTo(COOLDOWN_PERIOD) >= 0) {
                // Transition to half-open state to allow a test request
                if (circuitState.compareAndSet(CircuitState.OPEN, CircuitState.HALF_OPEN)) {
                    log.info("Circuit breaker transitioning to HALF_OPEN state, allowing test request");
                }
                return true;
            }
            return false;
        }

        // HALF_OPEN state - allow the test request through
        return true;
    }

    /**
     * Called when a request succeeds - reset the circuit breaker.
     */
    private void onSuccess() {
        CircuitState previousState = circuitState.getAndSet(CircuitState.CLOSED);
        int previousFailures = consecutiveFailures.getAndSet(0);

        if (previousState != CircuitState.CLOSED) {
            log.info(
                "Circuit breaker recovered: previousState={}, previousFailures={}",
                previousState,
                previousFailures
            );
        }
    }

    /**
     * Called when a request fails - potentially open the circuit.
     */
    private void onFailure(String username, Exception e) {
        lastFailureTime.set(clock.instant());
        int failures = consecutiveFailures.incrementAndGet();

        CircuitState currentState = circuitState.get();

        if (currentState == CircuitState.HALF_OPEN) {
            // Test request failed, go back to OPEN
            circuitState.set(CircuitState.OPEN);
            log.warn(
                "Circuit breaker test request failed, returning to OPEN state: userLogin={}, cooldownMinutes={}",
                username,
                COOLDOWN_PERIOD.toMinutes()
            );
        } else if (failures >= FAILURE_THRESHOLD) {
            // Too many failures, open the circuit
            if (circuitState.compareAndSet(CircuitState.CLOSED, CircuitState.OPEN)) {
                log.warn(
                    "Circuit breaker opened after {} consecutive failures: userLogin={}, cooldownMinutes={}",
                    failures,
                    username,
                    COOLDOWN_PERIOD.toMinutes()
                );
            }
        } else {
            log.debug(
                "Keycloak request failed: userLogin={}, consecutiveFailures={}, threshold={}",
                username,
                failures,
                FAILURE_THRESHOLD
            );
        }
    }

    // Package-private methods for testing
    CircuitState getCircuitState() {
        return circuitState.get();
    }

    int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }
}
