package de.tum.in.www1.hephaestus.account;

import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.integrations.posthog.PosthogClientException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.keycloak.admin.client.Keycloak;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/**
 * Controller for user account management operations.
 * Handles account deletion (GDPR) and user preferences/settings.
 */
@RestController
@RequestMapping("/user")
@Tag(name = "Account", description = "User account management (settings, deletion)")
public class AccountController {

    private static final Logger logger = LoggerFactory.getLogger(AccountController.class);

    private final AccountService accountService;
    private final Keycloak keycloak;
    private final UserRepository userRepository;

    @Value("${keycloak.realm}")
    private String realm;

    public AccountController(AccountService accountService, Keycloak keycloak, UserRepository userRepository) {
        this.accountService = accountService;
        this.keycloak = keycloak;
        this.userRepository = userRepository;
    }

    @DeleteMapping
    @Operation(
        summary = "Delete user account",
        description = "Permanently delete the current user's account and all associated data (GDPR)"
    )
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
            accountService.deleteUserTrackingData(gitUser, keycloakUserId);
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
    @Operation(
        summary = "Get user settings",
        description = "Get the current user's notification and research participation preferences"
    )
    public ResponseEntity<UserSettingsDTO> getUserSettings() {
        var user = userRepository.getCurrentUser();
        if (user.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        UserSettingsDTO userSettings = accountService.getUserSettings(user.get());
        return ResponseEntity.ok(userSettings);
    }

    @PostMapping("/settings")
    @Operation(
        summary = "Update user settings",
        description = "Update the current user's notification and research participation preferences"
    )
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
        UserSettingsDTO updatedUserSettings = accountService.updateUserSettings(
            user.get(),
            userSettings,
            keycloakUserId
        );
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
