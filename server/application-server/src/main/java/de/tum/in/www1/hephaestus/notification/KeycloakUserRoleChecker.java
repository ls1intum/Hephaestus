package de.tum.in.www1.hephaestus.notification;

import de.tum.in.www1.hephaestus.practices.spi.UserRoleChecker;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.ProcessingException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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
 */
@Component
public class KeycloakUserRoleChecker implements UserRoleChecker {

    private static final Logger log = LoggerFactory.getLogger(KeycloakUserRoleChecker.class);
    private static final String AUTOMATIC_DETECTION_ROLE = "run_automatic_detection";

    private final Keycloak keycloak;
    private final String realm;
    private final AtomicBoolean healthy = new AtomicBoolean(true);

    public KeycloakUserRoleChecker(Keycloak keycloak, @Value("${keycloak.realm}") String realm) {
        this.keycloak = keycloak;
        this.realm = realm;
    }

    @Override
    public boolean hasAutomaticDetectionRole(@NonNull String username) {
        if (!healthy.get()) {
            log.debug("Skipped role check: reason=keycloakUnhealthy, userLogin={}", username);
            return false;
        }

        try {
            List<UserRepresentation> users = keycloak.realm(realm).users().searchByUsername(username, true);

            if (users.isEmpty()) {
                log.debug("User not found in Keycloak: userLogin={}", username);
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

            return hasRole;
        } catch (ProcessingException | NotAuthorizedException e) {
            if (healthy.compareAndSet(true, false)) {
                // Log without stack trace - connection/auth failures trigger fallback behavior
                log.warn("Disabled Keycloak role checks: reason=authenticationFailed, userLogin={}", username);
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to check user role: userLogin={}", username, e);
            return false;
        }
    }

    @Override
    public boolean isHealthy() {
        return healthy.get();
    }
}
