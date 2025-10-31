package de.tum.in.www1.hephaestus.gitprovider.user;

import java.util.Optional;
import org.keycloak.admin.client.Keycloak;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping("/{login}/profile")
    public ResponseEntity<UserProfileDTO> getUserProfile(@PathVariable String login) {
        Optional<UserProfileDTO> userProfile = userService.getUserProfile(login);
        return userProfile.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteUser(JwtAuthenticationToken auth) {
        if (auth == null) {
            logger.error("No authentication token found.");
            return ResponseEntity.badRequest().body(null);
        }

        String userId = auth.getToken().getClaimAsString(StandardClaimNames.SUB);
        logger.info("Deleting user {}", userId);
        var response = keycloak.realm(realm).users().delete(userId);
        if (response.getStatus() != 204) {
            logger.error("Failed to delete user account: {}", response.getStatusInfo().getReasonPhrase());
            return ResponseEntity.badRequest().body(null);
        }
        return ResponseEntity.ok().build();
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
        JwtAuthenticationToken auth,
        @RequestBody UserSettingsDTO userSettings
    ) {
        var user = userRepository.getCurrentUser();
        if (user.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String keycloakUserId = auth.getToken().getClaimAsString(StandardClaimNames.SUB);
        UserSettingsDTO updatedUserSettings = userService.updateUserSettings(user.get(), userSettings, keycloakUserId);
        return ResponseEntity.ok(updatedUserSettings);
    }
}
