package de.tum.in.www1.hephaestus.notification;

import de.tum.in.www1.hephaestus.config.KeycloakProperties;
import de.tum.in.www1.hephaestus.practices.spi.UserRoleChecker;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.ProcessingException;
import java.util.List;
import java.util.function.Supplier;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Keycloak implementation of {@link UserRoleChecker}.
 * <p>
 * Checks user roles via the Keycloak Admin API with graceful degradation
 * when the Keycloak server is unavailable or authentication fails.
 * <p>
 * Uses Resilience4j circuit breaker to prevent cascade failures:
 * <ul>
 *   <li>CLOSED: Normal operation, requests go through</li>
 *   <li>OPEN: Too many failures, requests fail fast</li>
 *   <li>HALF_OPEN: After cooldown, allows test requests</li>
 * </ul>
 *
 * @see io.github.resilience4j.circuitbreaker.CircuitBreaker
 */
@Component
public class KeycloakUserRoleChecker implements UserRoleChecker {

    private static final Logger log = LoggerFactory.getLogger(KeycloakUserRoleChecker.class);
    private static final String AUTOMATIC_DETECTION_ROLE = "run_automatic_detection";

    private final Keycloak keycloak;
    private final KeycloakProperties keycloakProperties;
    private final CircuitBreaker circuitBreaker;

    public KeycloakUserRoleChecker(
        Keycloak keycloak,
        KeycloakProperties keycloakProperties,
        @Qualifier("keycloakCircuitBreaker") CircuitBreaker circuitBreaker
    ) {
        this.keycloak = keycloak;
        this.keycloakProperties = keycloakProperties;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public boolean hasAutomaticDetectionRole(@NonNull String username) {
        Supplier<Boolean> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, () ->
            checkRoleInternal(username)
        );

        try {
            return decoratedSupplier.get();
        } catch (CallNotPermittedException e) {
            log.debug("Skipped role check: reason=circuitOpen, userLogin={}", username);
            return false;
        } catch (NotAuthorizedException e) {
            // 401: Auth failures indicate credential misconfiguration - log at WARN level
            // so operators can see this clearly, but don't spam (circuit breaker ignores these)
            log.warn(
                "Keycloak authentication failed (401) - check KEYCLOAK_CLIENT_SECRET configuration: userLogin={}, error={}",
                username,
                e.getMessage()
            );
            return false;
        } catch (ForbiddenException e) {
            // 403: Client lacks required permissions - check Keycloak client role mappings
            log.warn(
                "Keycloak access forbidden (403) - check client has 'view-users' role in Keycloak: userLogin={}, error={}",
                username,
                e.getMessage()
            );
            return false;
        } catch (ProcessingException e) {
            // Connection/transport failures are recorded by the circuit breaker
            log.debug("Keycloak request failed: userLogin={}, error={}", username, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Unexpected error checking user role: userLogin={}", username, e);
            return false;
        }
    }

    /**
     * Internal method to check user role - called within circuit breaker.
     */
    private boolean checkRoleInternal(String username) {
        List<UserRepresentation> users = keycloak
            .realm(keycloakProperties.realm())
            .users()
            .searchByUsername(username, true);

        if (users.isEmpty()) {
            log.debug("User not found in Keycloak: userLogin={}", username);
            return false;
        }

        UserRepresentation user = users.getFirst();
        List<RoleRepresentation> roles = keycloak
            .realm(keycloakProperties.realm())
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

        return hasRole;
    }

    /**
     * Returns whether the Keycloak integration is healthy enough to accept requests.
     * <p>
     * Both CLOSED and HALF_OPEN states are considered healthy:
     * <ul>
     *   <li>CLOSED: Normal operation, all requests allowed</li>
     *   <li>HALF_OPEN: Recovery testing in progress, limited requests allowed to verify service recovery</li>
     * </ul>
     * <p>
     * Only OPEN state is unhealthy - the circuit has tripped and requests fail fast.
     * Treating HALF_OPEN as healthy allows normal traffic to participate in recovery testing
     * rather than artificially delaying recovery by skipping all checks.
     */
    @Override
    public boolean isHealthy() {
        CircuitBreaker.State state = circuitBreaker.getState();
        return state == CircuitBreaker.State.CLOSED || state == CircuitBreaker.State.HALF_OPEN;
    }

    // Package-private for testing
    CircuitBreaker.State getCircuitState() {
        return circuitBreaker.getState();
    }
}
