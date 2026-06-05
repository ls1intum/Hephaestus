package de.tum.cit.aet.hephaestus.core.auth.web;

import de.tum.cit.aet.hephaestus.core.auth.spi.IdentityProviderCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public discovery of the identity providers a user can sign in with (replaces the former
 * Keycloak {@code /auth/identity-providers}; ADR 0017). The SPA login
 * page renders one button per entry; each button targets
 * {@code /auth/login?provider={registrationId}}.
 *
 * <p>Lists every enabled instance-scoped {@code login_provider} (GitHub, GitLab.com, self-hosted
 * GitLab) — one shared registration per provider, reused across all workspaces.
 */
@RestController
@Tag(name = "Auth discovery", description = "Identity provider discovery (public)")
public class IdentityProviderDiscoveryController {

    private final IdentityProviderCatalog identityProviderCatalog;

    public IdentityProviderDiscoveryController(IdentityProviderCatalog identityProviderCatalog) {
        this.identityProviderCatalog = identityProviderCatalog;
    }

    /** One row per sign-in option. {@code providerType} drives the SPA's icon choice. */
    public record IdentityProviderViewDTO(String registrationId, String displayName, String providerType) {}

    @GetMapping("/identity-providers")
    @PreAuthorize("permitAll()")
    @Operation(summary = "List available identity providers", operationId = "listIdentityProviders")
    public ResponseEntity<List<IdentityProviderViewDTO>> list() {
        List<IdentityProviderViewDTO> views = new ArrayList<>();
        for (ClientRegistration reg : identityProviderCatalog.listRegistrations()) {
            views.add(
                new IdentityProviderViewDTO(
                    reg.getRegistrationId(),
                    reg.getClientName() != null ? reg.getClientName() : reg.getRegistrationId(),
                    providerTypeOf(reg)
                )
            );
        }
        return ResponseEntity.ok(views);
    }

    /**
     * The provider type drives the SPA's icon choice. Derived from the authorization endpoint host
     * (the only GitHub login target is github.com; everything else is a GitLab instance), so it works
     * for any admin-registered self-hosted GitLab without relying on the registration-id naming.
     */
    private static String providerTypeOf(ClientRegistration reg) {
        String authorizationUri = reg.getProviderDetails().getAuthorizationUri();
        return (authorizationUri != null && authorizationUri.contains("github.com")) ? "GITHUB" : "GITLAB";
    }
}
