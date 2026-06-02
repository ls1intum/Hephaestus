package de.tum.cit.aet.hephaestus.account;

import de.tum.cit.aet.hephaestus.integration.core.connection.identity.AuthenticatedGitProviderUserService;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Optional;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User preferences (settings) under {@code /user/settings}.
 *
 * <p>Account identity, linked-identity management, and GDPR deletion moved to the
 * {@code core.auth} module ({@code AccountWebController}). This controller now owns only the
 * notification / research / AI-review preference surface, which is independent of the
 * identity provider.
 */
@Validated
@RestController
@RequestMapping("/user")
@Tag(name = "Account", description = "User preferences")
@PreAuthorize("isAuthenticated()")
public class AccountController {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    private final AccountPreferencesService preferencesService;
    private final AuthenticatedGitProviderUserService authenticatedGitProviderUserService;

    public AccountController(
        AccountPreferencesService preferencesService,
        AuthenticatedGitProviderUserService authenticatedGitProviderUserService
    ) {
        this.preferencesService = preferencesService;
        this.authenticatedGitProviderUserService = authenticatedGitProviderUserService;
    }

    @GetMapping("/settings")
    @Operation(summary = "Get user settings", operationId = "getUserSettings")
    public ResponseEntity<UserSettingsDTO> getUserSettings(@AuthenticationPrincipal JwtAuthenticationToken auth) {
        return resolveOrProvisionCurrentUser(auth)
            .map(value -> ResponseEntity.ok(preferencesService.getUserSettings(value)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/settings")
    @Operation(summary = "Update user settings", operationId = "updateUserSettings")
    public ResponseEntity<UserSettingsDTO> updateUserSettings(
        @AuthenticationPrincipal JwtAuthenticationToken auth,
        @Valid @RequestBody UserSettingsDTO userSettings
    ) {
        var user = resolveOrProvisionCurrentUser(auth);
        if (user.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        JwtAuthenticationToken token = resolveAuthentication(auth);
        if (token == null) {
            // No authenticated principal: only allow non-consent-revoking updates.
            UserPreferences preferences = preferencesService.getOrCreatePreferences(user.get());
            boolean switchingOffResearch =
                Boolean.FALSE.equals(userSettings.participateInResearch()) && preferences.isParticipateInResearch();
            if (switchingOffResearch) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        }
        String subjectId = token != null ? token.getToken().getClaimAsString(StandardClaimNames.SUB) : null;
        return ResponseEntity.ok(preferencesService.updateUserSettings(user.get(), userSettings, subjectId));
    }

    private JwtAuthenticationToken resolveAuthentication(JwtAuthenticationToken injectedToken) {
        if (injectedToken != null) {
            return injectedToken;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof JwtAuthenticationToken jwt ? jwt : null;
    }

    private Optional<User> resolveOrProvisionCurrentUser(JwtAuthenticationToken auth) {
        if (resolveAuthentication(auth) == null) {
            return Optional.empty();
        }
        return authenticatedGitProviderUserService.resolveOrProvisionCurrentUser();
    }
}
