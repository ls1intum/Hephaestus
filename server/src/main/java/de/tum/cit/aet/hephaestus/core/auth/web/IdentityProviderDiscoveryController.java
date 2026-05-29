package de.tum.cit.aet.hephaestus.core.auth.web;

import de.tum.cit.aet.hephaestus.core.auth.oauth.LoginClientRegistrationRepository;
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
 * <p>v1 lists every configured registration (env defaults + active workspace OIDC
 * Connections). Workspace-scoped filtering ({@code ?workspace={slug}} → only that
 * workspace's providers + defaults) is a thin follow-up once the workspace-slug → id
 * resolution is exposed publicly; the registration ids already encode the workspace
 * ({@code gl-ws-{id}}).
 */
@RestController
@Tag(name = "Auth discovery", description = "Identity provider discovery (public)")
public class IdentityProviderDiscoveryController {

    private final LoginClientRegistrationRepository registrationRepository;

    public IdentityProviderDiscoveryController(LoginClientRegistrationRepository registrationRepository) {
        this.registrationRepository = registrationRepository;
    }

    /** One row per sign-in option. {@code providerType} drives the SPA's icon choice. */
    public record IdentityProviderViewDTO(String registrationId, String displayName, String providerType) {}

    @GetMapping("/identity-providers")
    @PreAuthorize("permitAll()")
    @Operation(summary = "List available identity providers", operationId = "listIdentityProviders")
    public ResponseEntity<List<IdentityProviderViewDTO>> list() {
        List<IdentityProviderViewDTO> views = new ArrayList<>();
        for (ClientRegistration reg : registrationRepository) {
            views.add(
                new IdentityProviderViewDTO(
                    reg.getRegistrationId(),
                    reg.getClientName() != null ? reg.getClientName() : reg.getRegistrationId(),
                    providerTypeOf(reg.getRegistrationId())
                )
            );
        }
        return ResponseEntity.ok(views);
    }

    private static String providerTypeOf(String registrationId) {
        if (registrationId.equals("github") || registrationId.startsWith("gh-ws-")) {
            return "GITHUB";
        }
        if (registrationId.equals("gitlab-lrz") || registrationId.startsWith("gl-ws-")) {
            return "GITLAB";
        }
        return "OIDC";
    }
}
