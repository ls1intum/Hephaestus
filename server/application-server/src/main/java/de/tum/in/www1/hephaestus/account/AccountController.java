package de.tum.in.www1.hephaestus.account;

import de.tum.in.www1.hephaestus.config.KeycloakProperties;
import de.tum.in.www1.hephaestus.gitprovider.user.AuthenticatedGitProviderUserService;
import de.tum.in.www1.hephaestus.gitprovider.user.AuthenticatedUserService;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.integrations.posthog.PosthogClientException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import org.keycloak.admin.client.Keycloak;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for user account management operations.
 * Handles account deletion (GDPR) and user preferences/settings.
 */
@Validated
@RestController
@RequestMapping("/user")
@Tag(name = "Account", description = "User account management (settings, deletion)")
@PreAuthorize("isAuthenticated()")
public class AccountController {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    private final AccountService accountService;
    private final Keycloak keycloak;
    private final AuthenticatedUserService authenticatedUserService;
    private final KeycloakProperties keycloakProperties;
    private final AuthenticatedGitProviderUserService authenticatedGitProviderUserService;

    public AccountController(
        AccountService accountService,
        Keycloak keycloak,
        AuthenticatedUserService authenticatedUserService,
        KeycloakProperties keycloakProperties,
        AuthenticatedGitProviderUserService authenticatedGitProviderUserService
    ) {
        this.accountService = accountService;
        this.keycloak = keycloak;
        this.authenticatedUserService = authenticatedUserService;
        this.keycloakProperties = keycloakProperties;
        this.authenticatedGitProviderUserService = authenticatedGitProviderUserService;
    }

    @DeleteMapping
    @Operation(
        summary = "Delete user account",
        description = "Permanently delete the current user's account and all associated data (GDPR)"
    )
    public ResponseEntity<Void> deleteUser(@AuthenticationPrincipal JwtAuthenticationToken auth) {
        JwtAuthenticationToken token = resolveAuthentication(auth);
        if (token == null) {
            log.error("No authentication token found");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String keycloakUserId = token.getToken().getClaimAsString(StandardClaimNames.SUB);
        List<User> gitUsers = authenticatedUserService.findAllLinkedUsers();
        if (gitUsers.isEmpty()) {
            log.warn("Could not resolve Git provider user for Keycloak subject {}", keycloakUserId);
        }

        try {
            accountService.deleteUserTrackingData(gitUsers, keycloakUserId);
        } catch (PosthogClientException exception) {
            log.error("Failed to remove analytics data before deleting user {}", keycloakUserId, exception);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }

        log.info("Deleting user {}", keycloakUserId);
        var response = keycloak.realm(keycloakProperties.realm()).users().delete(keycloakUserId);
        if (response.getStatus() != 204) {
            log.error("Failed to delete user account: {}", response.getStatusInfo().getReasonPhrase());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/settings")
    @Operation(
        summary = "Get user settings",
        description = "Get the current user's notification, research participation, and AI review preferences"
    )
    public ResponseEntity<UserSettingsDTO> getUserSettings(@AuthenticationPrincipal JwtAuthenticationToken auth) {
        var user = resolveOrProvisionCurrentUser(auth);
        if (user.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        UserSettingsDTO userSettings = accountService.getUserSettings(user.get());
        return ResponseEntity.ok(userSettings);
    }

    @PostMapping("/settings")
    @Operation(
        summary = "Update user settings",
        description = "Update the current user's notification, research participation, and AI review preferences"
    )
    public ResponseEntity<UserSettingsDTO> updateUserSettings(
        @AuthenticationPrincipal JwtAuthenticationToken auth,
        @Valid @RequestBody UserSettingsDTO userSettings
    ) {
        var user = resolveOrProvisionCurrentUser(auth);
        if (user.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        JwtAuthenticationToken token = resolveAuthentication(auth);
        String keycloakUserId = null;
        if (token != null) {
            keycloakUserId = token.getToken().getClaimAsString(StandardClaimNames.SUB);
        } else {
            log.warn("Updating user settings without an authenticated principal");
            UserPreferences preferences = accountService.getOrCreatePreferences(user.get());
            boolean switchingOffResearch =
                Boolean.FALSE.equals(userSettings.participateInResearch()) && preferences.isParticipateInResearch();
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

    @GetMapping("/linked-accounts")
    @Operation(
        summary = "List linked identity providers",
        description = "Returns all configured identity providers with their connection status for the current user"
    )
    public ResponseEntity<List<LinkedAccountDTO>> getLinkedAccounts(
        @AuthenticationPrincipal JwtAuthenticationToken auth
    ) {
        JwtAuthenticationToken token = resolveAuthentication(auth);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String keycloakUserId = token.getToken().getClaimAsString(StandardClaimNames.SUB);
        return ResponseEntity.ok(accountService.getLinkedAccounts(keycloakUserId));
    }

    @DeleteMapping("/linked-accounts/{providerAlias}")
    @Operation(
        summary = "Unlink an identity provider",
        description = "Remove the federated identity link for the given provider. Cannot unlink the last remaining provider."
    )
    public ResponseEntity<Void> unlinkAccount(
        @PathVariable @jakarta.validation.constraints.Pattern(regexp = "^[a-z0-9-]{1,64}$") String providerAlias,
        @AuthenticationPrincipal JwtAuthenticationToken auth
    ) {
        JwtAuthenticationToken token = resolveAuthentication(auth);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String keycloakUserId = token.getToken().getClaimAsString(StandardClaimNames.SUB);
        accountService.unlinkAccount(keycloakUserId, providerAlias);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/linked-accounts/{providerAlias}/claim")
    @Operation(
        summary = "Claim identity provider (temporarily disabled)",
        description = "Account merging is temporarily disabled until a secure relinking flow is implemented. " +
            "Use the standard linked-account flow instead."
    )
    @ApiResponse(responseCode = "409", description = "Account merging is temporarily unavailable")
    public ResponseEntity<Void> claimIdentity(
        @PathVariable @jakarta.validation.constraints.Pattern(regexp = "^[a-z0-9-]{1,64}$") String providerAlias,
        @AuthenticationPrincipal JwtAuthenticationToken auth
    ) {
        JwtAuthenticationToken token = resolveAuthentication(auth);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String keycloakUserId = token.getToken().getClaimAsString(StandardClaimNames.SUB);
        accountService.claimIdentity(keycloakUserId, providerAlias);
        return ResponseEntity.noContent().build();
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

    private Optional<User> resolveOrProvisionCurrentUser(JwtAuthenticationToken auth) {
        if (resolveAuthentication(auth) == null) {
            return Optional.empty();
        }

        return authenticatedGitProviderUserService.resolveOrProvisionCurrentUser(null);
    }
}
