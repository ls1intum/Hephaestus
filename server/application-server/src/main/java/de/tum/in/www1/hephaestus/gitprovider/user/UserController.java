package de.tum.in.www1.hephaestus.gitprovider.user;

import de.tum.in.www1.hephaestus.integrations.posthog.PosthogClientException;
import org.keycloak.admin.client.Keycloak;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private Keycloak keycloak;

    @Autowired
    private UserRepository userRepository;

    @Value("${keycloak.realm}")
    private String realm;

    public UserController(UserService actorService) {
        this.userService = actorService;
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteUser(@AuthenticationPrincipal JwtAuthenticationToken auth) {
        JwtAuthenticationToken token = resolveAuthentication(auth);
        if (token == null) {
            logger.error("No authentication token found");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String keycloakUserId = token.getToken().getClaimAsString(StandardClaimNames.SUB);
        var gitUser = userRepository.getCurrentUser();
        if (gitUser.isEmpty()) {
            logger.warn("Could not resolve Git provider user for Keycloak subject {}", keycloakUserId);
        }

        try {
            userService.deleteUserTrackingData(gitUser, keycloakUserId);
        } catch (PosthogClientException exception) {
            logger.error("Failed to remove analytics data before deleting user {}", keycloakUserId, exception);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }

        logger.info("Deleting user {}", keycloakUserId);
        var response = keycloak.realm(realm).users().delete(keycloakUserId);
        if (response.getStatus() != 204) {
            logger.error("Failed to delete user account: {}", response.getStatusInfo().getReasonPhrase());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/settings")
    public ResponseEntity<UserSettingsDTO> getUserSettings() {
        var user = userRepository.getCurrentUser();
        if (user.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        UserSettingsDTO userSettings = userService.getUserSettings(user.get());
        return ResponseEntity.ok(userSettings);
    }

    @PostMapping("/settings")
    public ResponseEntity<UserSettingsDTO> updateUserSettings(
        @AuthenticationPrincipal JwtAuthenticationToken auth,
        @RequestBody UserSettingsDTO userSettings
    ) {
        var user = userRepository.getCurrentUser();
        if (user.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        JwtAuthenticationToken token = resolveAuthentication(auth);
        String keycloakUserId = null;
        if (token != null) {
            keycloakUserId = token.getToken().getClaimAsString(StandardClaimNames.SUB);
        } else {
            logger.warn("Updating user settings without an authenticated principal");
            boolean switchingOffResearch =
                Boolean.FALSE.equals(userSettings.participateInResearch()) && user.get().isParticipateInResearch();
            if (switchingOffResearch) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        }
        UserSettingsDTO updatedUserSettings = userService.updateUserSettings(user.get(), userSettings, keycloakUserId);
        return ResponseEntity.ok(updatedUserSettings);
    }

    private JwtAuthenticationToken resolveAuthentication(JwtAuthenticationToken injectedToken) {
        if (injectedToken != null) {
            return injectedToken;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return jwtAuthenticationToken;
        }
        return null;
    }
}
